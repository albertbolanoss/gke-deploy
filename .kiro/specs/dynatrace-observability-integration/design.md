# Design Document: Dynatrace Observability Integration

## Overview

Esta solución integra observabilidad exhaustiva en una aplicación Spring Boot 3 con Kafka Streams utilizando Micrometer como biblioteca de instrumentación y Dynatrace OneAgent como plataforma de recolección. La arquitectura está diseñada para funcionar tanto en entornos de desarrollo local (sin Dynatrace) como en GKE con Dynatrace OneAgent desplegado.

### Objetivos Clave

1. **Visibilidad Completa de JVM**: Monitorear CPU, memoria (heap/non-heap), hilos, y garbage collection
2. **Métricas de Kafka Streams**: Capturar throughput, latencia, lag, estado de threads, y operaciones de repartitioning
3. **Métricas de RocksDB**: Exponer métricas internas de state stores (memoria, operaciones, compactación)
4. **Integración Transparente**: No modificar lógica de negocio existente
5. **Desarrollo Local**: Permitir inspección de métricas localmente sin Dynatrace

### Estrategia de Implementación

La solución utiliza tres mecanismos principales:

1. **Micrometer Binders**: Para métricas estándar de JVM (CPU, memoria, hilos, GC)
2. **Kafka Streams Metrics Listener**: Para capturar métricas nativas de Kafka Streams
3. **RocksDB Config Setter + Metrics Collector**: Para exponer métricas internas de RocksDB

Todas las métricas se registran en el `MeterRegistry` de Micrometer y se exponen vía Spring Boot Actuator en formato Prometheus, permitiendo que Dynatrace OneAgent las recolecte automáticamente en GKE.

## Architecture

### Componentes Principales

```mermaid
graph TB
    subgraph "Application Layer"
        KS[Kafka Streams Topology]
        RDB[RocksDB State Stores]
        JVM[JVM Runtime]
    end
    
    subgraph "Metrics Collection Layer"
        KSML[KafkaStreamsMicrometerListener]
        RDBCS[RocksDBConfigSetter]
        RDBMC[RocksDBMetricsCollector]
        JVMB[JVM Micrometer Binders]
    end
    
    subgraph "Metrics Registry"
        MR[MeterRegistry]
        PR[Prometheus Registry]
    end
    
    subgraph "Exposure Layer"
        ACT[Spring Boot Actuator]
        PM[/actuator/prometheus]
        ME[/actuator/metrics]
    end
    
    subgraph "Collection Layer"
        LOCAL[Local Inspection]
        DTOA[Dynatrace OneAgent]
    end
    
    KS -->|Stream Metrics| KSML
    RDB -->|Statistics API| RDBCS
    RDBCS -->|Stats Object| RDBMC
    JVM -->|JVM Metrics| JVMB
    
    KSML -->|Register| MR
    RDBMC -->|Register| MR
    JVMB -->|Register| MR
    
    MR -->|Expose| PR
    PR -->|Provide| ACT
    
    ACT -->|Serve| PM
    ACT -->|Serve| ME
    
    PM -->|Scrape| DTOA
    ME -->|Query| LOCAL
    
    style DTOA fill:#00a1e0
    style LOCAL fill:#90EE90
```

### Flujo de Métricas

1. **Recolección**: Cada componente (Kafka Streams, RocksDB, JVM) genera métricas
2. **Registro**: Los listeners/collectors registran métricas en el `MeterRegistry` de Micrometer
3. **Exposición**: Spring Boot Actuator expone las métricas en endpoints HTTP
4. **Consumo**: 
   - **Local**: Desarrolladores consultan `/actuator/metrics` o `/actuator/prometheus`
   - **GKE**: Dynatrace OneAgent hace scraping de `/actuator/prometheus`

### Separación de Responsabilidades

- **Kafka Streams Topology**: No modificada, continúa con su lógica de negocio
- **Metrics Listeners**: Solo observan, no alteran el comportamiento
- **RocksDB Config Setter**: Solo habilita estadísticas, no cambia configuración funcional
- **Actuator**: Solo expone métricas, no afecta el procesamiento

## Components and Interfaces

### 1. MetricsConfiguration

**Propósito**: Bean de configuración central que inicializa todos los componentes de métricas.

