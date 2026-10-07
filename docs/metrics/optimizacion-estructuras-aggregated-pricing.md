# Optimización de memoria/rendimiento en `AggregatedPricing` / `PricingStreamPunctuation`

Este documento responde a la sospecha de que `AggregatedPricingRecordPartialUpdate`
(construido por `AggregatedPricing.createBaseAggregation` y poblado en
`AggregatedPricing.aggregate`) es costoso en memoria durante el ciclo de
`onPunctuation`, y propone estructuras alternativas para agregar por `materialNumber`
sin perder la capacidad de enviar la información aguas abajo (Coveo).

El análisis parte únicamente del código Java compartido (este repo no contiene el
servicio del indexer, solo manifiestos de despliegue), así que algunos supuestos sobre
el `.avsc` (tipos de `price`, `stockStatusPtCode`, etc.) están marcados explícitamente.

---

## 1. Diagnóstico: por qué el objeto actual es costoso

`createBaseAggregation` crea **5 `HashMap` + 5 `ArrayList`** por cada `materialNumber`
sucio (uno por cada ciclo de punctuation, por cada entrada de `dirtyStore`):

```java
.setAddedprices(new HashMap<>())
.setAddedcontracts(new HashMap<>())
.setAddedstockstatuses(new HashMap<>())
.setAddedContractTypes(new HashMap<>())
.setAddedContractRanks(new HashMap<>())
.setRemovedprices(new ArrayList<>())
... (x5)
```

Y en `aggregate()`, por cada cliente (`kunnr`) de ese material se hace **hasta 1 `put()`
por cada uno de los 5 mapas** (`applyPrice`, `applyContract`, `applyStockStatus`,
`applyContractType`, `applyContractRank`), cada uno usando la misma clave `kunnr` de
forma independiente.

Esto es un diseño **column-oriented** (un mapa por campo) en vez de **row-oriented**
(un registro por cliente). El costo real:

| Fuente de costo | Detalle |
|---|---|
| Nodos de `HashMap` | Cada `put()` crea un `Node` (hash + key + value + next ≈ 32 bytes con compressed oops). Con 5 mapas, un material con **N clientes** puede generar hasta **5×N nodos** en vez de **N**. |
| Tablas internas repetidas | Cada `HashMap` resuelve su propio arreglo interno (16 slots por defecto, se duplica al crecer). 5 mapas = 5 tablas, 5 rehashes independientes, en vez de 1. Para materiales con cientos/miles de clientes (distribuidores grandes) esto es memoria transitoria significativa y presión de GC joven — justo lo que ya están vigilando en `jvm.gc`/`jvm.memory` según `decision-metricas-proyecto-kafka-streams.md`. |
| Boxing de `Integer` | `stockStatusPtCode` (asumiendo que es `int` primitivo en el Avro de `StrippedPricingRecord`) se autoboxea en cada `put()` a `addedstockstatuses`. Si el código no cae en el rango cacheado de `Integer` (-128..127), es una alocación nueva por cliente. `contractType` y `contractRank` **no** tienen este problema: `contractType` reutiliza los `Integer` ya cacheados en `CONTRACT_TYPE_CODE_MAP`, y `contractRank` usa el literal `10` (dentro del rango cacheado) — así que el boxing real está concentrado en `stockStatusPtCode`, no en los 3 mapas. |
| Costo fijo por material | Aun con pocos clientes, cada material sucio paga 10 objetos colección (5 `HashMap` + 5 `ArrayList`). En Java 8+ una `HashMap`/`ArrayList` vacía no reserva su tabla interna hasta el primer `put()`/`add()` (lazy), así que este costo fijo es bajo (~24-48 bytes por colección vacía) — **el problema no es el costo fijo, es el multiplicador ×5 cuando el material tiene muchos clientes.** |

**Conclusión del diagnóstico:** el objeto no es costoso por tener "muchos campos", es
costoso porque **duplica la clave `kunnr` en 5 estructuras de hashing independientes**
en vez de una sola. El impacto crece linealmente con el número de clientes por
material, que es exactamente el peor caso para los materiales de mayor volumen.

