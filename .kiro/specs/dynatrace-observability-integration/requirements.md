# Requirements Document

## Introduction

Este documento define los requisitos para integrar observabilidad exhaustiva en una aplicación Spring Boot 3 con Kafka Streams utilizando Micrometer como biblioteca de métricas y Dynatrace como plataforma APM. La solución debe proporcionar visibilidad completa sobre la infraestructura JVM, el comportamiento de Kafka Streams (incluyendo repartitioning y state stores), y las métricas internas de RocksDB, sin modificar la lógica de negocio existente.

## Glossary

- **Micrometer**: Biblioteca de instrumentación de métricas para aplicaciones JVM que proporciona una API neutral de vendor
- **Dynatrace**: Plataforma de Application Performance Monitoring (APM) y observabilidad
- **Kafka_Streams**: Framework de procesamiento de streams de Apache Kafka integrado en la aplicación
- **State_Store**: Almacenamiento local de estado en Kafka Streams, implementado con RocksDB
- **RocksDB**: Motor de almacenamiento embebido de clave-valor utilizado por Kafka Streams para state stores
- **StreamThread**: Hilo de ejecución de Kafka Streams que procesa particiones
- **Repartitioning**: Proceso de redistribución de mensajes en Kafka Streams basado en una nueva clave
- **Actuator**: Módulo de Spring Boot que expone endpoints de monitoreo y gestión
- **Registry**: Componente de Micrometer que exporta métricas a un sistema de monitoreo específico
- **MeterBinder**: Interfaz de Micrometer para registrar métricas personalizadas
- **RocksDBConfigSetter**: Interfaz de Kafka Streams para personalizar la configuración de RocksDB

## Requirements

### Requirement 1: Configuración de Dependencias y Actuator

**User Story:** Como desarrollador, quiero configurar las dependencias necesarias de Micrometer en el proyecto, para que la aplicación pueda exponer métricas que el agente OneAgent de Dynatrace pueda recolectar.

#### Acceptance Criteria

1. WHEN el archivo build.gradle es procesado, THE Build_System SHALL incluir las dependencias de Micrometer Core, Micrometer Registry Prometheus, y Spring Boot Actuator
2. WHEN la aplicación inicia, THE Spring_Boot_Actuator SHALL estar habilitado y configurado para exponer endpoints de métricas en formato Prometheus
3. THE Build_System SHALL mantener compatibilidad con Java 21 y Spring Boot 3.3.2
4. THE Build_System SHALL incluir las dependencias sin modificar las dependencias existentes de Kafka Streams

### Requirement 2: Configuración de Exposición de Métricas para Dynatrace OneAgent

**User Story:** Como ingeniero de operaciones, quiero que las métricas de Micrometer sean expuestas correctamente para que el agente OneAgent de Dynatrace las recolecte automáticamente en GKE, y también puedan ser inspeccionadas localmente durante el desarrollo.

#### Acceptance Criteria

1. THE Application SHALL exponer métricas mediante Spring Boot Actuator en formato Prometheus compatible con Dynatrace OneAgent
2. THE Actuator SHALL exponer el endpoint /actuator/metrics para inspección local de métricas individuales
3. THE Actuator SHALL exponer el endpoint /actuator/prometheus para scraping de todas las métricas en formato Prometheus
4. WHEN la aplicación se ejecuta localmente, THE Actuator SHALL permitir acceso a los endpoints de métricas sin requerir autenticación
5. WHEN la aplicación se ejecuta en GKE, THE Dynatrace_OneAgent SHALL recolectar automáticamente las métricas expuestas en /actuator/prometheus
6. THE Application SHALL incluir metadata de la aplicación (nombre, versión, entorno) como tags comunes en todas las métricas para facilitar la identificación en Dynatrace
7. THE Application SHALL funcionar correctamente tanto con OneAgent presente (GKE) como sin él (desarrollo local)

### Requirement 3: Métricas de Infraestructura JVM

**User Story:** Como ingeniero de operaciones, quiero monitorear el estado de la JVM (CPU, memoria, hilos), para que pueda detectar problemas de recursos y rendimiento.