**Responsabilidades**:
- Configurar tags comunes para todas las métricas (application.name, environment, host)
- Registrar JVM Micrometer Binders (ClassLoaderMetrics, JvmMemoryMetrics, JvmGcMetrics, ProcessorMetrics, JvmThreadMetrics)
- Crear y registrar KafkaStreamsMicrometerListener
- Crear y registrar RocksDBMetricsCollector

**Interfaz**:
```java
@Configuration
public class MetricsConfiguration {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
        @Value("${spring.application.name}") String applicationName,
        @Value("${ENVIRONMENT:local}") String environment
    );
    
    @Bean
    public KafkaStreamsMicrometerListener kafkaStreamsMicrometerListener(
        MeterRegistry meterRegistry
    );
    
    @Bean
    public RocksDBMetricsCollector rocksDBMetricsCollector(
        MeterRegistry meterRegistry
    );
}
```

### 2. KafkaStreamsMicrometerListener

**Propósito**: Listener que captura métricas nativas de Kafka Streams y las registra en Micrometer.

**Responsabilidades**:
- Implementar `KafkaStreams.StateListener` para detectar cambios de estado
- Acceder a `KafkaStreams.metrics()` para obtener métricas nativas
- Filtrar y transformar métricas relevantes
- Registrar métricas en MeterRegistry con tags apropiados

**Métricas Capturadas**:
- **Throughput**: `records-consumed-rate`, `records-produced-rate`, `bytes-consumed-rate`, `bytes-produced-rate`
- **Latencia**: `process-latency-avg`, `process-latency-max`, `commit-latency-avg`, `commit-latency-max`
- **Consumer Lag**: `records-lag`, `records-lag-max`
- **Thread State**: `thread-state` (running, rebalancing, dead)
- **Tasks**: `active-tasks`, `standby-tasks`
- **Repartitioning**: `repartition-records-sent`, `repartition-records-dropped`

**Interfaz**:
```java
public class KafkaStreamsMicrometerListener implements KafkaStreams.StateListener {
    
    private final MeterRegistry meterRegistry;
    private KafkaStreams kafkaStreams;
    
    public KafkaStreamsMicrometerListener(MeterRegistry meterRegistry);
    
    public void setKafkaStreams(KafkaStreams kafkaStreams);
    
    @Override
    public void onChange(KafkaStreams.State newState, KafkaStreams.State oldState);
    
    private void registerKafkaStreamsMetrics();
    
    private void registerMetricAsGauge(
        String metricName,
        String description,
        Map<String, String> tags
    );
}
```

**Integración con Kafka Streams**:
```java
@Configuration
public class KafkaStreamsConfig {
    
    @Bean
    public KafkaStreamsConfiguration kafkaStreamsConfiguration(
        KafkaStreamsMicrometerListener listener
    ) {
        // Configuración de Kafka Streams
        // Registrar listener para capturar métricas
    }
}
```

### 3. CustomRocksDBConfigSetter

**Propósito**: Implementación personalizada de `RocksDBConfigSetter` que habilita estadísticas internas de RocksDB.

**Responsabilidades**:
- Implementar `RocksDBConfigSetter` de Kafka Streams
- Habilitar `Statistics` en RocksDB
- Configurar nivel de estadísticas apropiado
- Exponer el objeto `Statistics` para que RocksDBMetricsCollector lo consuma

**Interfaz**:
```java
public class CustomRocksDBConfigSetter implements RocksDBConfigSetter {
    
    private static final Map<String, Statistics> storeStatistics = new ConcurrentHashMap<>();
    
    @Override
    public void setConfig(
        String storeName,
        Options options,
        Map<String, Object> configs
    );
    
    @Override
    public void close(String storeName, Options options);
    
    public static Map<String, Statistics> getStoreStatistics();
}
```

**Configuración en application.yaml**:
```yaml
spring:
  kafka:
    streams:
      properties:
        rocksdb.config.setter: com.labs.repartitioner.metrics.CustomRocksDBConfigSetter
```

### 4. RocksDBMetricsCollector

**Propósito**: Collector que lee estadísticas de RocksDB y las registra como métricas de Micrometer.

**Responsabilidades**:
- Implementar `MeterBinder` de Micrometer
- Acceder a `Statistics` objects de CustomRocksDBConfigSetter
- Extraer métricas relevantes de RocksDB
- Registrar métricas con tags que identifiquen el state store