---

## 2. ¿Y las listas `removed*`? (sí, consideradas — pero son un problema distinto)

Las 5 listas (`removedprices`, `removedcontracts`, `removedstockstatuses`,
`removedContractTypes`, `removedContractRanks`) **no tienen el mismo problema que los 5
mapas "added".** Son `List<String>` respaldadas por un arreglo plano (`ArrayList`, sin
hashing): cada `.add()` cuesta solo un slot de referencia (~4 bytes con compressed
oops) más el crecimiento amortizado del arreglo (factor 1.5x), sin `Node` por entrada.
Por eso, **entrada por entrada, son ~8-10x más baratas que un `put()` en los mapas
"added"**. No son el sospechoso principal del consumo de heap.

Donde sí hay desperdicio real, es distinto al de los mapas:

| Problema | Dónde ocurre | Costo real |
|---|---|---|
| **Señal duplicada 5 veces** | `applyDeletion()` agrega el mismo `kunnr` a las 5 listas cuando `opCode == DELETE`. Es un solo hecho ("este cliente se eliminó por completo") escrito 5 veces. | Barato en heap (5 slots de referencia), pero **se serializa 5 veces en el payload Avro que se envía por Kafka** — el mismo `kunnr` repetido 5 veces en el mensaje de salida. Si "costoso" para ti también incluye el tamaño del batch transmitido (no solo el heap), esto sí pesa. |
| **Mismo mecanismo para borrado total y borrado parcial de un campo** | El esquema no distingue "este cliente se eliminó por completo" (el caso dominante de `applyDeletion`) de "se eliminó solo el precio de este cliente" (caso parcial en `applyPrice`, etc.). Ambos cuestan lo mismo: una escritura por lista afectada. | No hay compactación para el caso común (delete total): se paga el mismo costo que un borrado parcial de 1 solo campo, multiplicado por 5. |

**Conclusión:** las listas `removed*` no son el cuello de botella de heap (ese es
claramente el lado de los mapas "added", sección 1), pero sí tienen desperdicio de
**redundancia en el payload serializado**. La corrección correcta no es optimizar la
`ArrayList` en sí (ya es la estructura adecuada para ese propósito) — es resolverlo en
el mismo rediseño de esquema de la sección 4, reemplazando la señal repetida por un
flag/bitmask.

---

## 3. Opción recomendada (ganancia inmediata, sin tocar el esquema Avro ni al consumidor)

**Reemplazar `java.util.HashMap` por un mapa *open-addressing*** (sin nodos encadenados)
únicamente dentro de `AggregatedPricing.createBaseAggregation`. Los campos generados por
Avro para los mapas están tipados como la interfaz `java.util.Map<CharSequence, X>`, no
como `HashMap` concreto, así que cualquier implementación de `Map` es compatible con los
setters generados y con la serialización — **cero cambio de esquema, cero coordinación
con Coveo.**

```java
// fastutil (it.unimi.dsi:fastutil) o Eclipse Collections (org.eclipse.collections)
.setAddedprices(new Object2ObjectOpenHashMap<>())
.setAddedcontracts(new Object2ObjectOpenHashMap<>())
.setAddedstockstatuses(new Object2ObjectOpenHashMap<>())
.setAddedContractTypes(new Object2ObjectOpenHashMap<>())
.setAddedContractRanks(new Object2ObjectOpenHashMap<>())
```

Por qué rinde mejor:
- Open-addressing guarda claves/valores en arreglos planos (sin `Node` por entrada) →
  elimina el overhead de ~32 bytes/nodo y mejora la localidad de caché (menos *cache
  misses* en el loop caliente de `aggregate()`, que corre por cada registro de
  `processedStore.prefixScan`).
- Para los 3 mapas de valor `Integer` (`addedstockstatuses`, `addedContractTypes`,
  `addedContractRanks`) se puede ir un paso más allá con `Object2IntOpenHashMap`
  (fastutil) o `ObjectIntHashMap` (Eclipse Collections): guardan los valores en un
  `int[]` plano en vez de `Integer[]`, y siguen siendo asignables a
  `Map<CharSequence, Integer>` (autoboxean solo al leer, no al almacenar).
