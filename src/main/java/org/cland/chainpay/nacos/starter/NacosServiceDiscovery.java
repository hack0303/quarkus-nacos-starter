package org.cland.chainpay.nacos.starter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Random;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 服务发现 —— 运行时查询服务实例列表，供下游 RPC 调用使用。
 *
 * <p>依赖 {@link NacosClientManager} 获取共享的 {@code NamingService}，避免重复创建客户端连接。
 *
 * <p>提供两种获取方式：
 * <ul>
 *   <li>{@link #getOneHealthyInstance(String)} — 随机获取一个健康实例（简单负载均衡）
 *   <li>{@link #getAllHealthyInstances(String)} — 获取全部健康实例
 * </ul>
 *
 * <p>Nacos 不可用时返回空结果，不影响主业务流程。
 */
@ApplicationScoped
public class NacosServiceDiscovery {

  private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);

  private final NacosClientManager clientManager;
  private final String defaultGroup;
  private final Random random = new Random();

  public NacosServiceDiscovery(
      NacosClientManager clientManager,
      @ConfigProperty(name = "nacos.discovery.service-group", defaultValue = "DEFAULT_GROUP")
          String defaultGroup) {
    this.clientManager = clientManager;
    this.defaultGroup = defaultGroup;
  }

  /**
   * 随机获取一个健康实例（简单负载均衡）。
   *
   * @param serviceName 目标服务名
   * @return 健康的实例，若无可用实例则返回 {@code null}
   */
  public Instance getOneHealthyInstance(String serviceName) {
    return getOneHealthyInstance(serviceName, defaultGroup);
  }

  /**
   * 随机获取一个健康实例（指定分组）。
   *
   * @param serviceName 目标服务名
   * @param groupName 分组名
   * @return 健康的实例，若无可用实例则返回 {@code null}
   */
  public Instance getOneHealthyInstance(String serviceName, String groupName) {
    if (!clientManager.isInitialized()) {
      log.warn("Nacos unavailable, cannot query instance: service={}", serviceName);
      return null;
    }
    try {
      List<Instance> instances =
          clientManager.getNamingService().selectInstances(serviceName, groupName, true);
      if (instances == null || instances.isEmpty()) {
        log.warn("No healthy Nacos instance: service={}, group={}", serviceName, groupName);
        return null;
      }
      return instances.get(random.nextInt(instances.size()));
    } catch (NacosException e) {
      log.error("Nacos instance query failed: service={}, group={}", serviceName, groupName, e);
      return null;
    }
  }

  /**
   * 获取指定服务的全部健康实例。
   *
   * @param serviceName 目标服务名
   * @return 健康实例列表，不会返回 {@code null}
   */
  public List<Instance> getAllHealthyInstances(String serviceName) {
    return getAllHealthyInstances(serviceName, defaultGroup);
  }

  /**
   * 获取指定服务的全部健康实例（指定分组）。
   *
   * @param serviceName 目标服务名
   * @param groupName 分组名
   * @return 健康实例列表，不会返回 {@code null}
   */
  public List<Instance> getAllHealthyInstances(String serviceName, String groupName) {
    if (!clientManager.isInitialized()) {
      log.warn("Nacos unavailable, cannot list instances: service={}", serviceName);
      return List.of();
    }
    try {
      return clientManager.getNamingService().selectInstances(serviceName, groupName, true);
    } catch (NacosException e) {
      log.error("Nacos instance list failed: service={}, group={}", serviceName, groupName, e);
      return List.of();
    }
  }
}
