# Métricas recomendadas para un sistema Kafka Streams de alto volumen (near-time) con Punctuator + batching + Producer

Contexto asumido: consumes con Kafka Streams a alta frecuencia, acumulas registros en memoria
(estado) y usas un **Punctuator** (`schedule(...)`) para disparar el envío de un batch acumulado
vía un **Kafka Producer**. Los riesgos típicos de este patrón son: **lag de consumo**, **rebalanceos**,
**presión de memoria/heap por el batching**, **latencia/backpressure del producer**, y si usas
state stores, **I/O de disco (RocksDB)**.

Cómo consultarlas (3 formas comunes):
1. **JMX directo** (jconsole/jmxterm): MBeans bajo `kafka.streams:*`, `kafka.consumer:*`, `kafka.producer:*`.
2. **Micrometer + Actuator** (si usas `micrometer-registry-*` con Kafka Streams/Spring Kafka):
   `GET /actuator/metrics/{nombre}` — el detalle trae `availableTags` (`client-id`, `task-id`,
   `thread-id`, `processor-node-id`, `topic`, `partition`).
3. **Prometheus JMX Exporter**: expone las mismas métricas JMX como `/metrics` en formato Prometheus,
   ideal si ya tienes Grafana.

---

## 1. Kafka Streams — nivel Thread / Task (salud del procesamiento)

| Métrica | Qué mide | Por qué importa en tu caso |
|---|---|---|
| `process-rate` / `process-total` | Registros procesados por stream-thread por segundo | Tu indicador principal de throughput real. Cae bajo demanda alta → cuello de botella. |
| `process-latency-avg` / `-max` | Latencia promedio/máxima de procesar un registro | Si sube, cada mensaje tarda más en pasar por tu topología (incluye tu lógica de acumulación). |
| `commit-rate` / `commit-latency-avg` | Frecuencia y latencia de los commits de Streams | Commits lentos o poco frecuentes = riesgo de reprocesar más datos si hay falla. |
| `poll-rate` / `poll-latency-avg` | Qué tan seguido y rápido el thread hace poll al consumer interno | Si baja mucho, tu Punctuator o el processing está bloqueando el loop principal. |
| `task-created-rate` / `task-closed-rate` | Tasas de creación/cierre de tasks | Picos = rebalanceos frecuentes, muy costoso en streams con estado. |

**Tag clave:** `thread-id` (para ver por hilo) y `task-id` (para ver por partición/task).

---

## 2. Kafka Streams — nivel Processor Node (tu Punctuator específicamente)

| Métrica | Qué mide | Por qué importa |
|---|---|---|
| `process-rate` (por `processor-node-id`) | Throughput del nodo específico donde vive tu lógica de acumulación | Aísla si el cuello de botella es tu procesador de acumulación vs otro nodo de la topología. |
| `punctuate-rate` / `punctuate-latency-avg` / `-max` | Cuántas veces se dispara el Punctuator y cuánto tarda cada ejecución | **La más importante para tu caso.** Si `punctuate-latency` es alta, el envío/flush del batch está tardando y puede retrasar el procesamiento normal (los punctuators corren en el mismo hilo que el resto del processing). |
| `forward-rate` | Registros reenviados hacia adelante en la topología (o al sink) | Te dice qué tan seguido tu lógica efectivamente "emite" el batch acumulado. |

**Nota práctica:** si `punctuate-latency-max` empieza a acercarse a tu intervalo del punctuator
(`schedule(Duration...)`), el batching se está atrasando respecto al tiempo real (rompe el "near-time").

---

## 3. Kafka Streams — Consumer interno (lag y lectura del topic origen)

| Métrica | Qué mide | Por qué importa |
|---|---|---|
| `records-lag` / `records-lag-max` (por partición) | Cuántos registros faltan por leer respecto al último offset del topic | **La métrica #1 de salud "near-time".** Si crece, no estás alcanzando el ritmo de llegada de datos. |
| `records-consumed-rate` / `bytes-consumed-rate` | Volumen de datos leídos por segundo | Compárala con la tasa de producción del topic origen para saber si vas a la par. |
| `fetch-latency-avg` | Latencia de las peticiones fetch al broker | Latencia alta = problema de red o brokers sobrecargados, no de tu app. |
| `commit-latency-avg` | Latencia al commitear offsets | Relevante para tolerancia a fallos (cuánto se puede reprocesar si hay caída). |
| `rebalance-total` / `last-rebalance-seconds-ago` | Rebalanceos del consumer group | Con estado (acumulación en memoria) los rebalanceos son costosos: se pierde el batch parcial de esa partición. |

**Tag clave:** `topic`, `partition`, `client-id`.

---

## 4. Kafka Producer (envío del batch acumulado)

