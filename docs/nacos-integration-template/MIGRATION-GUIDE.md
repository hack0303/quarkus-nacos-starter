# Migration Guide — Standardizing Nacos Integration

## Current State Across 6 Services

| Service | Config Approach | NacosClientManager | NacosServiceRegistry | NacosServiceDiscovery | NacosHealthCheck | ConfigSource SPI |
|---------|----------------|:---:|:---:|:---:|:---:|:---:|
| **payment-service** | `quarkus-config-nacos` | ✅ | ✅ | ✅ | ❌ | ❌ |
| **account-service** | `quarkus-config-nacos` | ❌ *(NacosLifecycleManager)* | ✅ *(different)* | ❌ *(NacosDiscoveryClient)* | ❌ | ❌ |
| **channel-service** | `quarkus-config-nacos` | ✅ | ✅ | ✅ | ✅ | ❌ |
| **clearing-service** | Custom SPI | ✅ | ✅ | ✅ | ❌ | ✅ |
| **ledger-service** | *(missing)* | ✅ | ✅ | ✅ | ❌ | ❌ |
| **recon-service** | *(missing)* | ✅ | ✅ | ✅ | ❌ | ❌ |

## Recommended Migration

### Step 1: Pick Your Approach

| If your service... | Use |
|-------------------|-----|
| Already uses `quarkus-config-nacos` (payment, account, channel) | **Approach A** — Keep existing extension, add missing health check |
| Uses custom SPI (clearing) | **Approach B** — Keep existing approach |
| Has no config integration (ledger, recon) | **Approach A** — Simpler to set up |

### Step 2: Copy Template Files

Replace `{service}` with your service directory name (e.g., `payment`, `account`, etc.).

For **Approach A**:
```
cp -r approach-a-quarkus-extension/src/main/java/org/cland/chainpay/{service}/infrastructure/nacos/* \
  ../cland-chainpay-{service}-service/src/main/java/org/cland/chainpay/{service}/infrastructure/nacos/
```

For **Approach B**:
```
cp -r approach-b-custom-spi/src/main/java/org/cland/chainpay/{service}/infrastructure/nacos/* \
  ../cland-chainpay-{service}-service/src/main/java/org/cland/chainpay/{service}/infrastructure/nacos/
cp approach-b-custom-spi/src/main/resources/META-INF/services/io.smallrye.config.ConfigSourceFactory \
  ../cland-chainpay-{service}-service/src/main/resources/META-INF/services/io.smallrye.config.ConfigSourceFactory
```

### Step 3: Add/Update application.properties

Merge the Nacos section from the template into your service's `application.properties`.

### Step 4: Verify pom.xml

Ensure both dependencies are present:

```xml
<!-- Required for both approaches -->
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.4.3</version>
</dependency>

<!-- Approach A only -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-config-nacos</artifactId>
</dependency>

<!-- Required for health checks -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

### Step 5: Add unremovable packages

In `application.properties`, ensure the Nacos package is in `quarkus.arc.unremovable-packages`:

```properties
quarkus.arc.unremovable-packages=...,org.cland.chainpay.{service}.infrastructure.nacos
```

## Detailed Service-Specific Migration

### payment-service
- Already has: `NacosClientManager`, `NacosServiceRegistry`, `NacosServiceDiscovery`
- Missing: `NacosHealthCheck`
- Action: Copy `NacosHealthCheck.java` (update `defaultValue` to `cland-chainpay-payment-service`)
- Also verify `application.properties` has both `quarkus.config.source.nacos.*` (already has) and `nacos.*` sections (already has)

### account-service
- Has different classes: `NacosConfig`, `NacosConfigClient`, `NacosLifecycleManager`, `NacosServiceRegistry`, `NacosDiscoveryClient`
- Recommended: Migrate to standard template (Approach A)
- Action: Replace with template's `NacosClientManager`, `NacosServiceRegistry`, `NacosServiceDiscovery`, `NacosHealthCheck`
- Remove: `NacosConfig.java`, `NacosConfigClient.java`, `NacosLifecycleManager.java`, `NacosDiscoveryClient.java`

### channel-service
- Already has: `NacosClientManager`, `NacosServiceRegistry`, `NacosServiceDiscovery`, `NacosHealthCheck`
- Already aligned with Approach A template! No changes needed.
- Verificar: Ensure `NacosHealthCheck` uses consistent `@ConfigProperty` keys (`nacos.discovery.service-name`, `nacos.discovery.service-group`)

### clearing-service
- Already uses custom SPI approach (Approach B)
- Has: `NacosConfigProperties`, `NacosConfigSource`, `NacosClientManager`, `NacosServiceRegistry`, `NacosServiceDiscovery`
- Missing: `NacosHealthCheck`
- Action: Copy `NacosHealthCheck.java` (update `defaultValue` to `cland-chainpay-clearing-service`)

### ledger-service
- Has: `NacosClientManager`, `NacosServiceRegistry`, `NacosServiceDiscovery`
- Missing: Config integration + `NacosHealthCheck`
- Action: Implement Approach A — copy all 4 template files + add `application.properties` Nacos section

### recon-service
- Has: `NacosClientManager`, `NacosServiceRegistry`, `NacosServiceDiscovery`
- Missing: Config integration + `NacosHealthCheck`
- Action: Implement Approach A — copy all 4 template files + add `application.properties` Nacos section