**Métricas Capturadas**:
- **Memoria**: `memtable.size.all`, `memtable.size.unflushed`, `block.cache.usage`, `block.cache.capacity`
- **Archivos SST**: `total.sst.files.size`, `num.live.sst.files`
- **Operaciones**: `get.count`, `put.count`, `delete.count`, `write.count`, `multiget.count`
- **Latencia**: `get.latency.p50`, `get.latency.p99`, `write.latency.p50`, `write.latency.p99`
- **Throughput**: `bytes.read`, `bytes.written`
- **Compactación**: `num.running.compactions`, `num.pending.flushes`, `compact.write.bytes`, `compact.read.bytes`, `compaction.time.micros`

**Interfaz**:
```java
public class RocksDBMetricsCollector implements MeterBinder {
    
    private final MeterRegistry meterRegistry;
    
    public RocksDBMetricsCollector(MeterRegistry meterRegistry);
    
    @Override
    public void bindTo(MeterRegistry registry);
    
    @Scheduled(fixedRate = 10000) // Cada 10 segundos
    public void collectMetrics();
    
    private void registerStoreMetrics(String storeName, Statistics stats);
    
    private long getStatValue(Statistics stats, TickerType ticker);
    
    private long getHistogramValue(Statistics stats, HistogramType histogram, HistogramData.Percentile percentile);
}
```

### 5. Spring Boot Actuator Configuration

**Propósito**: Configurar endpoints de Actuator para exponer métricas.

**Configuración en application.yaml**:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
```

**Endpoints Expuestos**:
- `/actuator/metrics`: Lista todas las métricas disponibles
- `/actuator/metrics/{metricName}`: Detalle de una métrica específica
- `/actuator/prometheus`: Todas las métricas en formato Prometheus

## Data Models

### Metric Naming Convention

Todas las métricas siguen la convención de nomenclatura de Micrometer:
- Lowercase con puntos como separadores
- Prefijos por categoría: `jvm.*`, `kafka.streams.*`, `rocksdb.*`

### Common Tags

Todas las métricas incluyen estos tags comunes:
```java
{
    "application": "LABS_GKE_DEPLOY",
    "environment": "dev|staging|production",
    "host": "hostname"
}
```

### Kafka Streams Metric Tags

Métricas de Kafka Streams incluyen tags adicionales:
```java
{
    "topic": "topic-name",
    "partition": "partition-number",
    "task-id": "task-identifier",
    "thread-id": "stream-thread-identifier"
}
```

### RocksDB Metric Tags

Métricas de RocksDB incluyen tags adicionales:
```java
{
    "state-store": "store-name",
    "column-family": "default"
}
```

### Metric Types

- **Gauge**: Valor instantáneo (memoria usada, threads activos, lag)
- **Counter**: Valor acumulativo (mensajes procesados, bytes escritos)
- **Timer**: Distribución de latencias (process latency, commit latency)
- **Summary**: Distribución de valores (percentiles de latencia)

### Example Metrics

**JVM Memory**:
```
jvm.memory.used{area="heap",id="G1 Old Gen",application="LABS_GKE_DEPLOY"} 123456789
jvm.memory.max{area="heap",id="G1 Old Gen",application="LABS_GKE_DEPLOY"} 2147483648
```

**Kafka Streams Throughput**:
```
kafka.streams.records.consumed.rate{topic="uppercase",partition="0",application="LABS_GKE_DEPLOY"} 1500.5
kafka.streams.bytes.produced.rate{topic="uppercase-repartition",application="LABS_GKE_DEPLOY"} 45678.2
```

**RocksDB Memory**:
```
rocksdb.memtable.size.all{state-store="uppercase-storage",application="LABS_GKE_DEPLOY"} 8388608
rocksdb.block.cache.usage{state-store="uppercase-storage",application="LABS_GKE_DEPLOY"} 16777216
```

### Configuration Properties Model

```yaml
# Configuración de métricas
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    enable:
      jvm: true
      kafka.streams: true
      rocksdb: true
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
      
# Configuración de RocksDB
spring:
  kafka:
    streams:
      properties:
        rocksdb.config.setter: com.labs.repartitioner.metrics.CustomRocksDBConfigSetter
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Common Tags on All Metrics

*For any* metric registered in the MeterRegistry, that metric should include the common tags: application name, environment, and host.

**Validates: Requirements 2.6, 3.6, 11.5, 12.1**

**Rationale**: Consistent tagging across all metrics enables effective filtering, grouping, and correlation in Dynatrace. This property ensures that every metric, regardless of its source (JVM, Kafka Streams, RocksDB), can be traced back to the specific application instance that generated it.

### Property 2: Kafka Streams Metrics Include Specific Tags

