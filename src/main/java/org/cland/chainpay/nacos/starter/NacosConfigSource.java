package org.cland.chainpay.nacos.starter;

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
 * Nacos 远程配置源 —— 将 Nacos 配置中心的内容注入 Quarkus MicroProfile Config 体系。
 *
 * <p>通过 Java SPI 自动注册（{@code META-INF/services/io.smallrye.config.ConfigSourceFactory}），
 * 消费方只需添加本 starter 依赖即可激活。
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li><b>启动不阻塞</b> — Nacos 不可用时自动降级到本地 {@code application.properties}
 *   <li><b>优先级 ordinal=250</b> — 高于本地配置文件 (100/110)，低于环境变量 (300/400)
 *   <li><b>懒加载</b> — 首次查询配置时才发起远程调用
 *   <li><b>动态刷新</b> — 注册 Nacos Listener，配置变更时自动更新缓存
 * </ul>
 */
public class NacosConfigSource implements ConfigSource, ConfigSourceFactory {

  private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);

  static final String NAME = "NacosConfigSource";

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
   * ordinal=250：介于 application.properties (100/110) 和环境变量 (300/400) 之间。
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
            "Nacos config loaded: dataId={}, group={}, properties={}",
            properties.dataId(), properties.group(), parsed.size());
        this.cachedProperties = parsed;
        registerListener();
      } else {
        log.warn(
            "Nacos config empty: dataId={}, group={} — using local config",
            properties.dataId(), properties.group());
        this.cachedProperties = Collections.emptyMap();
      }
    } catch (NacosException e) {
      log.warn(
          "Nacos config fetch failed (fallback to local): server={}, dataId={}, err={}",
          properties.serverAddr(), properties.dataId(), e.getMessage());
      this.cachedProperties = Collections.emptyMap();
    } catch (Exception e) {
      log.warn("Nacos config source init failed (fallback to local): {}", e.getMessage(), e);
      this.cachedProperties = Collections.emptyMap();
    }
  }

  /**
   * 注册 Nacos 配置变更监听，实现运行时动态刷新。
   *
   * <p>当 Nacos 远端配置变更时自动更新缓存。业务组件如需感知变化应主动调用
   * {@link #getValue(String)} 或实现自己的监听回调。
   */
  private void registerListener() {
    try {
      configService.addListener(properties.dataId(), properties.group(), new AbstractListener() {
        @Override
        public void receiveConfigInfo(String configInfo) {
          log.info("Nacos config changed, reloading: dataId={}, group={}",
              properties.dataId(), properties.group());
          if (configInfo != null && !configInfo.isBlank()) {
            cachedProperties = parseProperties(configInfo);
          } else {
            cachedProperties = Collections.emptyMap();
          }
        }
      });
    } catch (NacosException e) {
      log.warn("Failed to register Nacos config listener: {}", e.getMessage());
    }
  }

  /**
   * 解析 properties 格式的配置内容。
   *
   * <p>支持 key=value 格式，跳过空白行和 {@code #} 注释行。
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
