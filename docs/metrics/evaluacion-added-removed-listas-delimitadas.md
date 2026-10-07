# Evaluación: listas de strings delimitados (`added`/`removed`) vs. lista de records tipados

Este documento evalúa la propuesta de reducir `AggregatedPricingRecordPartialUpdate` a:

```java
{
  String materialNumber;
  List<String> added;   // "kunnr|price|contract|contractType|stock|..."
  List<String> removed;
}
```

donde cada entrada de `added` es un string con los campos concatenados por un
delimitador (`|`), y evalúa también qué tan costoso sería reconstruir el objeto en la
aplicación que consume estos batches. Es continuación de
[`optimizacion-estructuras-aggregated-pricing.md`](./optimizacion-estructuras-aggregated-pricing.md).

---

## 1. Lo que la propuesta hace bien

El instinto estructural es correcto: pasar de **10 colecciones** (5 mapas `added*` + 5
listas `removed*`) a **2 colecciones** (`added` + `removed`) es la misma dirección que
ya recomendé en la sección 4 del documento anterior — consolidar por cliente en vez de
por campo. Eso no es lo que hay que corregir.

Lo que sí hay que corregir es **cómo se representa cada entrada**: concatenar los
campos en un string delimitado (`"kunnr|price|contract|contractType|stock|..."`) en
vez de usar un *record* tipado con esos mismos campos.

---

## 2. Por qué el string delimitado no es la opción de mejor rendimiento

### 2.1 Costo de construcción (en esta aplicación)

Para producir `"123456|19.99|CONTRACT123|2|5|0|0"` no basta con tener los valores — hay
que **formatearlos a texto**:

| Campo origen | Tipo real | Conversión necesaria | Costo |
|---|---|---|---|
| `price` | `double`/`BigDecimal` | `Double.toString()` / `BigDecimal.toString()` | Asigna un `String` nuevo; el formateo de un double a decimal (algoritmo tipo Grisu/Ryu en `java.lang.Double`) es notablemente más caro en CPU que escribir 8 bytes binarios. |
| `stockStatusPtCode`, `contractType`, `contractRank` | `int` | `Integer.toString()` | Asigna un `String` nuevo por campo (aunque el valor numérico sea pequeño). |
| `contract` | `String` | ninguna (ya es texto) | Barato, pero igual se concatena. |

Y luego hay que **unir todo con el delimitador** (`StringBuilder`/`String.join`), lo que
asigna aún otro `char[]`/`byte[]` para el resultado final. Es decir: por cada cliente,
en vez de **guardar directamente** los valores que ya tienes en el `StrippedPricingRecord`
(solo copiar referencias/primitivos a un record), **los conviertes a texto, generando
~4 asignaciones adicionales de `String` por entrada** que no existían antes.

Comparado con un record tipado (`CustomerPricingUpdate` con `kunnr`, `price: double`,
`contract: String`, etc. — lo propuesto en la sección 4 del documento anterior), el
tamaño en heap es parecido o incluso un poco menor para el string (~55-70 bytes vs.
~55-65 bytes), **pero el string necesitó varias asignaciones intermedias para llegar
ahí, y el record no necesitó ninguna** (los valores se copian directo del
`StrippedPricingRecord`, sin reformatear).

### 2.2 Costo de lectura/reconstrucción (en cualquier app, incluida esta si necesita
inspeccionar el dato antes de enviarlo)

Para volver a tener los campos tipados hay que:
1. `split("\\|")` (o un parseo manual char por char) → asigna un `String[]` + N
   substrings.
2. `Double.parseDouble(...)`, `Integer.parseInt(...)` por cada campo numérico → parseo
   de texto a número, más costoso que un `get()` directo sobre un campo ya tipado.
3. Manejo de nulos por convención: si un campo puede faltar (`price` ausente), hay que
   dejar el token vacío (`"123456||CONTRACT123|2|5|0|0"`) y que el consumidor interprete
   `token.isEmpty()` como "sin valor" — una convención implícita, no algo que el
   esquema valide.

Esto no es gratis ni una sola vez: **se paga en el productor al formatear, y se vuelve a
pagar completo en el consumidor al parsear** — el doble de trabajo que simplemente
transportar los valores ya tipados en un record Avro (donde decodificar es leer bytes
binarios directo a `double`/`int`, sin texto de por medio).