| Métrica | Qué mide | Por qué importa |
|---|---|---|
| `record-send-rate` | Registros enviados por segundo | Throughput real de salida; compáralo con lo que entra para detectar acumulación excesiva. |
| `batch-size-avg` / `batch-size-max` | Tamaño promedio/máx de los batches enviados al broker | Te confirma si tu estrategia de acumulación está generando batches del tamaño esperado (ni muy chicos ni saturando `max.request.size`). |
| `request-latency-avg` / `-max` | Latencia de las requests de producción al broker | Si sube, el envío del batch acumulado tarda más → puede acumularse el siguiente batch antes de liberar el anterior. |
| `record-queue-time-avg` | Tiempo que un registro espera en el buffer antes de enviarse | Indicador temprano de backpressure del producer. |
| `buffer-available-bytes` | Memoria libre en el buffer del producer (`buffer.memory`) | Si tiende a 0, el producer no puede aceptar más registros → tu Punctuator se bloqueará al intentar enviar (backpressure real). |
| `record-error-rate` / `record-retry-rate` | Tasa de errores/reintentos al enviar | Errores sostenidos = pérdida de datos o retrasos graves en el batch. |
| `compression-rate-avg` | Ratio de compresión efectivo | Si usas compresión (recomendado con batches grandes), valida que realmente esté comprimiendo bien. |

---

## 5. State Store / RocksDB (solo si tu acumulación usa un `StateStore`, no memoria pura en el processor)

| Métrica | Qué mide | Por qué importa |
|---|---|---|
| `rocksdb.bytes-written-rate` | Bytes escritos a disco por RocksDB | Alto volumen de acumulación con state store = mucha escritura a disco; vigila esto junto con IOPS del disco. |
| `rocksdb.write-stall-duration-avg` | Tiempo que las escrituras se detienen por saturación de RocksDB | Señal directa de que el disco/compactación no aguanta el ritmo de escritura. |
| `restore-rate` / `restore-latency` | Velocidad de restauración del state store tras un fallo/rebalanceo | Restauraciones lentas = más tiempo sin procesar tras una caída, crítico en near-time. |

Si en cambio acumulas el batch en una estructura en memoria (`List`, mapa) dentro del `Processor`
**sin** un `StateStore` de Streams, esta sección no aplica — pero entonces la presión cae directo
sobre el heap de la JVM (ver sección 6).

---

## 6. JVM / Memoria / CPU / Disco (recomendadas para este patrón específico)

A diferencia de una app CRUD típica, aquí importan más las métricas de **presión de heap por
acumulación** y **GC pauses**, porque estás retiniendo objetos en memoria entre punctuaciones.

| Métrica | Qué mide | Por qué importa en tu caso |
|---|---|---|
| `jvm.memory.used` (`area:heap`) | Heap usado | Debe tener un patrón de "diente de sierra": crece mientras acumulas el batch, cae al enviarlo. Si no cae, el batch no se está liberando (leak o falla en el envío). |
| `jvm.gc.pause` (`sum`, `max`) | Duración de pausas GC | Pausas largas justo antes/durante una punctuación = mayor riesgo de que el batch crezca más de lo esperado (el consumer sigue recibiendo mientras el GC pausa el envío). |
| `jvm.gc.overhead` | % CPU gastado en GC | Con alto volumen de allocs (records entrando constantemente), este es tu indicador de si el heap está bien dimensionado. |
| `jvm.gc.memory.promoted` | Objetos que sobreviven a young gen | Si tu batch vive varios ciclos de GC antes de enviarse, es normal que suba; pero si sube sin control, hints de que el intervalo del punctuator es muy largo para el volumen. |
| `process.cpu.usage` | CPU del proceso | Compara picos con los momentos de `punctuate-rate` para saber si el envío del batch (serialización + I/O) es el que consume la CPU. |
| `system.cpu.usage` | CPU del host/nodo | Para detectar saturación a nivel de nodo (importante en GKE si compartes nodo con otros pods). |
| `disk.free` / uso de `ephemeral-storage` del pod | Espacio libre en disco | Relevante si usas RocksDB (state stores) o logging local intensivo; en GKE puedes monitorear esto vía métricas del nodo/kubelet o `container_fs_usage_bytes` de cAdvisor. |
| `jvm.threads.live` / `jvm.threads.states` | Hilos activos y su estado | Streams crea un hilo por `num.stream.threads`; útil para confirmar que no hay hilos bloqueados esperando el producer (backpressure). |

---

## Resumen mental (para interiorizar rápido)

- **¿Voy al ritmo de los datos?** → `records-lag` (consumer interno de Streams).
- **¿Mi Punctuator se está demorando?** → `punctuate-latency-avg/max`.
- **¿El envío del batch es el cuello de botella?** → `batch-size-avg`, `request-latency`, `buffer-available-bytes` (producer).
- **¿La acumulación está presionando memoria?** → `jvm.memory.used` (patrón diente de sierra) + `jvm.gc.pause`.
- **¿Hay riesgo de perder el batch parcial?** → `rebalance-total` / `last-rebalance-seconds-ago`.
- **¿El disco aguanta?** → métricas RocksDB (solo si usas state store) o `disk.free` del pod/nodo.