#### Acceptance Criteria

1. THE Micrometer SHALL recolectar métricas de uso de CPU del proceso JVM
2. THE Micrometer SHALL recolectar métricas de memoria heap y non-heap (usada, comprometida, máxima)
3. THE Micrometer SHALL recolectar métricas de Garbage Collection (conteo, tiempo de pausa)
4. THE Micrometer SHALL recolectar métricas de hilos JVM (activos, daemon, pico, iniciados)
5. THE Micrometer SHALL recolectar métricas de uso de disco y operaciones de I/O del sistema
6. WHEN estas métricas son recolectadas, THE Micrometer_Registry SHALL incluir tags con el nombre de la aplicación y el entorno

### Requirement 4: Métricas de Kafka Streams - Throughput y Latencia

**User Story:** Como ingeniero de operaciones, quiero monitorear el throughput y latencia de Kafka Streams, para que pueda identificar cuellos de botella en el procesamiento.

#### Acceptance Criteria

1. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de mensajes procesados por segundo (records-consumed-rate, records-produced-rate)
2. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de bytes procesados (bytes-consumed-rate, bytes-produced-rate)
3. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de latencia de procesamiento (process-latency-avg, process-latency-max)
4. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de consumer lag (records-lag, records-lag-max)
5. WHEN estas métricas son capturadas, THE Micrometer SHALL registrarlas con tags que identifiquen el topic, partition, y task-id

### Requirement 5: Métricas de Kafka Streams - Estado de Threads y Tareas

**User Story:** Como ingeniero de operaciones, quiero monitorear el estado de los StreamThreads y tareas de Kafka Streams, para que pueda detectar problemas de rebalanceo o tareas bloqueadas.

#### Acceptance Criteria

1. THE Kafka_Streams_Metrics_Listener SHALL capturar el número de StreamThreads activos y su estado (running, rebalancing, dead)
2. THE Kafka_Streams_Metrics_Listener SHALL capturar el número de tareas activas y en espera por thread
3. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de tiempo de commit (commit-latency-avg, commit-latency-max)
4. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de tiempo de rebalanceo (rebalance-latency-avg, rebalance-latency-max)
5. WHEN un StreamThread cambia de estado, THE Kafka_Streams_Metrics_Listener SHALL actualizar las métricas correspondientes

### Requirement 6: Métricas de Repartitioning

**User Story:** Como ingeniero de operaciones, quiero monitorear las operaciones de repartitioning en Kafka Streams, para que pueda entender el impacto en el rendimiento y la redistribución de datos.

#### Acceptance Criteria

1. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de registros emitidos durante repartitioning (repartition-records-sent)
2. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de registros descartados durante repartitioning (repartition-records-dropped)
3. THE Kafka_Streams_Metrics_Listener SHALL capturar métricas de latencia de operaciones de repartitioning
4. WHEN se realiza una operación de selectKey o repartition, THE Kafka_Streams_Metrics_Listener SHALL registrar las métricas asociadas con tags que identifiquen el nodo de la topología

### Requirement 7: Configuración Personalizada de RocksDB

**User Story:** Como desarrollador, quiero implementar un RocksDBConfigSetter personalizado, para que pueda habilitar la recolección de métricas internas de RocksDB.

#### Acceptance Criteria

1. THE Custom_RocksDBConfigSetter SHALL implementar la interfaz RocksDBConfigSetter de Kafka Streams
2. WHEN RocksDB es inicializado, THE Custom_RocksDBConfigSetter SHALL habilitar las estadísticas internas de RocksDB
3. THE Custom_RocksDBConfigSetter SHALL configurar RocksDB para exponer métricas a través de Statistics API
4. THE Application SHALL registrar el Custom_RocksDBConfigSetter en la configuración de Kafka Streams mediante la propiedad rocksdb.config.setter
5. THE Custom_RocksDBConfigSetter SHALL mantener las configuraciones por defecto de RocksDB que no afecten las métricas