*For any* metric registered by KafkaStreamsMicrometerListener, that metric should include specific tags identifying the topic, partition, task-id, or thread-id as applicable to the metric type.

**Validates: Requirements 4.5, 12.2**

**Rationale**: Kafka Streams metrics need granular tags to identify which part of the topology generated the metric. This enables operators to pinpoint performance issues to specific topics, partitions, or stream threads.

### Property 3: Repartitioning Metrics Include Topology Node Tags

*For any* repartitioning metric registered by KafkaStreamsMicrometerListener, that metric should include tags identifying the topology node that performed the repartitioning operation.

**Validates: Requirements 6.4**

**Rationale**: Repartitioning operations can be expensive. Tagging these metrics with the topology node allows operators to identify which transformations in the stream processing pipeline are causing performance bottlenecks.

### Property 4: RocksDB Metrics Include State Store Tags

*For any* metric registered by RocksDBMetricsCollector, that metric should include tags identifying the specific state store name and column family.

**Validates: Requirements 8.5, 12.3**

**Rationale**: Applications may have multiple state stores, each with different access patterns and performance characteristics. Tagging RocksDB metrics with the state store name enables operators to optimize each store independently.

### Property 5: Metric Names Follow Micrometer Convention

*For any* metric registered in the MeterRegistry, the metric name should follow Micrometer's naming convention: lowercase with dots as separators, and appropriate prefix (jvm.*, kafka.streams.*, rocksdb.*).

**Validates: Requirements 12.4, 12.5**

**Rationale**: Consistent naming conventions make metrics predictable and easier to query. Following Micrometer's standard conventions ensures compatibility with monitoring tools and reduces cognitive load for operators.

## Error Handling

### Resilience Strategy

The observability layer is designed to be non-intrusive and resilient to failures. Metrics collection should never cause the application to fail or degrade its core functionality.

### Error Handling Patterns

**1. Graceful Degradation in RocksDB Metrics Collection**

```java
@Scheduled(fixedRate = 10000)
public void collectMetrics() {
    try {
        Map<String, Statistics> storeStats = CustomRocksDBConfigSetter.getStoreStatistics();
        for (Map.Entry<String, Statistics> entry : storeStats.entrySet()) {
            try {
                registerStoreMetrics(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("Failed to collect metrics for store: {}", entry.getKey(), e);
                // Continue with other stores
            }
        }
    } catch (Exception e) {
        log.error("Failed to collect RocksDB metrics", e);
        // Application continues without RocksDB metrics
    }
}
```

**Validates: Requirements 13.2**

**2. Safe Metric Registration in Kafka Streams Listener**

```java
private void registerKafkaStreamsMetrics() {
    Map<MetricName, ? extends Metric> metrics = kafkaStreams.metrics();
    for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
        try {
            registerMetricAsGauge(entry.getKey(), entry.getValue());
        } catch (Exception e) {
            log.warn("Failed to register metric: {}", entry.getKey().name(), e);
            // Continue with other metrics
        }
    }
}
```

**Validates: Requirements 13.3**

**3. Logging Strategy**

- **ERROR level**: Critical failures that prevent entire metric categories (e.g., all RocksDB metrics)
- **WARN level**: Individual metric failures that don't affect other metrics
- **INFO level**: Successful initialization and configuration
- **DEBUG level**: Detailed metric values and registration events

**Validates: Requirements 13.5**

### Failure Scenarios

| Scenario | Behavior | Impact |
|----------|----------|--------|
| RocksDB Statistics not available | Log warning, skip RocksDB metrics | Application continues, no RocksDB visibility |
| Kafka Streams metric not found | Log warning, skip that metric | Application continues, partial Kafka Streams visibility |
| Actuator endpoint fails | Spring Boot handles gracefully | Metrics not exposed, application continues |
| Invalid metric name | Log warning, skip that metric | Application continues, one metric missing |

## Testing Strategy

### Dual Testing Approach

This feature requires both unit tests and property-based tests to ensure comprehensive coverage:

- **Unit tests**: Verify specific examples, edge cases, and error conditions
- **Property tests**: Verify universal properties across all inputs

Both testing approaches are complementary and necessary for comprehensive coverage.

### Unit Testing Focus

Unit tests should focus on:
- Specific examples that demonstrate correct behavior
- Integration points between components (Spring context, Kafka Streams, RocksDB)
- Edge cases and error conditions (missing metrics, null values, exceptions)
- Configuration loading and bean initialization

