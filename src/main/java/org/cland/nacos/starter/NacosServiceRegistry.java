package org.cland.nacos.starter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.quarkus.runtime.Shutdown;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
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
 * <p>配置项：
 *
 * <ul>
 *   <li>{@code nacos.discovery.service-name} — 注册的服务名（默认 {@code quarkus.application.name}）
 *   <li>{@code nacos.discovery.service-group} — 分组（默认 DEFAULT_GROUP）
 *   <li>{@code nacos.discovery.cluster} — 集群（默认 DEFAULT）
 *   <li>{@code nacos.discovery.ip} — IP（默认 auto：自动探测）
 *   <li>{@code quarkus.http.port} — 端口（默认 8080）
 * </ul>
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

  /**
   * Lazy port — resolved via {@code Provider.get()} at registration time,
   * by which point {@link NacosConfigSource} has loaded remote config.
   *
   * <p>Using {@link Provider} (not direct {@code @ConfigProperty}) defers
   * resolution to {@link #init()}, avoiding the stale value from
   * {@code application.properties} (8080) before Nacos is loaded.
   */
  @Inject
  @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
  Provider<Integer> port;

  public NacosServiceRegistry(
      NacosClientManager clientManager,
      @ConfigProperty(
              name = "nacos.discovery.service-name",
              defaultValue = "cland-chainpay-app")
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
          String discoveryIp) {
    this.clientManager = clientManager;
    this.serviceName = serviceName;
    this.serviceGroup = serviceGroup;
    this.clusterName = clusterName;
    this.host = resolveHost(discoveryIp);
  }

  @PostConstruct
  void init() {
    if (!clientManager.isInitialized() || clientManager.getNamingService() == null) {
      log.warn("Nacos unavailable, skipping service registration: service={}", serviceName);
      return;
    }
    try {
      // Lazy-resolve port at registration time.
      // By now NacosConfigSource has loaded remote config,
      // so quarkus.http.port from Nacos (8103) overrides the default (8080).
      int actualPort = port.get();

      Instance instance = new Instance();
      instance.setIp(this.host);
      instance.setPort(actualPort);
      instance.setClusterName(clusterName);
      instance.setWeight(1.0);
      instance.setEphemeral(true);
      instance.addMetadata("app", serviceName);
      instance.addMetadata("version", "1.0.0");

      clientManager.getNamingService().registerInstance(serviceName, serviceGroup, instance);

      log.info(
          "Nacos service registered: {} -> {}:{} [group={}, cluster={}]",
          serviceName, this.host, actualPort, serviceGroup, clusterName);
    } catch (NacosException e) {
      log.error("Nacos service registration failed (non-fatal): {}", e.getMessage());
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
          .deregisterInstance(serviceName, serviceGroup, host, port.get(), clusterName);
      log.info("Nacos service deregistered: {} -> {}:{}", serviceName, host, port.get());
    } catch (NacosException e) {
      log.warn("Nacos service deregistration failed: {}", e.getMessage());
    }
  }

  /**
   * 解析注册 IP：优先使用显式配置，否则自动探测本机非回环 IPv4 地址。
   *
   * <p>在 Docker/K8s 环境中，建议通过环境变量 {@code NACOS_DISCOVERY_IP} 显式设置。
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
      log.warn("Auto IP detection failed, fallback to InetAddress: {}", e.getMessage());
    }
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      log.warn("Failed to get local host, fallback to 127.0.0.1: {}", e.getMessage());
      return "127.0.0.1";
    }
  }
}