### Requirement 8: Métricas de RocksDB - Memoria y Almacenamiento

**User Story:** Como ingeniero de operaciones, quiero monitorear el uso de memoria de RocksDB (MemTable, Block Cache), para que pueda optimizar la configuración y prevenir problemas de memoria.

#### Acceptance Criteria

1. THE RocksDB_Metrics_Collector SHALL capturar métricas de uso de memoria de MemTable (memtable-size-all, memtable-size-unflushed)
2. THE RocksDB_Metrics_Collector SHALL capturar métricas de uso de Block Cache (block-cache-usage, block-cache-capacity)
3. THE RocksDB_Metrics_Collector SHALL capturar métricas de tamaño total de archivos SST (total-sst-files-size)
4. THE RocksDB_Metrics_Collector SHALL capturar métricas de número de archivos SST activos (num-live-sst-files)
5. WHEN estas métricas son recolectadas, THE Micrometer SHALL registrarlas con tags que identifiquen el state store específico

### Requirement 9: Métricas de RocksDB - Operaciones y Latencia

**User Story:** Como ingeniero de operaciones, quiero monitorear las operaciones de lectura/escritura y latencia de RocksDB, para que pueda identificar problemas de rendimiento en los state stores.

#### Acceptance Criteria

1. THE RocksDB_Metrics_Collector SHALL capturar métricas de operaciones de lectura (get-count, multiget-count)
2. THE RocksDB_Metrics_Collector SHALL capturar métricas de operaciones de escritura (put-count, delete-count, write-count)
3. THE RocksDB_Metrics_Collector SHALL capturar métricas de latencia de lectura (get-latency-p50, get-latency-p99)
4. THE RocksDB_Metrics_Collector SHALL capturar métricas de latencia de escritura (write-latency-p50, write-latency-p99)
5. THE RocksDB_Metrics_Collector SHALL capturar métricas de throughput de bytes (bytes-read, bytes-written)

### Requirement 10: Métricas de RocksDB - Compactación

**User Story:** Como ingeniero de operaciones, quiero monitorear las operaciones de compactación de RocksDB, para que pueda entender el impacto en el rendimiento y la gestión de almacenamiento.

#### Acceptance Criteria

1. THE RocksDB_Metrics_Collector SHALL capturar métricas de operaciones de compactación en progreso (num-running-compactions)
2. THE RocksDB_Metrics_Collector SHALL capturar métricas de operaciones de flush pendientes (num-pending-flushes)
3. THE RocksDB_Metrics_Collector SHALL capturar métricas de bytes escritos durante compactación (compact-write-bytes)
4. THE RocksDB_Metrics_Collector SHALL capturar métricas de bytes leídos durante compactación (compact-read-bytes)
5. THE RocksDB_Metrics_Collector SHALL capturar métricas de tiempo total de compactación (compaction-time-micros)

### Requirement 11: Configuración de Beans de Micrometer

**User Story:** Como desarrollador, quiero configurar beans de Spring para registrar métricas personalizadas, para que Micrometer pueda recolectar y exportar todas las métricas necesarias.

#### Acceptance Criteria

1. THE Metrics_Configuration SHALL definir un bean MeterRegistry que se inyecte en componentes que necesiten registrar métricas
2. THE Metrics_Configuration SHALL definir un bean KafkaStreamsMicrometerListener que se registre como StateListener de Kafka Streams
3. THE Metrics_Configuration SHALL definir un bean RocksDBMetricsCollector que implemente MeterBinder para registrar métricas de RocksDB
4. WHEN la aplicación inicia, THE Spring_Context SHALL inicializar todos los beans de métricas antes de iniciar Kafka Streams
5. THE Metrics_Configuration SHALL aplicar tags comunes a todas las métricas (application.name, environment, instance.id)

### Requirement 12: Etiquetado y Organización de Métricas

**User Story:** Como ingeniero de operaciones, quiero que todas las métricas tengan tags consistentes y descriptivos, para que pueda filtrar y agrupar métricas fácilmente en Dynatrace.

#### Acceptance Criteria

