# Implementation Plan: Dynatrace Observability Integration

## Overview

Este plan implementa observabilidad exhaustiva en la aplicación Spring Boot 3 con Kafka Streams, exponiendo métricas de JVM, Kafka Streams, y RocksDB mediante Micrometer y Spring Boot Actuator. Las métricas se exponen en formato Prometheus para que Dynatrace OneAgent las recolecte automáticamente en GKE, y también están disponibles para inspección local durante el desarrollo.

La implementación sigue un enfoque incremental: primero configuramos las dependencias y la infraestructura base, luego agregamos métricas de JVM (las más simples), seguidas de métricas de Kafka Streams, y finalmente las métricas más complejas de RocksDB. Cada paso incluye validación mediante tests.

## Tasks

- [x] 1. Configurar dependencias de Micrometer y Actuator
  - Agregar dependencias de Micrometer Core, Micrometer Registry Prometheus, y actualizaciones de Spring Boot Actuator en build.gradle
  - Verificar que las dependencias existentes de Kafka Streams no se modifiquen
  - _Requirements: 1.1, 1.4_

- [x] 2. Configurar Spring Boot Actuator para exposición de métricas
  - Actualizar application.yaml para habilitar endpoints de Actuator (/actuator/metrics, /actuator/prometheus)
  - Configurar exposición de métricas en formato Prometheus
  - Configurar tags comunes (application, environment, host)
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 14.1, 14.4_

- [x] 3. Crear clase de configuración de métricas base
  - [x] 3.1 Crear MetricsConfiguration con MeterRegistryCustomizer
    - Implementar bean que aplica tags comunes a todas las métricas
    - Configurar tags: application.name, environment, host
    - _Requirements: 11.1, 11.5, 12.1_
  
  - [x] 3.2 Write property test for common tags
    - **Property 1: Common Tags on All Metrics**
    - **Validates: Requirements 2.6, 3.6, 11.5, 12.1**
  
  - [x] 3.3 Write unit test for MetricsConfiguration bean initialization
    - Verificar que el bean MeterRegistryCustomizer se crea correctamente
    - Verificar que los tags comunes se aplican
    - _Requirements: 11.1_

- [ ] 4. Implementar métricas de JVM con Micrometer Binders
  - [x] 4.1 Registrar JVM Micrometer Binders en MetricsConfiguration
    - Agregar beans para ClassLoaderMetrics, JvmMemoryMetrics, JvmGcMetrics, ProcessorMetrics, JvmThreadMetrics
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  
  - [x] 4.2 Write unit tests for JVM metrics availability
    - Verificar que métricas de CPU, memoria, GC, y threads están presentes en el registry
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 5. Checkpoint - Verificar métricas JVM localmente
  - Iniciar la aplicación localmente y verificar que /actuator/prometheus expone métricas de JVM
  - Verificar que /actuator/metrics lista las métricas disponibles
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implementar KafkaStreamsMicrometerListener
  - [x] 6.1 Crear clase KafkaStreamsMicrometerListener
    - Implementar KafkaStreams.StateListener
    - Implementar método onChange para capturar cambios de estado
    - Implementar método registerKafkaStreamsMetrics para registrar métricas nativas
    - Implementar método registerMetricAsGauge para convertir métricas de Kafka a Micrometer
    - Incluir manejo de errores con try-catch y logging
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 11.2, 13.3_
  
  - [x] 6.2 Registrar KafkaStreamsMicrometerListener como bean en MetricsConfiguration
    - Crear bean que inyecta MeterRegistry
    - _Requirements: 11.2_
  
  - [x] 6.3 Integrar listener con Kafka Streams en configuración existente
    - Modificar configuración de Kafka Streams para registrar el listener
    - Inyectar KafkaStreams instance en el listener después de inicialización
    - _Requirements: 5.5_
  
  - [x] 6.4 Write property test for Kafka Streams metrics tags
    - **Property 2: Kafka Streams Metrics Include Specific Tags**
    - **Validates: Requirements 4.5, 12.2**
  
  - [x] 6.5 Write property test for repartitioning metrics tags
    - **Property 3: Repartitioning Metrics Include Topology Node Tags**
    - **Validates: Requirements 6.4**
  
  - [x] 6.6 Write unit tests for KafkaStreamsMicrometerListener
    - Verificar que el listener se registra correctamente
    - Verificar que métricas de throughput, latencia, y lag están presentes
    - Verificar manejo de errores cuando métricas no están disponibles
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 13.3_

- [x] 7. Checkpoint - Verificar métricas de Kafka Streams localmente
  - Iniciar la aplicación con Kafka habilitado
  - Procesar algunos mensajes a través de la topología
  - Verificar que /actuator/prometheus expone métricas de Kafka Streams con tags apropiados
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Implementar CustomRocksDBConfigSetter
  - [ ] 8.1 Crear clase CustomRocksDBConfigSetter
    - Implementar interfaz RocksDBConfigSetter de Kafka Streams
    - Implementar método setConfig para habilitar Statistics en RocksDB
    - Implementar método close para limpieza
    - Mantener mapa estático de Statistics por store name
    - Implementar método estático getStoreStatistics para acceso externo
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [ ] 8.2 Configurar rocksdb.config.setter en application.yaml
    - Agregar propiedad spring.kafka.streams.properties.rocksdb.config.setter
    - _Requirements: 7.4_
  
  - [ ]* 8.3 Write unit tests for CustomRocksDBConfigSetter
    - Verificar que Statistics se habilita correctamente
    - Verificar que Statistics está disponible después de inicialización
    - _Requirements: 7.2, 7.3_