- Riesgo bajo: es un cambio de una línea por mapa, localizado en
  `AggregatedPricing.createBaseAggregation`, reversible, sin tocar el `.avsc` ni el
  contrato de Kafka.

**Requisito:** agregar la dependencia (`it.unimi.dsi:fastutil` o
`org.eclipse.collections:eclipse-collections`) si el proyecto no la tiene ya.

---

## 4. Opción de mayor impacto (requiere coordinar el esquema con Coveo)

La estructura verdaderamente más barata es **consolidar los 5 mapas "added" en una
sola lista (`array`) de *records*, donde cada record lleva su propia clave `kunnr`
junto con el valor** — es decir, exactamente lo que preguntas: en vez de
`Map<kunnr, CustomerPricingUpdate>`, un `List<CustomerPricingUpdate>` donde
`CustomerPricingUpdate` incluye el `kunnr` como campo. Esto es **mejor que la versión
con `Map` que propuse antes**, no solo igual de bueno — y vale explicar por qué.

```json
{
  "name": "customerPricingUpdate",
  "type": "record",
  "fields": [
    {"name": "kunnr", "type": "string"},
    {"name": "price", "type": ["null", "double"], "default": null},
    {"name": "contract", "type": ["null", "string"], "default": null},
    {"name": "stockStatusPtCode", "type": ["null", "int"], "default": null},
    {"name": "contractType", "type": ["null", "int"], "default": null},
    {"name": "contractRank", "type": ["null", "int"], "default": null},
    {"name": "removed", "type": "boolean", "default": false},
    {"name": "removedFieldsMask", "type": "int", "default": 0}
  ]
}
```

y en `AggregatedPricingRecordPartialUpdate`, un único campo tipo `array`, no `map`:

```json
{"name": "customerUpdates", "type": {"type": "array", "items": "customerPricingUpdate"}, "default": []}
```

### Por qué una lista es más barata que un mapa aquí (no solo "igual de buena")

Un `Map` solo vale su costo de hashing si necesitas **buscar/actualizar una entrada
existente por clave**. Mira el patrón real de acceso en `aggregate()`:
`processedStore.prefixScan(materialNumber)` entrega **como máximo una entrada por
`kunnr`**, porque `process()` ya sobrescribe (`processedStore.put(key, ...)`) cualquier
registro previo de ese mismo `matnr-kunnr` antes de que llegue la punctuation. Es decir:
dentro de un mismo ciclo de agregación, **cada cliente aparece exactamente una vez**, y
`aggregate()` nunca necesita leer de vuelta ni fusionar con una entrada anterior de ese
mismo cliente — construye el `CustomerPricingUpdate` completo a partir de ese único
`StrippedPricingRecord` y ya.

Si nunca buscas por clave, el `Map` solo te está cobrando el costo de un mecanismo
(hashing + tabla de buckets + factor de carga) que no usas. Un `List`/`array` elimina
eso por completo: nada de `hashCode()`, nada de tabla interna, solo un slot de
referencia por entrada (~4 bytes) con crecimiento amortizado — más barato que incluso el
`Object2ObjectOpenHashMap` de la sección 3.

El loop de agregación queda más simple también (sin `computeIfAbsent`, solo construir y
agregar):

```java
CustomerPricingUpdate upd = buildCustomerUpdate(record); // incluye record.getKunnr()
aggregated.getCustomerUpdates().add(upd);
// sin hashing, sin tabla, sin búsqueda — construir una vez, append una vez
```

**Impacto:** de ~5×N nodos de `HashMap` + hasta 5×N slots de `ArrayList` a ~N slots de
`array` para un material con N clientes — más barato que la variante con `Map` de la
sección anterior. También reduce el payload serializado (ya no se repite `kunnr` como
clave/valor en 10 estructuras distintas al enviarlo por Kafka).

