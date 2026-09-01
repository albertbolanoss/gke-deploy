# Decisión: qué métricas habilitar en `management.metrics.enable` (proyecto real Kafka Streams + Punctuator + Producer)

Este documento evalúa la configuración real vista en `application-prf.yml`
(`ecomm-nxtgen-kafka-pd-coveo-streams-indexer`) y da una recomendación concreta de
**qué dejar en `true`, qué cambiar, y qué agregar**, considerando que el destino final
de las métricas es **Dynatrace** (exportador activo, Prometheus desactivado) — esto
importa porque Dynatrace cobra por volumen/cardinalidad de datos ingeridos (DDU), no
es "gratis" habilitar todo.

Config actual detectada:
```yaml
metrics:
  enable:
    all: false
    jvm.memory: true
    jvm.gc: true
    process.cpu: true
    system.cpu: true
    kafka.stream: false
    kafka.producer: false
dynatrace:
  metrics:
    export:
      enabled: true
      step: 15m
```

---

## Tabla de decisión

| Flag | Estado actual | Recomendación | Por qué |
|---|---|---|---|
| `jvm.memory` | `true` | **Mantener `true`** | Tu Punctuator acumula batches en memoria antes de enviarlos. Sin esto no puedes ver si el heap crece de forma sana (diente de sierra) o si hay leak. Bajo costo de cardinalidad. |
| `jvm.gc` | `true` | **Mantener `true`** | Alta tasa de ingesta constante = mucha creación de objetos. GC pauses largas retrasan tanto el consumo como el envío del batch. Bajo costo de cardinalidad (pocos pools de GC). |
| `process.cpu` | `true` | **Mantener `true`** | Simple, 1 sola serie, te dice si tu app está saturando CPU. Costo casi nulo. |
| `system.cpu` | `true` | **Mantener `true`** | Te distingue "mi app está lenta" vs "el nodo/pod está saturado por otros procesos". Costo casi nulo. |
| `kafka.stream` | `false` | **Cambiar a `true`, pero con filtro (ver abajo)** | Es la única forma de ver `records-lag` (si vas al ritmo del topic), `punctuate-latency` (si tu Punctuator se demora) y `commit-latency`. Sin esto, estás "ciego" respecto al corazón de tu procesamiento. Riesgo: alta cardinalidad (tags por `thread-id`, `task-id`, `processor-node-id`, `topic`, `partition`) → puede disparar el costo en Dynatrace si tienes muchas particiones/tasks. |
| `kafka.producer` | `false` | **Cambiar a `true`, pero con filtro (ver abajo)** | Sin esto no puedes saber si el envío del batch generado por el Punctuator es eficiente (`batch-size-avg`, `request-latency`) ni detectar backpressure (`buffer-available-bytes`). Riesgo de cardinalidad menor que `kafka.stream` (menos tags), aceptable habilitarlo completo. |

**Conclusión rápida:** los comentarios que ya escribiste en el YAML (`# Essentials for
Measuring the Performance of Your Streams`, `# and the efficiency of sending the
batches...`) son correctos — la config actual **te deja sin visibilidad justo en las
dos partes más críticas de tu arquitectura** (el Punctuator y el envío del batch).
El motivo por el que probablemente están en `false` hoy es control de costo/ruido en
Dynatrace, no que sean innecesarias.

---

## Flags que faltan y deberías agregar

| Flag a agregar | Por qué |
|---|---|
| `kafka.consumer: true` | No aparece en la config visible. El consumer interno de Kafka Streams es el que reporta `records-lag`, que es tu métrica #1 de salud "near-time". Si `kafka.stream: true` no incluye estas métricas en tu versión de Spring Boot, necesitas este flag explícito. Verifica en `/actuator/metrics` tras el cambio si `kafka.consumer.records.lag` aparece. |
| `jvm.threads: true` | Barato y útil: confirma que ningún hilo de Streams quede bloqueado esperando al producer (backpressure real). |

---

## Cómo controlar el costo/cardinalidad antes de habilitar en PRD

Habilitar `kafka.stream` sin filtro expone **decenas de métricas por thread/task/nodo**.
En un topology con varios processor nodes y muchas particiones, eso multiplica rápido
las series únicas que Dynatrace factura. Recomendación de rollout:

1. **Habilita primero en PRF** (ya estás ahí) y observa cuántas series nuevas aparecen:
   `GET /actuator/metrics` antes y después, y compara el conteo.
2. **Filtra tags de alta cardinalidad** que no necesites permanentemente, por ejemplo
   excluyendo `processor-node-id` si solo te importa el agregado por `thread-id`. Esto se
   hace con un `MeterFilter` (bean `MeterFilter.deny(...)` o `.ignoreTags(...)`) en el
   código, no desde el YAML.
3. **Sube el `step` en Dynatrace si el volumen es alto** (ya está en `15m`, razonable
   para no disparar costo; no lo bajes a menos que necesites diagnosticar algo puntual).
4. Si tras medir el volumen en PRF el costo es aceptable, promueve el mismo cambio a
   producción (`application-prod.yml` o el equivalente).

---

## Config recomendada (resultado final)

```yaml
metrics:
  enable:
    all: false
    jvm.memory: true
    jvm.gc: true
    jvm.threads: true          # nuevo
    process.cpu: true
    system.cpu: true
    kafka.stream: true         # cambiado de false -> true
    kafka.producer: true       # cambiado de false -> true
    kafka.consumer: true       # nuevo (si tu versión lo requiere para records-lag)
```

## Resumen mental
- Lo que ya tenías en `true` (JVM/CPU) — **correcto, mantenerlo**, es barato y da la base de salud del proceso.
- Lo que tenías en `false` (`kafka.stream`, `kafka.producer`) — **es justo lo que necesitas para medir tu arquitectura real** (Punctuator + batch + producer), así que enciéndelo.
- La única razón válida para no encenderlo ya es **costo/cardinalidad en Dynatrace** — mitígalo midiendo primero en PRF, no dejándolo apagado indefinidamente.
