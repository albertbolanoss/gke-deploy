# Improvements Implemented in the Performance Environment (Coveo Stream Pricing Indexer)

**1. Per-Message Processing (Store last message by Material and Customer)**
During the aggregation window wait time, the latest received message corresponding to a specific material and customer is stored using a composite key (Material Number + Customer ID). Additionally, the material numbers processed during this period are saved in a separate state store.

**2. Aggregation (On Punctuator)**
Once the aggregation window expires, the state stores are queried using a prefix scan for each processed material, proceeding to send a batch per material. After all materials have been processed, the stores are cleared, and the cycle restarts.

**3. Centralized Logging for Statistics (Before/After Batch and On-Process Messages)**
A centralized, reusable class was created and integrated into the stream topology. It outputs logs before and after processing batches, as well as per-message processing operations (if DEBUG mode is enabled). Furthermore, it logs system metrics such as CPU, memory usage, garbage collection, and disk I/O before and after each batch process.

**4. Actuator Metrics and Dynatrace Dashboards (In Progress)**
Spring Boot Actuator and its metrics were enabled, explicitly configuring which metrics are exposed. These metrics can now be exported to Dynatrace to build customized monitoring dashboards.

**5. Stream Parameterization (Enable/Disable Repartitioner, Compressed Minutes) - In Progress**
Configurations were introduced to control key topology parameters, allowing toggling of the repartitioning step and dynamic adjustment of the aggregation window duration.

**6. Code Optimization**
Code performance was enhanced by eliminating excessive serialization and deserialization overhead. The refactor also improved overall readability, maintainability, testability, and simplified the core logic.