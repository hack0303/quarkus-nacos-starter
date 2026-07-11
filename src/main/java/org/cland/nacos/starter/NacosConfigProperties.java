package org.cland.nacos.starter;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 配置属性 POJO —— 从环境变量 / 系统属性读取连接参数。
 *
 * <p>纯 POJO，无框架注解。不依赖 MicroProfile Config（避免 ConfigSource SPI 初始化期间的循环依赖）。
 *
 * <p>读取优先级：<b>环境变量 &gt; 系统属性 (-D) &gt; 硬编码默认值</b>。
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

  private static final String DEFAULT_DATA_ID = "cland-app";

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
    this.dataId = resolveDataId();
    this.group = env("NACOS_GROUP", "DEFAULT_GROUP");
    this.username = env("NACOS_USERNAME", "chainpay");
    this.password = env("NACOS_PASSWORD", "chainpay123");
    this.timeoutMs = Long.parseLong(env("NACOS_TIMEOUT_MS", "5000"));
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

  /**
   * 解析 dataId：优先环境变量，其次系统属性，最后硬编码默认值。
   */
  private static String resolveDataId() {
    String value = System.getenv("NACOS_DATAID");
    if (value != null && !value.isBlank()) return value;
    value = System.getProperty("nacos.config.data-id");
    if (value != null && !value.isBlank()) return value;
    log.warn("NACOS_DATAID not set — using fallback '{}'", DEFAULT_DATA_ID);
    log.warn("  Set NACOS_DATAID env var or create .env file in project root");
    return DEFAULT_DATA_ID;
  }

  private static String env(String key, String defaultValue) {
    return Optional.ofNullable(System.getenv(key))
        .orElseGet(() -> System.getProperty(key, defaultValue));
  }
}
