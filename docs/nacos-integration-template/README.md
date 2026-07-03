# Nacos Integration Template for ChainPay Microservices

> Standardized Nacos configuration center + service registration/discovery integration for Quarkus 3.x.

## Overview

Two approaches are provided — pick the one that fits your service:

| Approach | Config Source | Complexity | Use Case |
|----------|--------------|------------|----------|
| **A** (Recommended) | `quarkus-config-nacos` extension | Simple | Most services — auto-injects Nacos config into Quarkus MP Config |
| **B** (Custom SPI) | Custom `ConfigSourceFactory` SPI | Moderate | When you need exact control over config loading, parsing, and refresh |

Both approaches share the **same service registry/discovery layer** (native `nacos-client` SDK).

## Template File Structure

### Approach A — quarkus-config-nacos extension

```
app/
└── src/main/java/org/cland/chainpay/{service}/infrastructure/nacos/
    ├── NacosClientManager.java      # Singleton — holds NamingService instance
    ├── NacosServiceRegistry.java    # @Startup — auto-register on boot
    ├── NacosServiceDiscovery.java   # @ApplicationScoped — query instances
    └── NacosHealthCheck.java        # @Readiness — health probe

src/main/resources/
└── application.properties           # Nacos config entries
```

### Approach B — Custom ConfigSource SPI

```
app/
└── src/main/java/org/cland/chainpay/{service}/infrastructure/nacos/
    ├── NacosConfigProperties.java   # POJO — env var config
    ├── NacosConfigSource.java       # ConfigSourceFactory SPI — inject config into Quarkus
    ├── NacosClientManager.java      # Singleton — holds NamingService instance
    ├── NacosServiceRegistry.java    # @Startup — auto-register on boot
    ├── NacosServiceDiscovery.java   # @ApplicationScoped — query instances
    └── NacosHealthCheck.java        # @Readiness — health probe

src/main/resources/
├── META-INF/services/io.smallrye.config.ConfigSourceFactory  # SPI registration
└── application.properties           # Nacos config entries
```

## Choosing Between Approaches

| Factor | Approach A (`quarkus-config-nacos`) | Approach B (Custom SPI) |
|--------|--------------------------------------|------------------------|
| **Setup effort** | Minimal — just add extension + properties | Moderate — write 2 classes + SPI file |
| **Config refresh** | Handled by extension | Manual listener registration |
| **Error handling** | Extension manages fallback | You control fallback logic |
| **Config format** | Properties, YAML, JSON | Properties only (extensible) |
| **Dependency** | Extra Quarkus extension | Only `nacos-client` |

## pom.xml Dependencies (Both Approaches)

```xml
<!-- Required: Nacos Client SDK (service registry & discovery) -->
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.4.3</version>
</dependency>

<!-- Approach A only: Quarkus Nacos Config extension -->
<!--
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-config-nacos</artifactId>
</dependency>
-->

<!-- Required: Health Check -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

## Environment Variable Reference

| Variable | Default | Used By |
|----------|---------|---------|
| `NACOS_SERVERADDR` | `192.168.1.11:8848` | Both |
| `NACOS_NAMESPACE` | `f00bead4-47a4-491a-a5e2-66d79f82d8a4` | Both |
| `NACOS_USERNAME` | `chainpay` | Both |
| `NACOS_PASSWORD` | `chainpay123` | Both |
| `NACOS_DATAID` | `{service-name}` | Approach B |
| `NACOS_GROUP` | `DEFAULT_GROUP` | Both |
| `NACOS_TIMEOUT_MS` | `5000` | Approach B |
| `NACOS_DISCOVERY_IP` | (auto-detect) | Both |
| `NACOS_DISCOVERY_PORT` | `8080` | Both |

> **Note**: Approach A uses `QUARKUS_CONFIG_SOURCE_NACOS_*` env vars for the extension config.
> Both approach A and B use independent `NACOS_*` / `nacos.*` namespaces for the native SDK.