### 2.3 Riesgo de corrección (más allá del rendimiento)

- **Colisión de delimitador:** si `contract` alguna vez contiene el carácter `|`, el
  `split` genera un número distinto de tokens y el parseo posicional se corrompe
  silenciosamente (no hay excepción, hay datos mal alineados). Un record tipado no
  tiene este riesgo — cada campo es su propio slot.
- **Evolución de esquema:** Avro con registros tipados soporta agregar/quitar campos de
  forma compatible (y lo valida un Schema Registry). Un formato posicional
  "campo1|campo2|campo3" **no tiene ninguna validación de esquema** — agregar un campo
  nuevo requiere coordinar manualmente el orden/posición en ambas aplicaciones, y un
  descuido rompe el parseo sin aviso en tiempo de compilación.
- **Pérdida de tipado:** cualquier bug de formato (una coma en vez de punto decimal,
  un campo vacío mal interpretado) se descubre en producción al fallar el `parse`, no
  en compilación.

**Conclusión de la sección:** el string delimitado no gana en memoria de forma
significativa frente al record tipado, y pierde claramente en CPU (formateo + parseo
duplicado) y en seguridad de tipos/evolución de esquema. No es la estructura de mejor
rendimiento — es una micro-optimización de "menos objetos" que en la práctica cuesta
más de lo que ahorra.

---

## 3. ¿Qué tan costoso sería reconstruir `AggregatedPricingRecordPartialUpdate` en la app consumidora?

Depende de cuál de las dos estructuras le llega:

| | String delimitado (`List<String> added`) | Lista de records tipados (`List<CustomerPricingUpdate>`) |
|---|---|---|
| Decodificación del batch | Avro decodifica el `array<string>` (lectura binaria de bytes UTF-8 por entrada) | Avro decodifica el `array<record>` (lectura binaria de cada campo ya tipado) |
| Para obtener los campos usables | Por cada entrada: `split()` + `parseDouble`/`parseInt` × N campos (CPU de parseo + N asignaciones de `String`/autoboxing) | Por cada entrada: `getPrice()`, `getContract()`, etc. — lectura directa, sin parseo |
| Costo por entrada | O(F) operaciones de parseo + alocación (F = número de campos) | O(1) — ya están en memoria con su tipo |
| Si la app consumidora necesita indexar por `kunnr` | Igual en ambos casos: recorrer una vez y construir su propio índice (`Map`) — mismo costo, no depende del formato de origen | Igual |
| Riesgo de error de parseo | Sí (delimitador, nulos por convención) | No — el esquema lo garantiza |

**Respuesta directa:** reconstruir desde el string delimitado es estrictamente más caro
en CPU que desde la lista de records — se paga un parseo completo (split + conversión
de tipos) por cada campo de cada cliente, en cada batch, en la aplicación consumidora.
Con records tipados, "reconstruir" es simplemente leer campos ya decodificados por
Avro; el único trabajo real que le queda al consumidor es el que *tendría que hacer de
todas formas* si necesita indexar los datos (recorrer una vez), no un parseo adicional
de texto.

---

## 4. Estructura recomendada

Mantener la consolidación a **2 colecciones por material** (buen instinto de la
propuesta), pero con records tipados en vez de strings:

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

```json
{"name": "materialNumber", "type": "string"},
{"name": "customerUpdates", "type": {"type": "array", "items": "customerPricingUpdate"}, "default": []}
```

Esto es exactamente lo propuesto en la sección 4 de
`optimizacion-estructuras-aggregated-pricing.md`: una sola lista (`array`), sin
`Map`/hashing (porque el patrón de acceso en `aggregate()` nunca necesita buscar por
clave — ver esa sección para el detalle), y sin texto delimitado — cada campo mantiene
su tipo nativo tanto en memoria como en el wire format.

### Si de verdad hace falta ir más allá (solo si el profiling lo justifica)

