package com.labs.repartitioner.config;

import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.Options;
import org.rocksdb.Statistics;
import org.rocksdb.TickerType;
import org.rocksdb.BloomFilter;
import org.rocksdb.LRUCache;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MetricsRocksDbConfigSetter is a custom implementation of Kafka's RocksDBConfigSetter.
 * It configures RocksDB state stores for Kafka Streams, enabling advanced tuning and
 * metrics collection via the RocksDB Statistics object. This class is responsible for
 * applying all RocksDB-related configuration properties, including compaction, buffer,
 * parallelism, and bloom filter settings, as well as wiring up a shared Statistics instance
 * for metrics collection.
 *
 * This class is referenced in KafkaStreamsConfig as the ROCKSDB_CONFIG_SETTER_CLASS_CONFIG.
 *
 * All configuration values are extracted from the provided configs map, which is populated
 * from Spring application properties. Logging is provided for all applied settings.
 */

public class MetricsRocksDbConfigSetter implements RocksDBConfigSetter {

    /**
     * Shared RocksDB Statistics instance for collecting metrics across all state stores.
     * This is set on every Options object configured by this setter.
     */
    private static final Statistics rocksDbStats = new Statistics();

    /**
     * Log format for RocksDB metrics.
     */
    private static final String ROCKSDB_METRIC_LOG_FORMAT = "RocksDB Metric [{}]: rocksdb_metric={}, value={}";

    /**
     * Flag to ensure the scheduled executor is only initialized once.
     */
    private static volatile boolean executorInitialized = false;