- [ ] 9. Implementar RocksDBMetricsCollector
  - [ ] 9.1 Crear clase RocksDBMetricsCollector
    - Implementar interfaz MeterBinder de Micrometer
    - Implementar método bindTo para registrar métricas iniciales
    - Implementar método collectMetrics con @Scheduled para recolección periódica
    - Implementar método registerStoreMetrics para registrar métricas de un store específico
    - Implementar métodos helper getStatValue y getHistogramValue
    - Incluir manejo de errores con try-catch y logging
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 9.1, 9.2, 9.3, 9.4, 9.5, 10.1, 10.2, 10.3, 10.4, 10.5, 11.3, 13.2_
  
  - [ ] 9.2 Registrar RocksDBMetricsCollector como bean en MetricsConfiguration
    - Crear bean que inyecta MeterRegistry
    - Habilitar @EnableScheduling en la configuración
    - _Requirements: 11.3_
  
  - [ ]* 9.3 Write property test for RocksDB metrics tags
    - **Property 4: RocksDB Metrics Include State Store Tags**
    - **Validates: Requirements 8.5, 12.3**
  
  - [ ]* 9.4 Write unit tests for RocksDBMetricsCollector
    - Verificar que métricas de memoria, operaciones, y compactación están presentes
    - Verificar manejo de errores cuando Statistics no está disponible
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 9.1, 9.2, 9.3, 9.4, 9.5, 10.1, 10.2, 10.3, 10.4, 10.5, 13.2_

- [ ] 10. Checkpoint - Verificar métricas de RocksDB localmente
  - Iniciar la aplicación con Kafka habilitado
  - Procesar mensajes que generen operaciones en state stores
  - Verificar que /actuator/prometheus expone métricas de RocksDB con tags apropiados
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 11. Implementar validación de convención de nomenclatura
  - [ ]* 11.1 Write property test for metric naming convention
    - **Property 5: Metric Names Follow Micrometer Convention**
    - **Validates: Requirements 12.4, 12.5**
  
  - [ ]* 11.2 Write unit tests for metric naming validation
    - Verificar que todas las métricas registradas siguen la convención
    - Verificar prefijos apropiados (jvm.*, kafka.streams.*, rocksdb.*)
    - _Requirements: 12.4, 12.5_

- [ ] 12. Implementar tests de configuración externalizada
  - [ ]* 12.1 Write unit tests for Actuator endpoint configuration
    - Verificar que cambiar management.endpoints.web.exposure.include afecta endpoints disponibles
    - _Requirements: 14.1_
  
  - [ ]* 12.2 Write unit tests for metrics enablement configuration
    - Verificar que cambiar management.metrics.enable.* afecta métricas disponibles
    - _Requirements: 14.3_
  
  - [ ]* 12.3 Write unit tests for common tags configuration
    - Verificar que cambiar management.metrics.tags.* afecta tags en métricas
    - _Requirements: 14.4_

- [ ] 13. Implementar tests de manejo de errores
  - [ ]* 13.1 Write unit test for RocksDB metrics collection failure
    - Simular fallo en recolección de métricas de RocksDB
    - Verificar que la aplicación continúa y se registra log apropiado
    - _Requirements: 13.2, 13.5_
  
  - [ ]* 13.2 Write unit test for Kafka Streams metric not available
    - Simular métrica no disponible en Kafka Streams
    - Verificar que el listener continúa procesando otras métricas
    - _Requirements: 13.3, 13.5_

- [ ] 14. Crear documentación de métricas
  - Crear archivo METRICS.md en .kiro/specs/dynatrace-observability-integration/
  - Documentar todas las métricas de JVM con nombres, tipos, y descripciones
  - Documentar todas las métricas de Kafka Streams con nombres, tipos, tags, y descripciones
  - Documentar todas las métricas de RocksDB con nombres, tipos, tags, y descripciones
  - Incluir ejemplos de queries útiles para Dynatrace
  - Incluir recomendaciones de umbrales y alertas para métricas críticas
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

- [ ] 15. Crear documentación de arquitectura
  - Crear archivo .arquitectura.md en .kiro/specs/dynatrace-observability-integration/
  - Explicar de forma clara y concisa cada característica implementada
  - Listar todos los archivos involucrados con su propósito
  - Explicar el flujo de métricas desde origen hasta Dynatrace
  - Incluir diagramas de arquitectura (Mermaid)
  - Explicar qué problema resuelve cada componente y por qué se implementó así
  - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6_

- [ ] 16. Checkpoint final - Validación completa
  - Ejecutar todos los tests (unit y property-based)
  - Iniciar la aplicación localmente y verificar todos los endpoints de Actuator
  - Verificar que todas las métricas están presentes y correctamente etiquetadas
  - Revisar logs para asegurar que no hay errores de métricas
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada task referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Property tests validan propiedades de correctness universales
- Unit tests validan ejemplos específicos y casos edge
- La implementación es incremental: JVM → Kafka Streams → RocksDB
- La solución funciona tanto en local (sin Dynatrace) como en GKE (con OneAgent)
