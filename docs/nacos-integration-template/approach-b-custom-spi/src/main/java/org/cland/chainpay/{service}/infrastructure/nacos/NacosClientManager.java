package org.cland.chainpay.{service}.infrastructure.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.Properties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 客户端管理器 —— 全局唯一持有 {@link NamingService} 实例。
 *
 * <p>作为共享资源提供者，{@link NacosServiceRegistry}、{@link NacosServiceDiscovery} 和
 * {@link NacosHealthCheck} 均注入此管理器而非各自创建客户端，避免重复的连接池和心跳线程开销。
 *
 * <p>生命周期与应用绑定：{@link Startup} 启动时初始化，{@link PreDestroy} 关闭时释放。
 *
 * <p>初始化失败不会阻断应用启动（非阻断设计），Nacos 不可用时服务注册/发现功能降级。
 *
 * <p>配置键使用 {@code nacos.*} 独立命名空间。所有配置均支持环境变量覆盖。
 */
@Singleton
@Startup
public class NacosClientManager {

  private static final Logger log = LoggerFactory.getLogger(NacosClientManager.class);

  private final String serverAddr;
  private final String namespace;
  private final String username;
  private final String password;
  private NamingService namingService;
  private volatile boolean initialized;

  public NacosClientManager(
      @ConfigProperty(name = "nacos.server-addr", defaultValue = "192.168.1.11:8848")
          String serverAddr,
      @ConfigProperty(name = "nacos.namespace", defaultValue = "") String namespace,
      @ConfigProperty(name = "nacos.username", defaultValue = "") String username,
      @ConfigProperty(name = "nacos.password", defaultValue = "") String password) {
    this.serverAddr = serverAddr;
    this.namespace = namespace;
    this.username = username;
    this.password = password;
  }

  @PostConstruct
  void init() {
    try {
      Properties props = new Properties();
      props.put("serverAddr", serverAddr);
      if (!namespace.isBlank()) {
        props.put("namespace", namespace);
      }
      if (!username.isBlank()) {
        props.put("username", username);
      }
      if (!password.isBlank()) {
        props.put("password", password);
      }
      this.namingService = NacosFactory.createNamingService(props);
      this.initialized = true;
      log.info(
          "Nacos 客户端管理器初始化完成: serverAddr={}, namespace='{}', username='{}'",
          serverAddr, namespace, username);
    } catch (NacosException e) {
      log.error(
          "Nacos 客户端管理器初始化失败（非阻断，服务注册/发现降级）: serverAddr={}, err={}",
          serverAddr, e.getMessage());
      this.initialized = false;
    }
  }

  @PreDestroy
  void destroy() {
    if (namingService != null) {
      try {
        namingService.shutDown();
        log.info("Nacos 客户端管理器已关闭");
      } catch (NacosException e) {
        log.warn("Nacos 客户端管理器关闭异常: {}", e.getMessage());
      }
    }
  }

  /** 检查 Nacos 客户端是否已成功初始化。 */
  public boolean isInitialized() {
    return initialized;
  }

  /**
   * 获取共享的 NamingService 实例。
   *
   * @return NamingService 实例，若未初始化则返回 {@code null}
   */
  public NamingService getNamingService() {
    return namingService;
  }

  public String getServerAddr() {
    return serverAddr;
  }
}
