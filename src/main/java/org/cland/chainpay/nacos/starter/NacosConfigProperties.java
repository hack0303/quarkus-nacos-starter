package org.cland.chainpay.nacos.starter;

import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 配置属性 POJO —— 从环境变量 / MP Config / 系统属性读取连接参数。
 *
 * <p>纯 POJO，无框架注解。可安全地在任何层引用。
 *
 * <p>读取优先级：<b>环境变量 &gt; MP Config ({@code application.properties}) &gt; 系统属性 (-D) &gt; 硬编码默认值</b>。
 *
 * <p>所有配置键及对应环境变量：
 *
 * <table>
 *   <thead>
 *     <tr><th>环境变量</th><th>属性键</th><th>默认值</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>{@code NACOS_SERVERADDR}</td><td>{@code nacos.config.server-addr}</td><td>192.168.1.11:8848</td></tr>
 *     <tr><td>{@code NACOS_NAMESPACE}</td><td>{@code nacos.config.namespace}</td><td>f00bead4-...</td></tr>
 *     <tr><td>{@code NACOS_DATAID}</td><td>{@code nacos.config.data-id}</td><td>{app-name}</td></tr>
 *     <tr><td>{@code NACOS_GROUP}</td><td>{@code nacos.config.group}</td><td>DEFAULT_GROUP</td></tr>
 *     <tr><td>{@code NACOS_USERNAME}</td><td>{@code nacos.username}</td><td>chainpay</td></tr>
 *     <tr><td>{@code NACOS_PASSWORD}</td><td>{@code nacos.password}</td><td>chainpay123</td></tr>
 *     <tr><td>{@code NACOS_TIMEOUT_MS}</td><td>{@code nacos.config.timeout-ms}</td><td>5000</td></tr>
 *   </tbody>
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
    this.serverAddr = env("NACOS_SERVERADDR", "nacos.config.server-addr", "192.168.1.11:8848");
    this.namespace = env("NACOS_NAMESPACE", "nacos.config.namespace", "f00bead4-47a4-491a-a5e2-66d79f82d8a4");
    this.dataId = env("NACOS_DATAID", "nacos.config.data-id", resolveDefaultDataId());
    this.group = env("NACOS_GROUP", "nacos.config.group", "DEFAULT_GROUP");
    this.username = env("NACOS_USERNAME", "nacos.config.username", "chainpay");
    this.password = env("NACOS_PASSWORD", "nacos.config.password", "chainpay123");
    this.timeoutMs = Long.parseLong(env("NACOS_TIMEOUT_MS", "nacos.config.timeout-ms", "5000"));
    log.info(
        "NacosConfigProperties: serverAddr={}, namespace={}, dataId={}, group={}",
        serverAddr, namespace, dataId, group);
  }

  // ---- Accessors ----

  public String serverAddr() { return serverAddr; }
  public String namespace() { return namespace; }
  public String dataId() { return dataId; }
  public String group() { return group; }
  public String username() { return username; }
  public String password() { return password; }
  public long timeoutMs() { return timeoutMs; }

  // ---- Internal ----

  /** 尝试从 {@code quarkus.application.name} 推断默认 dataId。 */
  private static String resolveDefaultDataId() {
    String name = System.getProperty("quarkus.application.name");
    if (name != null && !name.isBlank()) return name;
    // 环境变量兜底
    name = System.getenv("QUARKUS_APPLICATION_NAME");
    if (name != null && !name.isBlank()) return name;
    return "cland-chainpay-app";
  }

  /**
   * 按优先级读取配置值：<br>
   * 1. 环境变量 (envKey)<br>
   * 2. MP Config / application.properties (mpConfigKey)<br>
   * 3. JVM 系统属性 -D (envKey)<br>
   * 4. 硬编码默认值
   */
  private static String env(String envKey, String mpConfigKey, String defaultValue) {
    // 1. 环境变量（最高优先级）
    String value = System.getenv(envKey);
    if (value != null && !value.isBlank()) return value;
    // 2. MicroProfile Config（读取 application.properties 中配置的默认值）
    try {
      value = ConfigProvider.getConfig().getOptionalValue(mpConfigKey, String.class).orElse(null);
      if (value != null && !value.isBlank()) return value;
    } catch (Exception ignored) {
      // MP Config 尚未就绪（ConfigSource SPI 初始化早期）
    }
    // 3. JVM 系统属性
    value = System.getProperty(envKey);
    if (value != null && !value.isBlank()) return value;
    // 4. 默认值
    return defaultValue;
  }
}