    /**
     * Configures the RocksDB Options for a given state store, applying all tuning and metrics settings.
     *
     * @param storeName the name of the state store being configured
     * @param options the RocksDB Options object to configure
     * @param configs the configuration map (from Spring properties)
     */
    @Override
    public void setConfig(String storeName, Options options, Map<String, Object> configs) {
        options.setStatistics(rocksDbStats);

        // Extract all config values from the configs map
        ConfigValues configValues = extractConfigValues(configs);

        // Apply general RocksDB options
        setOptions(options, configValues);
        // Apply bloom filter and block cache options
        setBloomFilter(options, configValues);

        // Log all applied configuration values for this store
        log.info("RocksDB Config for store {}: levelCompactionDynamicLevelBytes={}, maxBytesForLevelBase={}, targetFileSizeBase={}, maxBackgroundJobs={}, useAvailableProcessors={}, writeBufferSize={}, maxWriteBufferNumber={}, enableBloomFilter={}, bloomBitsPerKey={}, bloomIgnoredUseBaseBlockMode={}, bloomBlockSize={}, bloomBlockCacheSize={}",
                storeName, configValues.levelCompactionDynamicLevelBytes, configValues.maxBytesForLevelBase, configValues.targetFileSizeBase, configValues.maxBackgroundJobs,
                configValues.useAvailableProcessors, configValues.writeBufferSize, configValues.maxWriteBufferNumber, configValues.enableBloomFilter, configValues.bloomBitsPerKey,
                configValues.bloomIgnoredUseBaseBlockMode, configValues.bloomBlockSize, configValues.bloomBlockCacheSize);

        // Ensure the executor is only initialized once
        if (!executorInitialized) {
            synchronized (MetricsRocksDbConfigSetter.class) {
                if (!executorInitialized) {
                    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                        if (rocksDbStats == null) {
                            log.warn("RocksDB statistics not initialized yet.");
                            return;
                        }
                        try {
                            // Log each metric independently with a description
                            long blockCacheHit = rocksDbStats.getTickerCount(TickerType.BLOCK_CACHE_HIT);
                            log.info(ROCKSDB_METRIC_LOG_FORMAT, "Block cache hits (number of times a block was found in cache)", "BLOCK_CACHE_HIT", blockCacheHit);

                            long blockCacheMiss = rocksDbStats.getTickerCount(TickerType.BLOCK_CACHE_MISS);
                            log.info(ROCKSDB_METRIC_LOG_FORMAT, "Block cache misses (number of times a block was not found in cache)", "BLOCK_CACHE_MISS", blockCacheMiss);

                            double cacheHitRatio = blockCacheHit + blockCacheMiss > 0 ? (blockCacheHit * 100.0) / (blockCacheHit + blockCacheMiss) : 0.0;
                            log.info(ROCKSDB_METRIC_LOG_FORMAT, "Block cache hit ratio (%)", "BLOCK_CACHE_HIT_RATIO", cacheHitRatio);

                            long bloomUseful = rocksDbStats.getTickerCount(TickerType.BLOOM_FILTER_USEFUL);
                            log.info(ROCKSDB_METRIC_LOG_FORMAT, "Bloom filter useful (number of times bloom filter avoided a disk read)", "BLOOM_FILTER_USEFUL", bloomUseful);

                            long memtableHit = rocksDbStats.getTickerCount(TickerType.MEMTABLE_HIT);
                            log.info(ROCKSDB_METRIC_LOG_FORMAT, "Memtable hit (number of times a key was found in memtable)", "MEMTABLE_HIT", memtableHit);

                            long memtableMiss = rocksDbStats.getTickerCount(TickerType.MEMTABLE_MISS);
                            log.info(ROCKSDB_METRIC_LOG_FORMAT, "Memtable miss (number of times a key was not found in memtable)", "MEMTABLE_MISS", memtableMiss);

                        } catch (Exception e) {
                            log.error("Error logging RocksDB metrics", e);
                        }
                    }, 1, 1, TimeUnit.MINUTES);
                    executorInitialized = true;
                }
            }
        }
    }

    /**
     * Applies general RocksDB tuning options to the Options object.
     *
     * @param options the RocksDB Options object
     * @param configValues the extracted configuration values
     */
    private void setOptions(Options options, ConfigValues configValues) {
        if (configValues.levelCompactionDynamicLevelBytes != null)
            options.setLevelCompactionDynamicLevelBytes(configValues.levelCompactionDynamicLevelBytes);
        if (configValues.maxBytesForLevelBase != null)
            options.setMaxBytesForLevelBase(configValues.maxBytesForLevelBase);
        if (configValues.targetFileSizeBase != null)
            options.setTargetFileSizeBase(configValues.targetFileSizeBase);
        if (configValues.maxBackgroundJobs != null)
            options.setMaxBackgroundJobs(configValues.maxBackgroundJobs);
        if (configValues.useAvailableProcessors != null && configValues.useAvailableProcessors)
            options.setIncreaseParallelism(Runtime.getRuntime().availableProcessors());
        if (configValues.writeBufferSize != null)
            options.setWriteBufferSize(configValues.writeBufferSize);
        if (configValues.maxWriteBufferNumber != null)
            options.setMaxWriteBufferNumber(configValues.maxWriteBufferNumber);
    }

    /**
     * Applies bloom filter and block cache settings to the RocksDB table config, if enabled.
     *
     * @param options the RocksDB Options object
     * @param configValues the extracted configuration values
     */
    private void setBloomFilter(Options options, ConfigValues configValues) {
        BlockBasedTableConfig tableConfig = (BlockBasedTableConfig) options.tableFormatConfig();
        if (tableConfig != null && configValues.bloomBlockCacheSize != null) {
            tableConfig.setBlockCache(new LRUCache(configValues.bloomBlockCacheSize));
            log.info("Set block cache size to {}", configValues.bloomBlockCacheSize);
            tableConfig.setPinL0FilterAndIndexBlocksInCache(true);
            log.info("Set pin L0 filter and index blocks in cache to true");
        }
        if (configValues.enableBloomFilter != null && configValues.enableBloomFilter) {
            if (tableConfig != null) {
                if (configValues.bloomBitsPerKey != null && configValues.bloomIgnoredUseBaseBlockMode != null) {
                    tableConfig.setFilterPolicy(new BloomFilter(configValues.bloomBitsPerKey, configValues.bloomIgnoredUseBaseBlockMode));
                    log.info("Set bloom filter bits per key to {}", configValues.bloomBitsPerKey);
                    log.info("Set bloom filter ignored use base block mode to {}", configValues.bloomIgnoredUseBaseBlockMode);
                }
                // Do NOT call options.setTableFormatConfig(tableConfig); (Kafka Streams manages this)
            } else {
                log.warn("BlockBasedTableConfig is null for store {}. Bloom filter and block cache settings will not be applied.", options);
            }
        }
    }

    /**
     * Extracts all relevant RocksDB configuration values from the configs map.
     *
     * @param configs the configuration map
     * @return a ConfigValues object with all extracted settings
     */
    private ConfigValues extractConfigValues(Map<String, Object> configs) {
        ConfigValues values = new ConfigValues();
        values.levelCompactionDynamicLevelBytes = getConfigBoolean(configs, "spring.kafka.streams.rocksdbproperties.level-compaction-dynamic-level-bytes");
        values.maxBytesForLevelBase = getConfigLong(configs, "spring.kafka.streams.rocksdbproperties.max-bytes-for-level-base");
        values.targetFileSizeBase = getConfigLong(configs, "spring.kafka.streams.rocksdbproperties.target-file-size-base");
        values.maxBackgroundJobs = getConfigInt(configs, "spring.kafka.streams.rocksdbproperties.max-background-jobs");
        values.useAvailableProcessors = getConfigBoolean(configs, "spring.kafka.streams.rocksdbproperties.use-available-processors");
        values.writeBufferSize = getConfigLong(configs, "spring.kafka.streams.rocksdbproperties.write-buffer-size");
        values.maxWriteBufferNumber = getConfigInt(configs, "spring.kafka.streams.rocksdbproperties.max-write-buffer-number");
        values.enableBloomFilter = getConfigBoolean(configs, "spring.kafka.streams.rocksdbproperties.enable-bloom-filter");
        values.bloomBitsPerKey = getConfigInt(configs, "spring.kafka.streams.rocksdbproperties.bloom-bits-per-key");
        values.bloomIgnoredUseBaseBlockMode = getConfigBoolean(configs, "spring.kafka.streams.rocksdbproperties.bloom-ignored-use-base-block-mode");
        values.bloomBlockSize = getConfigInt(configs, "spring.kafka.streams.rocksdbproperties.bloom-block-size");
        values.bloomBlockCacheSize = getConfigLong(configs, "spring.kafka.streams.rocksdbproperties.bloom-block-cache-size");
        return values;
    }

    // --- Helper methods for extracting typed config values from the configs map ---

    private Boolean getConfigBoolean(Map<String, Object> configs, String key) {
        return configs.containsKey(key) ? getBooleanConfig(configs, key) : null;
    }

    private Long getConfigLong(Map<String, Object> configs, String key) {
        return configs.containsKey(key) ? getLongConfig(configs, key) : null;
    }

    private Integer getConfigInt(Map<String, Object> configs, String key) {
        return configs.containsKey(key) ? getIntConfig(configs, key) : null;
    }

    /**
     * Container for all extracted RocksDB configuration values.
     */
    private static class ConfigValues {
        Boolean levelCompactionDynamicLevelBytes;
        Long maxBytesForLevelBase;
        Long targetFileSizeBase;
        Integer maxBackgroundJobs;
        Boolean useAvailableProcessors;
        Long writeBufferSize;
        Integer maxWriteBufferNumber;
        Boolean enableBloomFilter;
        Integer bloomBitsPerKey;
        Boolean bloomIgnoredUseBaseBlockMode;
        Integer bloomBlockSize;
        Long bloomBlockCacheSize;
    }

    /**
     * No-op close method (required by interface, not used).
     */
    @Override
    public void close(String arg0, Options arg1) {
        // No resources to close
    }

    // --- Type-safe config extraction helpers ---

    private boolean getBooleanConfig(Map<String, Object> configs, String key) {
        Object value = configs.get(key);
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private long getLongConfig(Map<String, Object> configs, String key) {
        Object value = configs.get(key);
        try {
            return value != null ? Long.parseLong(value.toString()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int getIntConfig(Map<String, Object> configs, String key) {
        Object value = configs.get(key);
        try {
            return value != null ? Integer.parseInt(value.toString()) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

--
management:
  endpoints:
    web:
      exposure:
        include: "health,info" # ,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    db:
      enabled: true
    diskspace:
      enabled: false
  info:
    env:
      enabled: true
    git:
      enabled: true
    build:
      enabled: true
  metrics:
    enable:
      all: false
      jvm.memory: true # Memory: Essential for managing the Punctuator's temporary buffers before flushing the batches
      jvm.gc: true # Garbage Collector: Massive processing and constant batch creation generate a lot of garbage in Eden. Monitoring pauses prevents desynchronization in Kafka.
      jvm.threads: true # Threads: Essential for monitoring the number of threads used by the Punctuator and the Kafka Streams application.
      process.cpu: true # Allows you to scale and detect pod saturation in GKE under high demand
      system.cpu: true # Allows you to scale and detect pod saturation in GKE under high demand
      kafka.stream: false # Essentials for Measuring the Performance of Your Streams
      kafka.producer: false # and the efficiency of sending the batches generated by the Punctuator.
      kafka.consumer: false # Essential for measuring the performance of your streams and the efficiency of sending the batches generated by the Punctuator.
  dynatrace:
    metrics:
      export:
        enabled: true
        uri: http://localhost:14499/metrics/ingest
        step: 15m
        v2:
          enrich-with-dynatrace-metadata: true
          metric-key-prefix: "ecomm.nxtgen.kafka.coveo.streams.indexer.prf"

--

package com.cardinalhealth.coveo.streams.indexer.kstreams.traceability;

import com.cardinalhealth.coveo.streams.indexer.config.MeterRegistryInitializer;
import com.cardinalhealth.coveo.streams.indexer.config.app.StreamProcessorProperties;
import com.cardinalhealth.coveo.streams.indexer.utils.constants.OperationCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class StreamProcessorTrace {
    private static final String UNKNOWN_TASK = "n/a";

    private final StreamProcessorProperties streamProcessorProperties;
    private final String processorName;
    private final Long maxFailedRecordsLogged;
    private final Long maxBatchFailedLogged;
    private String taskId;
    private final AtomicLong upsertRecordCounter = new AtomicLong(0L);
    private final AtomicLong deleteRecordCounter = new AtomicLong(0L);
    private final AtomicLong failedRecordCounter = new AtomicLong(0L);
    private final AtomicLong processedBatches = new AtomicLong(0L);
    private final AtomicLong droppedRecordsCounter = new AtomicLong(0L);
    private final HashSet<String> failedRecordsSet = new HashSet<>();
    private final HashSet<String> processedBatchSet = new HashSet<>();

    public StreamProcessorTrace(StreamProcessorProperties streamProcessorProperties) {
        this.streamProcessorProperties = streamProcessorProperties;
        this.processorName = streamProcessorProperties.getKey();
        this.maxFailedRecordsLogged = Optional.ofNullable(streamProcessorProperties.getMaxFailedRecordsLogged()).orElse(0L);
        this.maxBatchFailedLogged = Optional.ofNullable(streamProcessorProperties.getMaxBatchFailedLogged()).orElse(0L);
    }

    public void setTaskId(String taskId) {
        this.taskId = Optional.ofNullable(taskId).orElse(UNKNOWN_TASK);
    }

    public void traceUpsert(String recordRef) {
        upsertRecordCounter.incrementAndGet();
        log.debug("'{}' inserted/updated record: {}.", processorName, recordRef);
    }

    public void traceDelete(String recordRef) {
        deleteRecordCounter.incrementAndGet();
        log.debug("'{}' deleted record: {}.", processorName, recordRef);
    }

    public void traceFailure(String recordRef) {
        failedRecordCounter.incrementAndGet();
        if ((long)failedRecordsSet.size() < maxFailedRecordsLogged) {
            failedRecordsSet.add(recordRef);
        }

        log.debug("'{}' failed to processed record: {}.", processorName, recordRef);
    }

    public void traceDropped(String recordRef) {
        droppedRecordsCounter.incrementAndGet();
        log.debug("'{}' dropped record: {}", processorName, recordRef);
    }

    public void traceProcessedOperation(String operationCode, String recordRef) {
        switch (OperationCode.fromCode(operationCode)) {
            case INSERT:
            case UPDATE:
                traceUpsert(recordRef);
                break;
            case DELETE:
                traceDelete(recordRef);
        }

    }

    public void traceProcessedBatch(String batchRef) {
        processedBatches.incrementAndGet();
        if ((long)processedBatchSet.size() < maxBatchFailedLogged) {
            processedBatchSet.add(batchRef);
        }

        log.debug("'{}' processed batch with material number {}.", processorName, batchRef);
    }

    public void logBeforeStartingToProcessBatches() {
        var upsertOperations = upsertRecordCounter.getAndSet(0L);
        var deleteOperations = deleteRecordCounter.getAndSet(0L);
        var droppedRecords = droppedRecordsCounter.getAndSet(0L);
        var failedRecords = failedRecordCounter.getAndSet(0L);
        var totalProcessedRecords = upsertOperations + deleteOperations;
        var totalReceivedMessages = totalProcessedRecords + droppedRecords + failedRecords;
        var unprocessedRecords = totalReceivedMessages - totalProcessedRecords;
        var reference = " Log before starting to process batches.";
        var failedDetails = failedRecords == 0L ? "" :
                "Samples of failed records: [%s]".formatted(getFormatedValues(failedRecordsSet, maxFailedRecordsLogged));

        processedBatches.set(0L);

        log.info("""
            {}
            Summary of '{}' [task {}] processor. 
            Total acumulated received messages: {}, 
            Total sucessfuly processed messages: {},
            Total unprocessed messages: {}
            Total upsert operations: {}, 
            Total delete operations: {}, 
            Total dropped records: {}, 
            Total failed process records: {}, 
            {}""",
                reference, processorName, taskId, totalReceivedMessages, totalProcessedRecords, unprocessedRecords,
                upsertOperations, deleteOperations, droppedRecords, failedRecords, failedDetails);

        logSystemMetrics(reference);
    }

    public void logAfterEndingToProcessBatches() {
        var processedBatchesCount = processedBatches.getAndSet(0L);
        var sampleBatchedMaterials = getFormatedValues(processedBatchSet, maxBatchFailedLogged);
        var reference = " Log after ending to process batches.";

        log.info("""
            {}
            Summary of '{}' [task {}] aggregated batches.
            Total processed batches: {},
            Sample of aggregated material numbers: [{}]"""
                , reference, processorName, taskId, processedBatchesCount, sampleBatchedMaterials);

        logSystemMetrics(reference);
    }

    public void logFailedBatch(Set<String> keys, Exception e) {
        String sampleFailedMaterials = getFormatedValues(keys, maxBatchFailedLogged);
        log.error("'{}' [task {}] Aggregation failed trying to create the batch with material numbers: [{}].", processorName, taskId, sampleFailedMaterials, e);
    }


    private void logSystemMetrics(String reference) {
        var meterRegistry = MeterRegistryInitializer.meterRegistry;
        if (meterRegistry == null) {
            log.warn("'{}' [task {}] MeterRegistry not bound yet, skipping system metrics.", processorName, taskId);
            return;
        }

        // process.cpu.usage: share of CPU consumed by this JVM (0-1). A value sustained near 1
        // while lag grows points at CPU-bound work (serialization, punctuator batching logic)
        // rather than network/broker backpressure.
        double processCpuUsage = gaugeValue(meterRegistry, "process.cpu.usage");

        // system.cpu.usage: CPU usage of the whole pod/host (0-1). Comparing it with
        // process.cpu.usage shows whether this stream thread is competing for CPU with
        // other containers on the same GKE node.
        double systemCpuUsage = gaugeValue(meterRegistry,"system.cpu.usage");

        // jvm.memory.used/max (heap): heap footprint. The punctuator holds records in memory
        // until a batch is flushed, so heap creeping toward max is an early signal that the
        // batch/commit interval is not draining fast enough for the incoming volume.
        double heapUsedBytes = gaugeValue(meterRegistry,"jvm.memory.used", "area", "heap");
        double heapMaxBytes = gaugeValue(meterRegistry,"jvm.memory.max", "area", "heap");
        double heapUsedPct = heapMaxBytes > 0 ? (heapUsedBytes / heapMaxBytes) * 100 : 0;

        // jvm.gc.pause: time the JVM spends stopped for garbage collection. Kafka Streams
        // punctuators fire on wall-clock/stream-time schedules, so long GC pauses delay
        // batch flushes and can make throughput look worse than the topology logic actually is.
        var gcPauseTimer = meterRegistry.find("jvm.gc.pause").timer();
        long gcPauseCount = gcPauseTimer != null ? gcPauseTimer.count() : 0L;
        double gcPauseMaxMs = gcPauseTimer != null ? gcPauseTimer.max(TimeUnit.MILLISECONDS) : 0d;

        // Disk usable space on the working directory: Kafka Streams persists its RocksDB
        // state (including any state stores backing the punctuator's batches) under state.dir.
        // Running low on disk stalls state store writes and can crash the stream thread.
        File workingDir = new File(".");
        double diskUsableGb = workingDir.getUsableSpace() / (1024.0 * 1024 * 1024);
        double diskTotalGb = workingDir.getTotalSpace() / (1024.0 * 1024 * 1024);

        log.info("""
            {}.
            System diagnostics for '{}' [task {}].
            Process CPU usage: {}, System CPU usage: {},
            Heap used: {} MB / {} MB ({}%),
            GC pauses observed: {} (max {} ms),
            Disk usable: {} GB / {} GB""",
                reference, processorName, taskId,
                round(processCpuUsage), round(systemCpuUsage),
                round(heapUsedBytes / (1024 * 1024)), round(heapMaxBytes / (1024 * 1024)), round(heapUsedPct),
                gcPauseCount, round(gcPauseMaxMs),
                round(diskUsableGb), round(diskTotalGb));
    }


    private double gaugeValue(MeterRegistry meterRegistry, String name, String... tags) {
        var gauge = meterRegistry.find(name).tags(tags).gauge();
        return gauge != null ? gauge.value() : Double.NaN;
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private String getFormatedValues(Set<String> keys, Long maxRecordsLogged) {
        var suffix = keys.size() > maxRecordsLogged ? ", ... (" + (keys.size() - maxRecordsLogged) + " more)]" : "]";
        return keys.stream()
                .limit(maxRecordsLogged)
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", suffix));
    }

}


