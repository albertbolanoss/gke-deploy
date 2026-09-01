# Métricas base de Spring Boot Actuator (`/actuator/metrics`)

Estas son las métricas que expone por defecto Actuator (vía Micrometer) para cualquier
app JVM/Spring Boot, agrupadas en 3 temas: **Garbage Collector (GC)**, **Memoria JVM** y **CPU**.

Cómo consultarlas:
- Listar nombres: `GET /actuator/metrics`
- Ver detalle + tags de una métrica: `GET /actuator/metrics/{nombre}`
  Ejemplo: `GET /actuator/metrics/jvm.memory.used`
- Filtrar por tag: `GET /actuator/metrics/{nombre}?tag=id:G1 Old Gen`
  El detalle de cada métrica te dice qué `availableTags` tiene (ej: `id`, `area`, `cause`).

---

## GC (Garbage Collector)

| Métrica | Qué mide | Cómo interpretarla |
|---|---|---|
| `jvm.gc.pause` | Duración e histórico de las pausas de GC (timer: count, sum, max) | Si `max` o el promedio (`sum/count`) crece, el GC está tardando más → posible presión de memoria o mal tuning de heap. Tag `cause` te dice si fue GC menor o mayor. |
| `jvm.gc.overhead` | % de tiempo de CPU que el proceso gasta en GC vs trabajo real | Regla general: >10-15% sostenido es señal de alerta (mucho tiempo "perdido" en GC). |
| `jvm.gc.memory.allocated` | Bytes asignados en el heap tras un ciclo de GC joven | Tasa alta = tu app crea muchos objetos por segundo (allocation rate alto), fuerza más GCs. |
| `jvm.gc.memory.promoted` | Bytes que pasan de generación joven (young) a vieja (old) | Si sube mucho, objetos que deberían ser de corta vida están sobreviviendo → posible fuga o mal sizing del young gen. |
| `jvm.gc.live.data.size` | Tamaño de datos "vivos" en el heap justo después de un GC mayor | Tendencia creciente en el tiempo = posible memory leak. |
| `jvm.gc.max.data.size` | Tamaño máximo estimado de heap disponible para datos vivos | Te da el techo teórico; compáralo con `live.data.size` para ver cuánto margen tienes. |
| `jvm.gc.concurrent.phase.time` | Tiempo en fases concurrentes de recolectores como G1/ZGC | Solo aplica si usas un GC con fases concurrentes; útil para diagnosticar pausas "invisibles". |

**Tip práctico:** las métricas más vigiladas en producción normalmente son `jvm.gc.pause` (latencia) y `jvm.gc.overhead` (costo de CPU).

---

## Memoria JVM

| Métrica | Qué mide | Cómo interpretarla |
|---|---|---|
| `jvm.memory.used` | Memoria actualmente usada (heap y no-heap) | Compárala con `jvm.memory.max` para saber el % de uso. Usa tag `area:heap` o `area:nonheap`, y `id` para el pool específico (ej. `G1 Eden Space`). |
| `jvm.memory.committed` | Memoria que la JVM tiene reservada al SO (no toda está en uso) | Si `committed` se acerca a `max`, la JVM ya casi no tiene margen para crecer. |
| `jvm.memory.max` | Límite máximo configurado (heap: `-Xmx`; metaspace, etc.) | Es tu "techo". `used/max` cerca de 90% sostenido = riesgo de `OutOfMemoryError`. |
| `jvm.memory.usage.after.gc` | % de heap usado inmediatamente después de un GC | Si este valor queda alto (ej. >70-80%) después de cada GC, significa que el GC no está liberando suficiente → posible leak o heap subdimensionado. |

**Tip práctico:** este es el trío clave para monitorear salud de memoria: `used`, `max`, y `usage.after.gc`.

---

## CPU

| Métrica | Qué mide | Cómo interpretarla |
|---|---|---|
| `process.cpu.usage` | % de CPU usado por el proceso de tu app (0.0 a 1.0) | Multiplica por 100 para ver %. Útil para ver si tu app específica consume mucha CPU. |
| `process.cpu.time` | Tiempo total de CPU acumulado por el proceso (nanosegundos) | Es acumulativo (contador), mejor mirar su tasa de cambio (delta por minuto) que el valor absoluto. |
| `system.cpu.usage` | % de CPU usado por todo el sistema/host (0.0 a 1.0) | Compáralo con `process.cpu.usage`: si el sistema está alto pero tu proceso bajo, hay otros procesos compitiendo por CPU (vecino ruidoso en el pod/nodo). |
| `system.cpu.count` | Número de cores disponibles para la JVM | No es una métrica de salud, es contexto: te ayuda a normalizar los % anteriores (ej. 1 core saturado en una VM de 4 cores no es tan grave). |

---

## Resumen mental (para interiorizar rápido)
- **GC** = "¿cuánto le cuesta a la JVM limpiar memoria y qué tan seguido?" → `pause`, `overhead`.
- **Memoria** = "¿qué tan cerca estoy del límite y se libera bien después de limpiar?" → `used` vs `max`, `usage.after.gc`.
- **CPU** = "¿mi app o el host están saturados?" → `process.cpu.usage` vs `system.cpu.usage`.
