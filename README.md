# Quarkus Nacos Starter

> Drop-in Nacos integration for ChainPay Quarkus microservices.
> Custom ConfigSource SPI (ordinal=280) + native nacos-client SDK.

## What It Provides

| Capability | Class | Auto-Configured |
|------------|-------|:---:|
| Remote config injection | `NacosConfigSource` (SPI) | ✅ via `META-INF/services` |
| NamingService lifecycle | `NacosClientManager` | ✅ via `@Singleton @Startup` |
| Service registration | `NacosServiceRegistry` | ✅ via `@Singleton @Startup` |
| Service discovery | `NacosServiceDiscovery` | ✅ via `@ApplicationScoped` |
| Readiness health probe | `NacosHealthCheck` | ✅ via `@Readiness` |

## Usage

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.cland</groupId>
    <artifactId>quarkus-nacos-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure `application.properties`

```properties
# ===== Nacos Config Center (custom SPI) =====
nacos.config.server-addr=${NACOS_SERVERADDR:192.168.1.11:8848}
nacos.config.namespace=${NACOS_NAMESPACE:f00bead4-...}
nacos.config.data-id=${NACOS_DATAID:cland-chainpay-{service}}
nacos.config.group=${NACOS_GROUP:DEFAULT_GROUP}
nacos.config.username=${NACOS_USERNAME:chainpay}
nacos.config.password=${NACOS_PASSWORD:chainpay123}
nacos.config.timeout-ms=${NACOS_TIMEOUT_MS:5000}

# ===== Nacos Service Registration / Discovery =====
nacos.server-addr=${NACOS_SERVERADDR:192.168.1.11:8848}
nacos.namespace=${NACOS_NAMESPACE:f00bead4-...}
nacos.username=${NACOS_USERNAME:chainpay}
nacos.password=${NACOS_PASSWORD:chainpay123}
nacos.discovery.service-name=${NACOS_DISCOVERY_SERVICE_NAME:cland-chainpay-{service}}
nacos.discovery.service-group=${NACOS_DISCOVERY_SERVICE_GROUP:DEFAULT_GROUP}
nacos.discovery.cluster=${NACOS_DISCOVERY_CLUSTER:DEFAULT}
nacos.discovery.ip=${NACOS_DISCOVERY_IP:auto}
nacos.discovery.port=${NACOS_DISCOVERY_PORT:8080}
```

### 3. Ensure Packages Not Removed

In `application.properties`, add the starter's package to `quarkus.arc.unremovable-packages`:

```properties
quarkus.arc.unremovable-packages=org.cland.nacos.starter
```

### 4. Use NacosServiceDiscovery in Your Code

```java
@ApplicationScoped
public class MyRpcClient {
    @Inject NacosServiceDiscovery discovery;

    public void callDownstream() {
        Instance instance = discovery.getOneHealthyInstance("cland-chainpay-account-service");
        if (instance != null) {
            String url = "http://" + instance.getIp() + ":" + instance.getPort();
            // ... make HTTP/gRPC call
        }
    }
}
```

## Build

```bash
cd quarkus-nacos-starter
mvn clean install -DskipTests
```

## Environment Variable Reference

| Variable | Default | Used By |
|----------|---------|---------|
| `NACOS_SERVERADDR` | `192.168.1.11:8848` | Config + Discovery |
| `NACOS_NAMESPACE` | `f00bead4-...` | Config + Discovery |
| `NACOS_DATAID` | `cland-chainpay-app` | Config source |
| `NACOS_GROUP` | `DEFAULT_GROUP` | Config source |
| `NACOS_USERNAME` | `chainpay` | Auth |
| `NACOS_PASSWORD` | `chainpay123` | Auth |
| `NACOS_TIMEOUT_MS` | `5000` | Config fetch timeout |
| `NACOS_DISCOVERY_SERVICE_NAME` | `cland-chainpay-app` | Service registration |
| `NACOS_DISCOVERY_SERVICE_GROUP` | `DEFAULT_GROUP` | Service registration |
| `NACOS_DISCOVERY_CLUSTER` | `DEFAULT` | Service registration |
| `NACOS_DISCOVERY_IP` | `auto` | Service registration IP |
| `NACOS_DISCOVERY_PORT` | `8080` | Service registration port |

## Integration with Existing Services

| Service | Action |
|---------|--------|
| payment-service | Remove local Nacos classes, add starter dependency |
| account-service | Remove `NacosLifecycleManager` etc., add starter dependency |
| channel-service | Remove local Nacos classes, add starter dependency |
| clearing-service | Replace local SPI classes with starter |
| ledger-service | Remove local Nacos classes, add starter dependency |
| recon-service | Remove local Nacos classes, add starter dependency |
