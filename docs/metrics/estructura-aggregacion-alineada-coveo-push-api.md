# Estructura de agregación alineada al contrato Push API de Coveo (`partialUpdate`)

Las imágenes que compartiste muestran el **contrato de salida real** que este pipeline
debe producir (documento `partialUpdate` del Push API de Coveo). Esto cambia una parte
de lo que recomendé en
[`evaluacion-added-removed-listas-delimitadas.md`](./evaluacion-added-removed-listas-delimitadas.md)
— y vale explicar honestamente por qué, en vez de forzar consistencia con lo anterior.

---

## 1. Qué revela el contrato de las imágenes

```json
{ "documentId": "product://1009281", "operator": "dictionaryPut",    "field": "ec_ineligible_flag_dict", "value": { "2150452695": 1, "2150426166": 1, ... } }
{ "documentId": "product://1009281", "operator": "dictionaryRemove", "field": "ec_buying_group_code",    "value": [ "2057190756", "2057213072", ... ] }
{ "documentId": "product://1009281", "operator": "fieldValueReplace","field": "pricingoffsetid",         "value": "19|1238501677|1790792721860" }
```

Tres hechos importantes:

1. **`documentId` = `"product://" + materialNumber`** — cada documento Coveo es un
   material, igual que la clave de `dirtyStore`/`prefixScan` en `PricingStreamPunctuation`.
2. **El `operator` exige una *forma* específica de `value`:**
   - `dictionaryPut` → `value` debe ser un **objeto JSON** (`Map<String, Object>`). No
     puede ser un string, un array, ni nada distinto a un diccionario.
   - `dictionaryRemove` → `value` debe ser un **array JSON** (`List<String>`).
   - `fieldValueReplace` → `value` es un **escalar** (aquí, un string ya empacado).
3. **`"pricingoffsetid"` con valor `"19|1238501677|1790792721860"` coincide con el
   campo `offsetId`** que ya existe en `AggregatedPricing.aggregate()`
   (`aggregated.setOffsetId(record.getOffsetId())`). Esto confirma que estas
   operaciones salen de la misma `AggregatedPricingRecordPartialUpdate` del pricing
   stream — no es un contrato ajeno.

**Nota:** los nombres de campo exactos de Coveo para precio/contrato/stock/tipo de
contrato/rango de contrato no están en el código que compartiste (`ec_ineligible_flag_dict`
y `ec_buying_group_code` no corresponden literalmente a `price`/`contract`/etc.). Abajo
uso nombres de campo ilustrativos (`ec_price_dict`, etc.) — reemplázalos por los reales
de tu mapeo Coveo.

---

## 2. Por qué esto cambia la recomendación de la sección 4 del documento anterior

En `evaluacion-added-removed-listas-delimitadas.md` recomendé consolidar los 5 mapas +
5 listas en **una sola lista de records por cliente** (`List<CustomerPricingUpdate>`),
razonando que el patrón de acceso en `aggregate()` nunca necesita buscar por clave, así
que un `Map` era un costo innecesario.

Esa lógica seguía siendo válida *para el acceso interno*. Lo que no tenía en cuenta es
el **destino final**: Coveo exige, **por cada campo**, un diccionario para las altas y
un arreglo para las bajas. Si consolidas en una lista de records por cliente, en algún
punto —esta aplicación u otra— **tienes que volver a "explotar" esa lista en 5
diccionarios + 5 arreglos** antes de poder construir las operaciones `dictionaryPut`/
`dictionaryRemove`. Esa explosión:

- Hace exactamente el mismo trabajo que ya evitabas (crear 5 mapas, 5 listas, con N
  escrituras cada uno).
- Además paga el costo de haber creado la lista intermedia de records primero (N
  objetos `CustomerPricingUpdate` que luego se descartan).

**Conclusión:** dado el contrato real, consolidar en una lista por cliente no ahorra
trabajo — lo mueve y le agrega una copia intermedia. La estructura original (un
`Map`/`List` por campo) está, estructuralmente, bien alineada con el destino. El
problema de rendimiento nunca estuvo en "cuántas colecciones", sino en **qué
implementación de colección se usa** (eso sí lo mantengo de la sección 3 del documento
anterior).

