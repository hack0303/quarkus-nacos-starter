package org.cland.chainpay.{service}.infrastructure.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 服务注册 —— 应用启动时自动注册当前服务实例到 Nacos。
 *
 * <p>依赖 {@link NacosClientManager} 获取共享的 {@code NamingService}，避免重复创建客户端连接。
 *
 * <p>采用 {@link Singleton} + {@link Startup} 确保应用启动后尽早完成注册，
 * {@link Shutdown} 时自动注销实例，避免 Nacos 心跳超时残留脏数据。
 *
 * <p>Nacos 不可用时跳过注册并记录警告，不阻断应用启动。
 */
@Singleton
@Startup
public class NacosServiceRegistry {

  private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

  private final NacosClientManager clientManager;
  private final String serviceName;
  private final String serviceGroup;
  private final String clusterName;
  private final String host;
  private final int port;

  public NacosServiceRegistry(
      NacosClientManager clientManager,
      @ConfigProperty(
              name = "nacos.discovery.service-name",
              defaultValue = "{service-name}")
          String serviceName,
      @ConfigProperty(
              name = "nacos.discovery.service-group",
              defaultValue = "DEFAULT_GROUP")
          String serviceGroup,
      @ConfigProperty(
              name = "nacos.discovery.cluster",
              defaultValue = "DEFAULT")
          String clusterName,
      @ConfigProperty(
              name = "nacos.discovery.ip",
              defaultValue = "auto")
          String discoveryIp,
      @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080") int port) {
    this.clientManager = clientManager;
    this.serviceName = serviceName;
    this.serviceGroup = serviceGroup;
    this.clusterName = clusterName;
    this.host = resolveHost(discoveryIp);
    this.port = port;
  }

  @PostConstruct
  void init() {
    if (!clientManager.isInitialized() || clientManager.getNamingService() == null) {
      log.warn("Nacos 客户端未初始化，跳过服务注册: serviceName={}", serviceName);
      return;
    }
    try {
      Instance instance = new Instance();
      instance.setIp(this.host);
      instance.setPort(this.port);
      instance.setClusterName(clusterName);
      instance.setWeight(1.0);
      instance.setEphemeral(true);
      instance.addMetadata("app", serviceName);
      instance.addMetadata("version", "1.0.0");

      clientManager.getNamingService().registerInstance(serviceName, serviceGroup, instance);

      log.info(
          "Nacos 服务注册成功: {} -> {}:{} [group={}, cluster={}]",
          serviceName, this.host, this.port, serviceGroup, clusterName);
    } catch (NacosException e) {
      log.error("Nacos 服务注册失败（非阻断）: {}", e.getMessage());
    }
  }

  /** 应用关闭时主动注销实例，避免 Nacos 残留脏数据。 */
  @Shutdown
  void destroy() {
    if (!clientManager.isInitialized() || clientManager.getNamingService() == null) {
      return;
    }
    try {
      clientManager
          .getNamingService()
          .deregisterInstance(serviceName, serviceGroup, host, port, clusterName);
      log.info("Nacos 服务注销成功: {} -> {}:{}", serviceName, host, port);
    } catch (NacosException e) {
      log.warn("Nacos 服务注销失败（不影响关闭）: {}", e.getMessage());
    }
  }

  /**
   * 解析注册 IP：优先使用配置的 {@code nacos.discovery.ip}，否则自动探测本机非回环地址。
   */
  private static String resolveHost(String configuredIp) {
    if (configuredIp != null && !configuredIp.isBlank() && !"auto".equalsIgnoreCase(configuredIp)) {
      return configuredIp;
    }
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      if (interfaces != null) {
        while (interfaces.hasMoreElements()) {
          NetworkInterface ni = interfaces.nextElement();
          if (ni.isLoopback() || !ni.isUp()) continue;
          Enumeration<InetAddress> addresses = ni.getInetAddresses();
          while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
              return addr.getHostAddress();
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("自动探测本机 IP 失败，回退至 InetAddress.getLocalHost(): {}", e.getMessage());
    }
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      log.warn("获取本机 IP 失败，回退至 127.0.0.1: {}", e.getMessage());
      return "127.0.0.1";
    }
  }
}
