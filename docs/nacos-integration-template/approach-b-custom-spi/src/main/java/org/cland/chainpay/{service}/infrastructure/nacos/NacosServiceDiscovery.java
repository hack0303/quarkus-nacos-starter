package org.cland.chainpay.{service}.infrastructure.nacos;

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
 * <p>依赖 {@link NacosClientManager} 获取共享的 {@code NamingService}。
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

  public Instance getOneHealthyInstance(String serviceName) {
    return getOneHealthyInstance(serviceName, defaultGroup);
  }

  public Instance getOneHealthyInstance(String serviceName, String groupName) {
    if (!clientManager.isInitialized()) {
      log.warn("Nacos 客户端未初始化，无法查询实例: service={}", serviceName);
      return null;
    }
    try {
      List<Instance> instances =
          clientManager.getNamingService().selectInstances(serviceName, groupName, true);
      if (instances == null || instances.isEmpty()) {
        log.warn("Nacos 无可用健康实例: service={}, group={}", serviceName, groupName);
        return null;
      }
      return instances.get(random.nextInt(instances.size()));
    } catch (NacosException e) {
      log.error("Nacos 查询实例失败: service={}, group={}", serviceName, groupName, e);
      return null;
    }
  }

  public List<Instance> getAllHealthyInstances(String serviceName) {
    return getAllHealthyInstances(serviceName, defaultGroup);
  }

  public List<Instance> getAllHealthyInstances(String serviceName, String groupName) {
    if (!clientManager.isInitialized()) {
      log.warn("Nacos 客户端未初始化，无法查询实例列表: service={}", serviceName);
      return List.of();
    }
    try {
      return clientManager.getNamingService().selectInstances(serviceName, groupName, true);
    } catch (NacosException e) {
      log.error("Nacos 查询实例列表失败: service={}, group={}", serviceName, groupName, e);
      return List.of();
    }
  }
}