---

## 3. Estructura de mejor rendimiento dado este contrato

1. **Mantener un `Map` por campo para altas y un `List` por campo para bajas** — es lo
   que Coveo necesita, sin traducción adicional.
2. **Usar colecciones *open-addressing* (fastutil/Eclipse Collections) en vez de
   `HashMap`/`ArrayList` estándar** — sigue siendo la ganancia de bajo riesgo que
   describí antes: menos nodos, sin boxing innecesario en los mapas de `Integer`,
   mejor localidad de caché. Esto no cambia por el nuevo contrato — al contrario, ahora
   es claramente la *única* optimización estructural que vale la pena del lado del
   productor, porque la forma (Map/List) no se puede evitar.
3. **No copiar los mapas/listas al armar el payload final — pasarlos por
   referencia.** Si `value` de un `dictionaryPut` acepta cualquier objeto serializable
   como JSON, no hay que convertir el `Map` deserializado a un `HashMap` "limpio"
   antes de serializarlo: cualquier `Map`/`List` (incluido uno de fastutil) se
   serializa igual con Jackson u otra librería JSON que trabaje por interfaz.
4. **Omitir la operación si el mapa/lista queda vacío.** Si un material no tuvo altas
   de precio en el ciclo, no generes un `dictionaryPut` con `value: {}` — ni Coveo ni
   la red necesitan ese ruido.
5. **El campo escalar (`pricingoffsetid`) no tiene el problema de los strings
   delimitados de la sección 2 del documento anterior.** Ese análisis aplicaba a
   formatear **por cliente** (costo que escala con N clientes). `pricingoffsetid` es
   **un valor por material**, construido una sola vez por ciclo de punctuation — el
   costo de empacarlo en un string es O(1), no O(N). Por eso Coveo ya usa esa
   convención aquí sin problema.

### Advertencia sobre el alcance del beneficio de fastutil

El swap a fastutil solo reduce la presión de GC **en esta aplicación**, durante
`onPunctuation`. Si `AggregatedPricingRecordPartialUpdate` se envía por Kafka
serializado en Avro binario, el *wire format* es el mismo sin importar qué
implementación de `Map` se usó en memoria (Avro serializa iterando `entrySet()`, no le
importa la clase concreta). Pero al deserializar en la aplicación consumidora, el
lector Avro generado típicamente reconstruye los mapas como `java.util.HashMap`
estándar — **el beneficio de fastutil no viaja automáticamente a la otra
aplicación**. Si esa aplicación también sufre presión de GC al construir el payload de
Coveo, necesita aplicar la misma optimización de forma independiente del lado de su
deserialización/armado de payload.

---

## 4. Clases Java propuestas

### 4.1 `AggregatedPricingRecordPartialUpdate` — sin cambio de esquema, solo de implementación

El `.avsc` **no cambia frente al original** que me compartiste al inicio — ya estaba
bien alineado con Coveo. Lo único que cambia es qué colección concreta se usa al
construirlo:

```java
package com.cardinalhealth.coveo.streams.indexer.config.kstreams.transformer;

import com.cardinalhealth.coveo.streams.indexer.avro.AggregatedPricingRecordPartialUpdate;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class AggregatedPricing {

    public static AggregatedPricingRecordPartialUpdate createBaseAggregation(String materialNumber) {
        return AggregatedPricingRecordPartialUpdate.newBuilder()
                .setMaterialNumber(materialNumber)
                .setAddedprices(new Object2DoubleOpenHashMap<>())
                .setAddedcontracts(new Object2ObjectOpenHashMap<>())
                .setAddedstockstatuses(new Object2IntOpenHashMap<>())
                .setAddedContractTypes(new Object2IntOpenHashMap<>())
                .setAddedContractRanks(new Object2IntOpenHashMap<>())
                .setRemovedprices(new ObjectArrayList<>())
                .setRemovedcontracts(new ObjectArrayList<>())
                .setRemovedstockstatuses(new ObjectArrayList<>())
                .setRemovedContractTypes(new ObjectArrayList<>())
                .setRemovedContractRanks(new ObjectArrayList<>())
                .setCompressedAdds(0L)
                .setCompressedDeletes(0L)
                .setCompressedCount(0L)
                .setTotalMessageCount(0L)
                .build();
    }

    // aggregate(), applyPrice(), applyContract(), applyStockStatus(), applyContractType(),
    // applyContractRank(), applyAddition(), applyDeletion(): SIN CAMBIOS respecto al
    // código original — ya escriben contra la interfaz Map/List, no contra HashMap/ArrayList
    // concretos, así que el swap de arriba es la única edición necesaria en esta clase.
}
```

