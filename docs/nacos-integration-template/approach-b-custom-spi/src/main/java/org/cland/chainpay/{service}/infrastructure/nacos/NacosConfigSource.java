package org.cland.chainpay.{service}.infrastructure.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 配置源 —— 从 Nacos 配置中心获取远程配置，注入 Quarkus 的 MicroProfile Config 体系。
 *
 * <p>特点：
 *
 * <ul>
 *   <li><b>启动不阻塞</b> — Nacos 不可用时自动降级到本地 {@code application.properties}，不阻塞应用启动
 *   <li><b>优先级 ordinal=250</b> — 高于 {@code application.properties} (100/110)，低于环境变量 (300/400)
 *   <li><b>懒加载</b> — 首次 {@link #getValue} 时从远程拉取配置
 *   <li><b>配置变更监听</b> — 注册 Nacos Listener 实现动态刷新
 * </ul>
 *
 * <p>SPI 注册文件：{@code META-INF/services/io.smallrye.config.ConfigSourceFactory}
 *
 * <p>文件内容（一行）：<pre>
 * org.cland.chainpay.{service}.infrastructure.nacos.NacosConfigSource
 * </pre>
 */
public class NacosConfigSource implements ConfigSource, ConfigSourceFactory {

  private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);

  private static final String NAME = "NacosConfigSource";

  private final NacosConfigProperties properties;
  private volatile Map<String, String> cachedProperties;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private ConfigService configService;

  public NacosConfigSource() {
    this.properties = new NacosConfigProperties();
  }

  // ========================================================================
  // ConfigSourceFactory
  // ========================================================================

  @Override
  public Iterable<ConfigSource> getConfigSources(final ConfigSourceContext context) {
    return Collections.singletonList(this);
  }

  // ========================================================================
  // ConfigSource
  // ========================================================================

  @Override
  public Set<String> getPropertyNames() {
    ensureInitialized();
    return cachedProperties.keySet();
  }

  @Override
  public String getValue(final String propertyName) {
    ensureInitialized();
    return cachedProperties.get(propertyName);
  }

  @Override
  public String getName() {
    return NAME;
  }

  /**
   * ordinal=250：高于 application.properties (100/110)，低于环境变量 (300/400)。
   * 确保 Nacos 配置可覆盖本地配置，但环境变量具有最高优先级。
   */
  @Override
  public int getOrdinal() {
    return 250;
  }

  // ========================================================================
  // Internal — Lazy Initialization
  // ========================================================================

  private void ensureInitialized() {
    if (!initialized.get()) {
      synchronized (this) {
        if (!initialized.compareAndSet(false, true)) {
          return;
        }
        loadFromNacos();
      }
    }
  }

  private void loadFromNacos() {
    try {
      Properties nacosProps = new Properties();
      nacosProps.setProperty("serverAddr", properties.serverAddr());
      if (!properties.namespace().isBlank()) {
        nacosProps.setProperty("namespace", properties.namespace());
      }
      if (!properties.username().isBlank()) {
        nacosProps.setProperty("username", properties.username());
      }
      if (!properties.password().isBlank()) {
        nacosProps.setProperty("password", properties.password());
      }

      this.configService = NacosFactory.createConfigService(nacosProps);
      String configContent =
          configService.getConfig(properties.dataId(), properties.group(), properties.timeoutMs());

      if (configContent != null && !configContent.isBlank()) {
        Map<String, String> parsed = parseProperties(configContent);
        log.info(
            "从 Nacos 加载配置成功: dataId={}, group={}, 属性数={}",
            properties.dataId(), properties.group(), parsed.size());
        this.cachedProperties = parsed;
        registerListener();
      } else {
        log.warn(
            "Nacos 配置内容为空: dataId={}, group={}，使用本地配置",
            properties.dataId(), properties.group());
        this.cachedProperties = Collections.emptyMap();
      }
    } catch (NacosException e) {
      log.warn(
          "从 Nacos 加载配置失败（降级到本地配置）: serverAddr={}, dataId={}, error={}",
          properties.serverAddr(), properties.dataId(), e.getMessage());
      this.cachedProperties = Collections.emptyMap();
    } catch (Exception e) {
      log.warn("Nacos 配置源初始化异常（降级到本地配置）: {}", e.getMessage(), e);
      this.cachedProperties = Collections.emptyMap();
    }
  }

  /**
   * 注册 Nacos 配置变更监听器，实现运行时动态刷新。
   *
   * <p>当 Nacos 远端配置变更时，会自动更新 {@link #cachedProperties} 并记录日志。
   * 注意：被 {@code @ConfigProperty} 注入的字段不会自动更新 — 业务组件需要通过
   * {@link #getValue(String)} 主动查询最新值，或自行实现监听回调。
   */
  private void registerListener() {
    try {
      configService.addListener(properties.dataId(), properties.group(), new AbstractListener() {
        @Override
        public void receiveConfigInfo(String configInfo) {
          log.info("Nacos 配置已变更，重新加载: dataId={}, group={}",
              properties.dataId(), properties.group());
          if (configInfo != null && !configInfo.isBlank()) {
            cachedProperties = parseProperties(configInfo);
          } else {
            cachedProperties = Collections.emptyMap();
          }
        }
      });
    } catch (NacosException e) {
      log.warn("注册 Nacos 配置监听器失败: {}", e.getMessage());
    }
  }

  /**
   * 解析 Nacos properties 格式的配置内容。
   *
   * <p>支持 key=value 格式，跳过空白行和注释行（#）。
   * 注：不支持 YAML/JSON 嵌套结构。
   */
  static Map<String, String> parseProperties(String content) {
    Map<String, String> result = new HashMap<>();
    String[] lines = content.split("\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      int eqIdx = trimmed.indexOf('=');
      if (eqIdx > 0 && eqIdx < trimmed.length() - 1) {
        String key = trimmed.substring(0, eqIdx).trim();
        String value = trimmed.substring(eqIdx + 1).trim();
        result.put(key, value);
      }
    }
    return result;
  }
}
