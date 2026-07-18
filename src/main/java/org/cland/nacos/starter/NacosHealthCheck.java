package org.cland.nacos.starter;

import com.alibaba.nacos.api.exception.NacosException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 健康检查 —— 作为 Quarkus SmallRye Health 的 Readiness 探针。
 *
 * <p>通过 Nacos {@code NamingService#selectInstances()} 检测 Nacos 服务端连通性。
 * 暴露在 {@code /health/ready} 端点，供 Kubernetes/Docker 就绪探针使用。
 *
 * <p>当 Nacos 不可用时标记为 {@code DOWN}，平台会停止流量路由至此实例。
 */
@Readiness
@ApplicationScoped
public class NacosHealthCheck implements HealthCheck {

  private static final Logger log = LoggerFactory.getLogger(NacosHealthCheck.class);

  private final boolean nacosEnabled;
  private final NacosClientManager clientManager;
  private final String serviceName;
  private final String serviceGroup;

  public NacosHealthCheck(
      @ConfigProperty(name = "nacos.config.enabled", defaultValue = "true")
          boolean nacosEnabled,
      NacosClientManager clientManager,
      @ConfigProperty(
              name = "nacos.discovery.service-name",
              defaultValue = "cland-chainpay-app")
          String serviceName,
      @ConfigProperty(
              name = "nacos.discovery.service-group",
              defaultValue = "DEFAULT_GROUP")
          String serviceGroup) {
    this.nacosEnabled = nacosEnabled;
    this.clientManager = clientManager;
    this.serviceName = serviceName;
    this.serviceGroup = serviceGroup;
  }

  @Override
  public HealthCheckResponse call() {
    if (!nacosEnabled) {
      return HealthCheckResponse.named("Nacos Service Registration & Discovery")
          .withData("status", "disabled")
          .withData("reason", "nacos.config.enabled=false")
          .up()
          .build();
    }
    try {
      var instances =
          clientManager.getNamingService().selectInstances(serviceName, serviceGroup, true);
      boolean healthy = instances != null && !instances.isEmpty();
      return HealthCheckResponse.named("Nacos Service Registration & Discovery")
          .withData("serverAddr", clientManager.getServerAddr())
          .withData("serviceName", serviceName)
          .withData("group", serviceGroup)
          .status(healthy)
          .build();
    } catch (NacosException e) {
      log.warn("Nacos health check failed: {}", e.getMessage());
      return HealthCheckResponse.named("Nacos Service Registration & Discovery")
          .withData("serverAddr", clientManager.getServerAddr())
          .withData("error", e.getMessage())
          .down()
          .build();
    }
  }
}