Para materiales con decenas de miles de clientes, el siguiente nivel de optimización —
**no recomendado como primer paso** por la complejidad que agrega — sería pasar de
"arreglo de records" (*array of structs*) a "arreglos paralelos" (*struct of arrays*):
`List<String> kunnrs`, `double[] prices`, `List<String> contracts`, `int[]
stockStatuses`, etc., todos indexados por posición. Esto elimina el overhead del header
de objeto Java (~16 bytes) por cada `CustomerPricingUpdate`, a cambio de que el código
sea más frágil (8 colecciones que deben mantenerse sincronizadas por índice) y de
reintroducir parcialmente la fragmentación "columnar" que ya se identificó como el
problema original en la sección 1 del documento anterior. **Solo vale la pena si, tras
medir con `jvm.memory`/`jvm.gc` después de aplicar la opción de la sección 4, el
overhead de objeto por cliente sigue siendo el cuello de botella dominante** — no antes.

---

## 5. Comparación final

| | 5 mapas + 5 listas (actual) | `List<String>` delimitado (propuesta) | `List<CustomerPricingUpdate>` (recomendada) |
|---|---|---|---|
| Nodos/overhead de hashing | Alto (5× `HashMap`) | Ninguno | Ninguno |
| Costo de formateo/parseo | Ninguno (valores ya tipados) | Alto (toString + split + parse, en ambos extremos) | Ninguno |
| Seguridad de tipos | Sí | No (posicional, sin validación) | Sí |
| Evolución de esquema | Sí (Avro) | No (formato ad-hoc) | Sí (Avro) |
| Riesgo de colisión de delimitador | N/A | Sí | N/A |
| Tamaño en heap por cliente | Alto (~5 nodos de `HashMap`) | Medio-bajo | Medio-bajo |
| Costo en la app consumidora | — | Alto (reparseo completo) | Bajo (lectura directa) |

**Recomendación:** no usar strings delimitados. La estructura de mejor rendimiento
real — en esta aplicación y en la que consume los batches — es la lista de records
tipados (`List<CustomerPricingUpdate>`) de la sección 4, no por ahorrar bytes en heap
(ahí la diferencia con el string es modesta), sino porque **evita el costo de
formatear y volver a parsear cada campo en cada batch, en ambos lados de la
integración**, y conserva la seguridad de tipos y la evolución de esquema que ya les da
Avro.

---

## 6. Clases Java: el contenedor de la agregación por material number

Dos aclaraciones antes del código:

- `CustomerPricingUpdate` y `AggregatedPricingRecordPartialUpdate` **no se escriben a
  mano** — las genera `avro-maven-plugin` a partir del `.avsc` de la sección 4, en
  `target/generated-sources/avro` al compilar. Lo que sigue es la forma/API que
  tendrían esas clases generadas, para que quede claro qué se usa en
  `AggregatedPricing`.
- `RemovedField` sí es una clase que hay que escribir a mano — Avro solo genera el
  campo `int removedFieldsMask`; la semántica de los bits es responsabilidad del código
  de la aplicación.
- Los tipos exactos (`Double` para `price`, por ejemplo) asumen que coinciden con los
  del `.avsc` de `StrippedPricingRecord`. Si en ese esquema `price` es `BigDecimal`/
  `bytes decimal`, ajusta el tipo tanto en el `.avsc` nuevo como en el `Builder` de
  abajo — no tengo ese esquema para confirmarlo.

### 6.1 `CustomerPricingUpdate` (generada por Avro — forma resultante)

```java
package com.cardinalhealth.coveo.streams.indexer.avro;

public class CustomerPricingUpdate extends org.apache.avro.specific.SpecificRecordBase
        implements org.apache.avro.specific.SpecificRecord {

    private java.lang.CharSequence kunnr;
    private java.lang.Double price;
    private java.lang.CharSequence contract;
    private java.lang.Integer stockStatusPtCode;
    private java.lang.Integer contractType;
    private java.lang.Integer contractRank;
    private boolean removed;
    private int removedFieldsMask;

    public static Builder newBuilder() { /* generado */ return new Builder(); }

    public CharSequence getKunnr() { return kunnr; }
    public void setKunnr(CharSequence kunnr) { this.kunnr = kunnr; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public CharSequence getContract() { return contract; }
    public void setContract(CharSequence contract) { this.contract = contract; }
    public Integer getStockStatusPtCode() { return stockStatusPtCode; }
    public void setStockStatusPtCode(Integer stockStatusPtCode) { this.stockStatusPtCode = stockStatusPtCode; }
    public Integer getContractType() { return contractType; }
    public void setContractType(Integer contractType) { this.contractType = contractType; }
    public Integer getContractRank() { return contractRank; }
    public void setContractRank(Integer contractRank) { this.contractRank = contractRank; }
    public boolean getRemoved() { return removed; }
    public void setRemoved(boolean removed) { this.removed = removed; }
    public int getRemovedFieldsMask() { return removedFieldsMask; }
    public void setRemovedFieldsMask(int removedFieldsMask) { this.removedFieldsMask = removedFieldsMask; }

    // SCHEMA$, get()/put() por posición, readExternal/writeExternal: boilerplate
    // generado, se omite — no se edita a mano.

    public static final class Builder
            extends org.apache.avro.specific.SpecificRecordBuilderBase<CustomerPricingUpdate> {
        public Builder setKunnr(CharSequence v) { /* ... */ return this; }
        public Builder setPrice(Double v) { /* ... */ return this; }
        public Builder setContract(CharSequence v) { /* ... */ return this; }
        public Builder setStockStatusPtCode(Integer v) { /* ... */ return this; }
        public Builder setContractType(Integer v) { /* ... */ return this; }
        public Builder setContractRank(Integer v) { /* ... */ return this; }
        public Builder setRemoved(boolean v) { /* ... */ return this; }
        public Builder setRemovedFieldsMask(int v) { /* ... */ return this; }
        public CustomerPricingUpdate build() { /* ... */ return new CustomerPricingUpdate(); }
    }
}
```