**La única condición para que esto sea seguro:** depende de que `processedStore`
garantice como máximo un registro por `(matnr, kunnr)` entre punctuations — cosa que
hoy es cierta por el `put()` sobrescribible en `process()`. Si esa garantía cambiara
(por ejemplo, si `processedStore` llegara a acumular varios registros pendientes por
cliente en vez de solo el último), una lista produciría entradas duplicadas/conflictivas
para el mismo cliente, y ahí sí haría falta volver a un `Map` (o agregar un paso de
*merge* explícito antes de construir la lista).

**El otro trade-off, del lado del consumidor:** con un `Map`, Coveo recibe la
estructura ya indexada por `kunnr`; con un `array`, si Coveo necesita acceso por clave
tendría que construir su propio índice al recibir el batch. Para un consumidor que solo
itera el batch y aplica cada actualización (el patrón típico de "aplicar un lote de
cambios a un índice de búsqueda"), esto no es un problema — pero es parte de lo que hay
que confirmar con el equipo de Coveo al coordinar el cambio de esquema.

**Costo:** sigue siendo un cambio de contrato — requiere evolución del `.avsc` y que el
consumidor de Coveo entienda la nueva forma. No es "quick win"; tiene que planearse
como:
1. Agregar el campo nuevo como aditivo (no romper compatibilidad), publicar en paralelo.
2. Validar con el equipo de Coveo el consumo del nuevo campo (iteración vs. necesidad de
   lookup por `kunnr`).
3. Deprecar y luego retirar los 10 campos viejos en una ventana acordada.

---

## 5. Hallazgos adicionales en el *hot path* (no estructurales, pero relevantes)

- **`process()` usa `"%s-%s".formatted(...)` en el camino caliente** (una vez por cada
  registro entrante). `String.format`/`.formatted()` parsea el patrón en cada llamada;
  en un hot path de alto throughput, reemplazarlo por concatenación simple
  (`record.value().getMatnr() + "-" + record.value().getKunnr()`) es más rápido y
  evita el *regex parsing* interno. (La llamada equivalente en `init()` para
  `processorTaskId` no importa, corre una sola vez.)

- **Posible colisión en `prefixScan(materialNumber, ...)`:** las claves de
  `processedStore` son `"{matnr}-{kunnr}"`, pero `dirtyStore` guarda `matnr` sin el
  separador. Si un `materialNumber` es prefijo literal de otro (p. ej. `"10"` y
  `"100"`), `prefixScan("10")` también matchea las entradas de `"100-5"`, agregando
  clientes del material equivocado. Vale la pena escanear con
  `materialNumber + "-"` para forzar el límite del delimitador. Esto no es un tema de
  memoria, pero sí de corrección en la agregación "por material number" que estás
  revisando.

---

## 6. Plan de acción sugerido

| Paso | Qué | Esfuerzo | Riesgo | Cuándo |
|---|---|---|---|---|
| 1 | Swap `HashMap`→`Object2ObjectOpenHashMap`/`Object2IntOpenHashMap` en `createBaseAggregation` | Bajo | Bajo | Inmediato |
| 2 | Reemplazar `.formatted()` por concatenación en `process()` | Muy bajo | Nulo | Inmediato |
| 3 | Corregir el límite de `prefixScan` con `materialNumber + "-"` | Muy bajo | Bajo (corrige comportamiento) | Inmediato |
| 4 | Medir antes/después con `jvm.memory`/`jvm.gc` (ya habilitados) en PRF | — | — | Después de 1-3 |
| 5 | Diseñar el `customerUpdates` consolidado y coordinar con Coveo | Alto | Medio (contrato compartido) | Próximo trimestre, si 1-3 no son suficientes |

Los pasos 1-3 se pueden desplegar ya mismo sin coordinación externa. El paso 5 es la
estructura "ideal" en memoria/rendimiento, pero su costo es organizacional (cambio de
contrato), no técnico — tiene sentido solo si, tras medir el impacto de 1-3 en PRF, el
heap/GC sigue mostrando presión alta durante `onPunctuation`.