### 4.2 `PartialUpdateOperation` — modela exactamente la forma de las imágenes

```java
package com.cardinalhealth.coveo.streams.indexer.config.coveo;

public record PartialUpdateOperation(String documentId, String operator, String field, Object value) {
}
```

### 4.3 `CoveoPartialUpdateMapper` — construye las operaciones sin copiar los mapas/listas

```java
package com.cardinalhealth.coveo.streams.indexer.config.coveo;

import com.cardinalhealth.coveo.streams.indexer.avro.AggregatedPricingRecordPartialUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CoveoPartialUpdateMapper {

    private static final String DICTIONARY_PUT = "dictionaryPut";
    private static final String DICTIONARY_REMOVE = "dictionaryRemove";
    private static final String FIELD_VALUE_REPLACE = "fieldValueReplace";

    private CoveoPartialUpdateMapper() {
    }

    public static List<PartialUpdateOperation> toPartialUpdateOperations(AggregatedPricingRecordPartialUpdate aggregated) {
        String documentId = "product://" + aggregated.getMaterialNumber();
        List<PartialUpdateOperation> ops = new ArrayList<>();

        // Reemplaza estos nombres por los campos Coveo reales de tu mapeo.
        addDictionaryOps(ops, documentId, "ec_price_dict", aggregated.getAddedprices(), aggregated.getRemovedprices());
        addDictionaryOps(ops, documentId, "ec_contract_dict", aggregated.getAddedcontracts(), aggregated.getRemovedcontracts());
        addDictionaryOps(ops, documentId, "ec_stock_status_dict", aggregated.getAddedstockstatuses(), aggregated.getRemovedstockstatuses());
        addDictionaryOps(ops, documentId, "ec_contract_type_dict", aggregated.getAddedContractTypes(), aggregated.getRemovedContractTypes());
        addDictionaryOps(ops, documentId, "ec_contract_rank_dict", aggregated.getAddedContractRanks(), aggregated.getRemovedContractRanks());

        if (aggregated.getOffsetId() != null) {
            ops.add(new PartialUpdateOperation(documentId, FIELD_VALUE_REPLACE, "pricingoffsetid",
                    aggregated.getOffsetId().toString()));
        }

        return ops;
    }

    private static void addDictionaryOps(List<PartialUpdateOperation> ops, String documentId, String field,
                                           Map<?, ?> added, List<?> removed) {
        if (added != null && !added.isEmpty()) {
            ops.add(new PartialUpdateOperation(documentId, DICTIONARY_PUT, field, added)); // misma referencia
        }
        if (removed != null && !removed.isEmpty()) {
            ops.add(new PartialUpdateOperation(documentId, DICTIONARY_REMOVE, field, removed)); // misma referencia
        }
    }
}
```

Nótese: `added`/`removed` se pasan **por referencia** como `value` — no hay
`new HashMap<>(added)` ni `new ArrayList<>(removed)`. Cualquier serializador JSON que
trabaje por interfaz (`Map`/`List`, como Jackson) los serializa igual sin necesitar una
copia a un tipo "estándar".

---

## 5. Recomendaciones adicionales de memoria/GC para el payload final

- **Agrupar varios materiales por request HTTP a Coveo.** El array `partialUpdate` de
  nivel superior puede contener operaciones de múltiples `documentId` en un solo
  request — evita 1 llamada HTTP por material (menos objetos de request/response, menos
  overhead de red, mejor throughput). Si la app que llama al Push API ya hace esto,
  no hay nada que cambiar; si llama 1 vez por material, es la optimización de mayor
  impacto de este lado.