### 6.2 `AggregatedPricingRecordPartialUpdate` (campos relevantes, tras el rediseño)

```java
package com.cardinalhealth.coveo.streams.indexer.avro;

public class AggregatedPricingRecordPartialUpdate extends org.apache.avro.specific.SpecificRecordBase
        implements org.apache.avro.specific.SpecificRecord {

    private java.lang.CharSequence materialNumber;
    private java.util.List<CustomerPricingUpdate> customerUpdates;
    private java.lang.CharSequence offsetId;
    private java.lang.Long compressedAdds;
    private java.lang.Long compressedDeletes;
    private java.lang.Long compressedCount;
    private java.lang.Long totalMessageCount;

    public static Builder newBuilder() { /* generado */ return new Builder(); }

    public CharSequence getMaterialNumber() { return materialNumber; }
    public void setMaterialNumber(CharSequence materialNumber) { this.materialNumber = materialNumber; }
    public java.util.List<CustomerPricingUpdate> getCustomerUpdates() { return customerUpdates; }
    public void setCustomerUpdates(java.util.List<CustomerPricingUpdate> customerUpdates) { this.customerUpdates = customerUpdates; }
    // getOffsetId/setOffsetId, getCompressedAdds/setCompressedAdds, etc. — sin cambios

    public static final class Builder
            extends org.apache.avro.specific.SpecificRecordBuilderBase<AggregatedPricingRecordPartialUpdate> {
        public Builder setMaterialNumber(CharSequence v) { /* ... */ return this; }
        public Builder setCustomerUpdates(java.util.List<CustomerPricingUpdate> v) { /* ... */ return this; }
        public Builder setCompressedAdds(Long v) { /* ... */ return this; }
        public Builder setCompressedDeletes(Long v) { /* ... */ return this; }
        public Builder setCompressedCount(Long v) { /* ... */ return this; }
        public Builder setTotalMessageCount(Long v) { /* ... */ return this; }
        public AggregatedPricingRecordPartialUpdate build() { /* ... */ return new AggregatedPricingRecordPartialUpdate(); }
    }
}
```

### 6.3 `RemovedField` (hand-written — bits del `removedFieldsMask`)

```java
package com.cardinalhealth.coveo.streams.indexer.config.kstreams.transformer;

public final class RemovedField {

    public static final int PRICE         = 1 << 0;
    public static final int CONTRACT      = 1 << 1;
    public static final int STOCK_STATUS  = 1 << 2;
    public static final int CONTRACT_TYPE = 1 << 3;
    public static final int CONTRACT_RANK = 1 << 4;

    private RemovedField() {
    }

    public static int set(int mask, int field) {
        return mask | field;
    }

    public static boolean isSet(int mask, int field) {
        return (mask & field) != 0;
    }
}
```

### 6.4 `AggregatedPricing` reescrita contra el nuevo contenedor

Reemplaza el `createBaseAggregation` + los 5 `apply*` actuales por construir **un solo
`CustomerPricingUpdate` por cliente** y agregarlo a la lista:

