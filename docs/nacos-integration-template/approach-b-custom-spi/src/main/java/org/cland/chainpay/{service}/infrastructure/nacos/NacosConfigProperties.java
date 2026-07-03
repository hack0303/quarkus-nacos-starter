package org.cland.chainpay.{service}.infrastructure.nacos;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 配置属性 POJO —— 从环境变量 / 系统属性读取连接参数。
 *
 * <p>纯 POJO，无框架注解。遵循 Clean Architecture 依赖规则，允许在 domain / application 层引用。
 *
 * <p>读取优先级：环境变量 &gt; 系统属性 (-D) &gt; 硬编码默认值。
 *
 * <p>所有配置键及对应环境变量：
 *
 * <table>
 *   <tr><th>环境变量</th><th>属性键</th><th>默认值</th></tr>
 *   <tr><td>{@code NACOS_SERVERADDR}</td><td>{@code nacos.server-addr}</td><td>192.168.1.11:8848</td></tr>
 *   <tr><td>{@code NACOS_NAMESPACE}</td><td>{@code nacos.namespace}</td><td>{namespace-uuid}</td></tr>
 *   <tr><td>{@code NACOS_DATAID}</td><td>{@code nacos.config.data-id}</td><td>cland-chainpay-{service}</td></tr>
 *   <tr><td>{@code NACOS_GROUP}</td><td>{@code nacos.config.group}</td><td>DEFAULT_GROUP</td></tr>
 *   <tr><td>{@code NACOS_USERNAME}</td><td>{@code nacos.username}</td><td>chainpay</td></tr>
 *   <tr><td>{@code NACOS_PASSWORD}</td><td>{@code nacos.password}</td><td>chainpay123</td></tr>
 *   <tr><td>{@code NACOS_TIMEOUT_MS}</td><td>{@code nacos.config.timeout-ms}</td><td>5000</td></tr>
 * </table>
 */
public class NacosConfigProperties {

  private static final Logger log = LoggerFactory.getLogger(NacosConfigProperties.class);

  private final String serverAddr;
  private final String namespace;
  private final String dataId;
  private final String group;
  private final String username;
  private final String password;
  private final long timeoutMs;

  public NacosConfigProperties() {
    this.serverAddr = env("NACOS_SERVERADDR", "192.168.1.11:8848");
    this.namespace = env("NACOS_NAMESPACE", "f00bead4-47a4-491a-a5e2-66d79f82d8a4");
    this.dataId = env("NACOS_DATAID", "cland-chainpay-{service}");
    this.group = env("NACOS_GROUP", "DEFAULT_GROUP");
    this.username = env("NACOS_USERNAME", "chainpay");
    this.password = env("NACOS_PASSWORD", "chainpay123");
    this.timeoutMs = Long.parseLong(env("NACOS_TIMEOUT_MS", "5000"));
    log.info(
        "NacosConfigProperties: serverAddr={}, namespace={}, dataId={}, group={}",
        serverAddr, namespace, dataId, group);
  }

  // --- Accessors ---

  public String serverAddr() { return serverAddr; }
  public String namespace() { return namespace; }
  public String dataId() { return dataId; }
  public String group() { return group; }
  public String username() { return username; }
  public String password() { return password; }
  public long timeoutMs() { return timeoutMs; }

  /**
   * 按优先级读取配置：环境变量 &gt; 系统属性 &gt; 默认值。
   */
  private static String env(String key, String defaultValue) {
    return Optional.ofNullable(System.getenv(key))
        .orElseGet(() -> System.getProperty(key, defaultValue));
  }
}