- **Revisar el límite de tamaño de batch del Push API de Coveo** antes de agrandar el
  agrupamiento — no tengo ese límite confirmado para tu cuenta/versión, revísalo en la
  documentación de Coveo antes de ajustar `compressionMinutes`/tamaño de batch pensando
  solo en memoria local.
- **No generar operaciones vacías** (sección 3, punto 4) — además de ahorrar payload,
  evita asignar objetos `PartialUpdateOperation` que no aportan nada.

---

## 6. Comparación final (actualizada)

| | 5 mapas + 5 listas con `HashMap`/`ArrayList` (original) | Lista consolidada por cliente (sección 4 del doc. anterior) | 5 mapas + 5 listas con fastutil + mapeo zero-copy (esta propuesta) |
|---|---|---|---|
| Alineado con el contrato Coveo (`dictionaryPut`/`dictionaryRemove`) | Sí, directo | No — requiere explotar de nuevo antes de llamar a Coveo | Sí, directo |
| Overhead de hashing | Alto (`HashMap` con nodos encadenados) | Ninguno durante la agregación, pero se repite al explotar | Bajo (open-addressing) |
| Trabajo duplicado | No | Sí (construir records + luego reconstruir mapas/listas) | No |
| Boxing en mapas `Integer` | Sí (`HashMap<CharSequence,Integer>`) | Se evita en la lista, pero reaparece al explotar | Reducido (`Object2IntOpenHashMap`) |
| Cambio de `.avsc` requerido | No | Sí (requiere coordinar con Coveo/consumidor) | No |
| Beneficio viaja a la app consumidora | — | — | Solo si esa app aplica la misma optimización en su propio deserializador |

**Recomendación final:** usar la estructura original (`Map`/`List` por campo), pero con
colecciones *open-addressing* en vez de `HashMap`/`ArrayList`, y construir las
operaciones de Coveo pasando esas mismas colecciones por referencia. Descarta la
consolidación en una lista por cliente para esta parte del pipeline — era una buena
idea en abstracto, pero el contrato real de Coveo la vuelve contraproducente.

---

## 7. Confirmación: existe un servicio separado que envía a Coveo

Dato adicional que refuerza (no cambia) la recomendación: **otro servicio, no este, es
el que llama al Push API de Coveo.** Esto resuelve la ambigüedad que dejé abierta en la
sección 5 ("sea esta misma u otra aplicación") — y hace la elección entre Array vs.
Map/List todavía más clara, porque ahora el costo de "explotar" una estructura
consolidada en los 5 mapas/listas que Coveo exige **recaería en ese otro servicio**, no
en esta.

Comparando el trabajo total en todo el pipeline (ambos servicios), para un material con
N clientes:

| | `List<CustomerPricingUpdate>` (array consolidado) | `Map`/`List` por campo (esta propuesta) |
|---|---|---|
| Trabajo en esta app | Construir N records — barato, sin hashing | Construir hasta 5 mapas/listas por material — con fastutil, barato |
| Trabajo en el otro servicio | **Debe reconstruir** 5 `HashMap`/`ArrayList` desde los N records antes de llamar a Coveo (hashing que reaparece, ahora en *su* heap) | Ninguno — ya recibe los mapas/listas listos, solo los envuelve como `value` de la operación |
| Asignaciones totales en el pipeline | N records + hasta 5N entradas reconstruidas ≈ hasta 6N | Hasta 5N, construidas una sola vez |
| Dónde pega la presión de GC | En **ambos** servicios | Solo en el productor (donde ya era necesaria de todas formas) |

**El Array no evita el trabajo de armar los 5 diccionarios/listas — solo lo retrasa y
lo duplica**, moviéndolo al servicio que precisamente también te importa que rinda
bien. Con `Map`/`List` por campo, ese trabajo se hace una sola vez, en el productor, y
el servicio de envío a Coveo lo recibe listo para usar sin reconstruir nada.

La única razón válida para preferir el Array sería que el servicio de envío a Coveo
necesitara la vista por cliente para algo distinto de construir el payload (validación,
deduplicación, auditoría por cliente) — si su único trabajo es enviar a Coveo, el
Array le agrega trabajo, no se lo quita.