```java
package com.cardinalhealth.coveo.streams.indexer.config.kstreams.transformer;

import com.cardinalhealth.coveo.streams.indexer.avro.AggregatedPricingRecordPartialUpdate;
import com.cardinalhealth.coveo.streams.indexer.avro.CustomerPricingUpdate;
import com.cardinalhealth.coveo.streams.indexer.avro.StrippedPricingRecord;
import com.cardinalhealth.coveo.streams.indexer.config.kstreams.topology.PricingStreamTopology;
import com.cardinalhealth.coveo.streams.indexer.utils.constants.OperationCode;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public class AggregatedPricing {

    public static AggregatedPricingRecordPartialUpdate createBaseAggregation(String materialNumber) {
        return AggregatedPricingRecordPartialUpdate.newBuilder()
                .setMaterialNumber(materialNumber)
                .setCustomerUpdates(new ArrayList<>())
                .setCompressedAdds(0L)
                .setCompressedDeletes(0L)
                .setCompressedCount(0L)
                .setTotalMessageCount(0L)
                .build();
    }

    public static void aggregate(StrippedPricingRecord record, AggregatedPricingRecordPartialUpdate aggregated) {
        aggregated.setOffsetId(record.getOffsetId());
        aggregated.setTotalMessageCount(aggregated.getTotalMessageCount() + 1);
        aggregated.setCompressedCount(aggregated.getCompressedCount() + 1);

        boolean isDeletion = OperationCode.DELETE.getCode().equals(record.getOpCode());
        CustomerPricingUpdate update = isDeletion ? buildDeletion(record) : buildAddition(record);

        aggregated.getCustomerUpdates().add(update);

        if (isDeletion) {
            aggregated.setCompressedDeletes(aggregated.getCompressedDeletes() + 1);
        } else {
            aggregated.setCompressedAdds(aggregated.getCompressedAdds() + 1);
        }
    }

    private static CustomerPricingUpdate buildDeletion(StrippedPricingRecord record) {
        return CustomerPricingUpdate.newBuilder()
                .setKunnr(record.getKunnr())
                .setRemoved(true)
                .build();
    }

    private static CustomerPricingUpdate buildAddition(StrippedPricingRecord record) {
        CustomerPricingUpdate.Builder builder = CustomerPricingUpdate.newBuilder()
                .setKunnr(record.getKunnr())
                .setRemoved(false);

        int mask = 0;

        if (record.getPrice() != null) {
            builder.setPrice(record.getPrice());
        } else {
            mask = RemovedField.set(mask, RemovedField.PRICE);
        }

        if (StringUtils.isNotBlank(record.getContract())) {
            builder.setContract(record.getContract());
        } else {
            mask = RemovedField.set(mask, RemovedField.CONTRACT);
        }

        if (record.getStockStatusPtCode() != null && record.getStockStatusPtCode() != -1) {
            builder.setStockStatusPtCode(record.getStockStatusPtCode());
        } else {
            mask = RemovedField.set(mask, RemovedField.STOCK_STATUS);
        }

        if (StringUtils.isNotBlank(record.getPCTYP())) {
            builder.setContractType(resolveContractType(record.getPCTYP()));
        } else {
            mask = RemovedField.set(mask, RemovedField.CONTRACT_TYPE);
        }

        if (StringUtils.isNotBlank(record.getHIERCY()) && record.getHIERCY().trim().equals("10")) {
            builder.setContractRank(10);
        } else {
            mask = RemovedField.set(mask, RemovedField.CONTRACT_RANK);
        }

        return builder.setRemovedFieldsMask(mask).build();
    }

    private static Integer resolveContractType(String pctyp) {
        return PricingStreamTopology.CONTRACT_TYPE_CODE_MAP.getOrDefault(pctyp.toUpperCase(),
                PricingStreamTopology.CONTRACT_TYPE_CODE_MAP.getOrDefault("DEFAULT", 100));
    }
}
```

Diferencia clave frente al original: antes, por cliente, se hacían hasta 5 `put()` en
5 `HashMap` distintos + hasta 5 `add()` en 5 `ArrayList` distintos. Ahora, por cliente,
se construye **un solo objeto** (`CustomerPricingUpdate`) y se hace **un solo**
`add()` sobre `customerUpdates` — sin `Map`, sin hashing, sin duplicar la clave
`kunnr` ni la señal de eliminación.