1. THE Micrometer SHALL aplicar tags comunes a todas las métricas (application, environment, host)
2. WHEN se registran métricas de Kafka Streams, THE Micrometer SHALL incluir tags específicos (topic, partition, task-id, thread-id)
3. WHEN se registran métricas de RocksDB, THE Micrometer SHALL incluir tags específicos (state-store, column-family)
4. THE Micrometer SHALL utilizar nombres de métricas con prefijos consistentes (jvm.*, kafka.streams.*, rocksdb.*)
5. THE Micrometer SHALL seguir las convenciones de nomenclatura de Micrometer (lowercase con puntos como separadores)

### Requirement 13: Manejo de Errores y Resiliencia

**User Story:** Como desarrollador, quiero que la recolección de métricas sea resiliente a fallos, para que problemas en la observabilidad no afecten la funcionalidad principal de la aplicación.

#### Acceptance Criteria

1. IF la exportación a Dynatrace falla, THEN THE Application SHALL continuar operando normalmente
2. IF la recolección de métricas de RocksDB falla, THEN THE Application SHALL registrar el error en logs y continuar sin métricas de RocksDB
3. IF el Kafka_Streams_Metrics_Listener encuentra una métrica no disponible, THEN THE Listener SHALL omitir esa métrica y continuar con las demás
4. THE Application SHALL implementar circuit breaker o retry logic para la conexión a Dynatrace
5. WHEN ocurre un error en la recolección de métricas, THE Application SHALL emitir logs con nivel WARN o ERROR según la severidad

### Requirement 14: Configuración Externalizada

**User Story:** Como ingeniero de operaciones, quiero que toda la configuración de observabilidad sea externalizable, para que pueda ajustar parámetros sin recompilar la aplicación.

#### Acceptance Criteria

1. THE Application SHALL permitir configurar qué endpoints de Actuator están habilitados mediante propiedades management.endpoints.web.exposure.include
2. THE Application SHALL permitir configurar el puerto del servidor de métricas mediante la propiedad management.server.port si se requiere separación
3. THE Application SHALL permitir habilitar/deshabilitar métricas específicas mediante propiedades management.metrics.enable.*
4. THE Application SHALL permitir configurar tags comunes para todas las métricas mediante propiedades management.metrics.tags.*
5. THE Application SHALL proporcionar valores por defecto razonables para todas las propiedades de configuración

### Requirement 15: Documentación de Métricas

**User Story:** Como ingeniero de operaciones, quiero documentación clara de todas las métricas disponibles, para que pueda crear dashboards efectivos en Dynatrace.

#### Acceptance Criteria

1. THE Documentation SHALL listar todas las métricas de JVM con sus nombres, tipos, y descripciones
2. THE Documentation SHALL listar todas las métricas de Kafka Streams con sus nombres, tipos, tags, y descripciones
3. THE Documentation SHALL listar todas las métricas de RocksDB con sus nombres, tipos, tags, y descripciones
4. THE Documentation SHALL incluir ejemplos de queries o filtros útiles para Dynatrace
5. THE Documentation SHALL incluir recomendaciones de umbrales y alertas para métricas críticas

### Requirement 16: Documentación de Arquitectura

**User Story:** Como desarrollador, quiero un documento de arquitectura que explique claramente qué se implementó y cómo funciona, para que pueda entender rápidamente la solución de observabilidad.

#### Acceptance Criteria

1. THE Architecture_Documentation SHALL explicar de forma clara y concisa cada característica implementada
2. THE Architecture_Documentation SHALL listar todos los archivos involucrados en la implementación con su propósito
3. THE Architecture_Documentation SHALL explicar cómo fluyen las métricas desde su origen hasta Dynatrace
4. THE Architecture_Documentation SHALL incluir diagramas que ilustren la arquitectura de observabilidad
5. THE Architecture_Documentation SHALL explicar qué problema resuelve cada componente y por qué se implementó de esa manera
6. THE Architecture_Documentation SHALL estar ubicada en .kiro/specs/dynatrace-observability-integration/.arquitectura.md