**Example Unit Tests**:
- Verify MetricsConfiguration creates all required beans
- Verify KafkaStreamsMicrometerListener registers on state change
- Verify CustomRocksDBConfigSetter enables Statistics
- Verify RocksDBMetricsCollector handles missing Statistics gracefully
- Verify Actuator endpoints are accessible after startup

### Property-Based Testing Configuration

**Library**: Use **jqwik** for Java property-based testing (compatible with JUnit 5)

**Configuration**:
- Minimum 100 iterations per property test
- Each property test must reference its design document property
- Tag format: `@Tag("Feature: dynatrace-observability-integration, Property {number}: {property_text}")`

**Property Test Implementation**:

Each correctness property must be implemented by a SINGLE property-based test:

**Property 1: Common Tags on All Metrics**
```java
@Property
@Tag("Feature: dynatrace-observability-integration, Property 1: Common Tags on All Metrics")
void allMetricsHaveCommonTags(@ForAll("metricNames") String metricName) {
    // Generate random metrics and verify all have common tags
    Meter meter = meterRegistry.find(metricName).meter();
    assertThat(meter.getId().getTags())
        .extracting(Tag::getKey)
        .contains("application", "environment", "host");
}
```

**Property 2: Kafka Streams Metrics Include Specific Tags**
```java
@Property
@Tag("Feature: dynatrace-observability-integration, Property 2: Kafka Streams Metrics Include Specific Tags")
void kafkaStreamsMetricsHaveSpecificTags(@ForAll("kafkaStreamsMetricNames") String metricName) {
    // Generate random Kafka Streams metrics and verify tags
    Meter meter = meterRegistry.find(metricName).meter();
    List<String> tagKeys = meter.getId().getTags().stream()
        .map(Tag::getKey)
        .collect(Collectors.toList());
    
    // At least one of these tags should be present
    assertThat(tagKeys)
        .containsAnyOf("topic", "partition", "task-id", "thread-id");
}
```

**Property 3: Repartitioning Metrics Include Topology Node Tags**
```java
@Property
@Tag("Feature: dynatrace-observability-integration, Property 3: Repartitioning Metrics Include Topology Node Tags")
void repartitioningMetricsHaveNodeTags(@ForAll("repartitioningMetricNames") String metricName) {
    // Generate random repartitioning metrics and verify node tags
    Meter meter = meterRegistry.find(metricName).meter();
    assertThat(meter.getId().getTags())
        .extracting(Tag::getKey)
        .contains("node-id");
}
```

**Property 4: RocksDB Metrics Include State Store Tags**
```java
@Property
@Tag("Feature: dynatrace-observability-integration, Property 4: RocksDB Metrics Include State Store Tags")
void rocksdbMetricsHaveStateStoreTags(@ForAll("rocksdbMetricNames") String metricName) {
    // Generate random RocksDB metrics and verify state store tags
    Meter meter = meterRegistry.find(metricName).meter();
    assertThat(meter.getId().getTags())
        .extracting(Tag::getKey)
        .contains("state-store", "column-family");
}
```

**Property 5: Metric Names Follow Micrometer Convention**
```java
@Property
@Tag("Feature: dynatrace-observability-integration, Property 5: Metric Names Follow Micrometer Convention")
void metricNamesFollowConvention(@ForAll("metricNames") String metricName) {
    // Verify metric name is lowercase with dots
    assertThat(metricName).matches("^[a-z][a-z0-9.]*$");
    
    // Verify metric has appropriate prefix
    assertThat(metricName)
        .matches("^(jvm|kafka\\.streams|rocksdb)\\..*");
}
```

### Integration Testing

Integration tests should verify:
- End-to-end metric flow from source to Actuator endpoint
- Kafka Streams topology with metrics collection
- RocksDB state store with metrics collection
- Actuator endpoints return valid Prometheus format

### Test Data Generators

For property-based tests, implement custom generators:
- `metricNames()`: Generate valid metric names from the registry
- `kafkaStreamsMetricNames()`: Generate Kafka Streams metric names
- `repartitioningMetricNames()`: Generate repartitioning metric names
- `rocksdbMetricNames()`: Generate RocksDB metric names

### Testing Constraints

- Tests should not require external dependencies (Dynatrace, Kafka cluster)
- Use embedded Kafka for Kafka Streams tests
- Use in-memory state stores for RocksDB tests when possible
- Mock external systems when necessary
