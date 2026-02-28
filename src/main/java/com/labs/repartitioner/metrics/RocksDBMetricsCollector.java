package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.rocksdb.HistogramData;
import org.rocksdb.HistogramType;
import org.rocksdb.Statistics;
import org.rocksdb.TickerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collector that reads RocksDB statistics and registers them as Micrometer metrics.
 * Implements MeterBinder to integrate with Micrometer's metric registry.
 * Uses @Scheduled to periodically collect metrics from RocksDB state stores.
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 9.1, 9.2, 9.3, 9.4, 9.5, 10.1, 10.2, 10.3, 10.4, 10.5, 11.3, 13.2
 */
public class RocksDBMetricsCollector implements MeterBinder {
    
    private static final Logger log = LoggerFactory.getLogger(RocksDBMetricsCollector.class);
    
    private MeterRegistry meterRegistry;
    
    // Cache for gauge values to avoid recreating gauges on each collection
    private final Map<String, AtomicLong> gaugeCache = new ConcurrentHashMap<>();
    
    /**
     * Constructor for RocksDBMetricsCollector.
     * 
     * @param meterRegistry the Micrometer registry to register metrics
     */
    public RocksDBMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Binds initial metrics to the registry.
     * Called by Micrometer when the binder is registered.
     * 
     * @param registry the MeterRegistry to bind metrics to
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        this.meterRegistry = registry;
        log.info("RocksDBMetricsCollector bound to MeterRegistry");
        
        // Initial collection attempt
        try {
            collectMetrics();
        } catch (Exception e) {
            log.warn("Initial metrics collection failed, will retry on schedule", e);
        }
    }
    
    /**
     * Periodically collects metrics from all RocksDB state stores.
     * Scheduled to run every 10 seconds.
     * Implements graceful degradation - continues even if individual stores fail.
     */
    @Scheduled(fixedRate = 10000)
    public void collectMetrics() {
        try {
            Map<String, Statistics> storeStats = CustomRocksDBConfigSetter.getStoreStatistics();
            
            if (storeStats.isEmpty()) {
                log.debug("No RocksDB state stores available for metrics collection");
                return;
            }
            
            log.debug("Collecting metrics from {} RocksDB state stores", storeStats.size());
            
            for (Map.Entry<String, Statistics> entry : storeStats.entrySet()) {
                try {
                    registerStoreMetrics(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    log.warn("Failed to collect metrics for store: {}", entry.getKey(), e);
                    // Continue with other stores
                }
            }
        } catch (Exception e) {
            log.error("Failed to collect RocksDB metrics", e);
            // Application continues without RocksDB metrics
        }
    }
    
    /**
     * Registers metrics for a specific RocksDB state store.
     * Collects memory, storage, operations, latency, and compaction metrics.
     * 
     * @param storeName the name of the state store
     * @param stats the Statistics object for the store
     */
    private void registerStoreMetrics(String storeName, Statistics stats) {
        if (stats == null) {
            log.warn("Statistics object is null for store: {}", storeName);
            return;
        }
        
        try {
            // Memory and Storage Metrics (Requirements 8.1, 8.2, 8.3, 8.4)
            registerGauge("rocksdb.memtable.size.all", storeName, stats, TickerType.MEMTABLE_HIT,
                    "Total size of all memtables");
            registerGauge("rocksdb.block.cache.hit", storeName, stats, TickerType.BLOCK_CACHE_HIT,
                    "Block cache hits");
            registerGauge("rocksdb.block.cache.miss", storeName, stats, TickerType.BLOCK_CACHE_MISS,
                    "Block cache misses");
            
            // Operations Metrics (Requirements 9.1, 9.2, 9.5)
            registerGauge("rocksdb.number.keys.written", storeName, stats, TickerType.NUMBER_KEYS_WRITTEN,
                    "Number of keys written");
            registerGauge("rocksdb.number.keys.read", storeName, stats, TickerType.NUMBER_KEYS_READ,
                    "Number of keys read");
            registerGauge("rocksdb.number.keys.updated", storeName, stats, TickerType.NUMBER_KEYS_UPDATED,
                    "Number of keys updated");
            registerGauge("rocksdb.bytes.read", storeName, stats, TickerType.BYTES_READ,
                    "Total bytes read");
            registerGauge("rocksdb.bytes.written", storeName, stats, TickerType.BYTES_WRITTEN,
                    "Total bytes written");
            
            // Compaction Metrics (Requirements 10.1, 10.2, 10.3, 10.4, 10.5)
            registerGauge("rocksdb.compact.write.bytes", storeName, stats, TickerType.COMPACT_WRITE_BYTES,
                    "Bytes written during compaction");
            registerGauge("rocksdb.compact.read.bytes", storeName, stats, TickerType.COMPACT_READ_BYTES,
                    "Bytes read during compaction");
            
            // Latency Metrics (Requirements 9.3, 9.4) - using histograms
            registerHistogramMetric("rocksdb.db.get.micros", storeName, stats, HistogramType.DB_GET,
                    "Get operation latency in microseconds");
            registerHistogramMetric("rocksdb.db.write.micros", storeName, stats, HistogramType.DB_WRITE,
                    "Write operation latency in microseconds");
            
            log.debug("Successfully registered metrics for store: {}", storeName);
            
        } catch (Exception e) {
            log.warn("Error registering metrics for store: {}", storeName, e);
            throw e;
        }
    }
    
    /**
     * Registers a gauge metric for a RocksDB ticker.
     * Uses cached AtomicLong values to avoid recreating gauges.
     * 
     * @param metricName the name of the metric
     * @param storeName the state store name
     * @param stats the Statistics object
     * @param ticker the RocksDB ticker type
     * @param description the metric description
     */
    private void registerGauge(String metricName, String storeName, Statistics stats, 
                               TickerType ticker, String description) {
        try {
            String cacheKey = metricName + "." + storeName;
            
            // Get or create cached value holder
            AtomicLong valueHolder = gaugeCache.computeIfAbsent(cacheKey, k -> {
                AtomicLong holder = new AtomicLong(0);
                
                // Register the gauge once
                Gauge.builder(metricName, holder, AtomicLong::get)
                        .description(description)
                        .tags(Arrays.asList(
                                Tag.of("state-store", storeName),
                                Tag.of("column-family", "default")
                        ))
                        .register(meterRegistry);
                
                return holder;
            });
            
            // Update the value
            long value = getStatValue(stats, ticker);
            valueHolder.set(value);
            
        } catch (Exception e) {
            log.debug("Failed to register gauge {}: {}", metricName, e.getMessage());
        }
    }
    
    /**
     * Registers a gauge metric for a RocksDB histogram percentile.
     * Uses cached AtomicLong values to avoid recreating gauges.
     * 
     * @param metricName the name of the metric
     * @param storeName the state store name
     * @param stats the Statistics object
     * @param histogram the RocksDB histogram type
     * @param description the metric description
     */
    private void registerHistogramMetric(String metricName, String storeName, Statistics stats,
                                        HistogramType histogram, String description) {
        try {
            String cacheKey = metricName + "." + storeName;
            
            // Get or create cached value holder
            AtomicLong valueHolder = gaugeCache.computeIfAbsent(cacheKey, k -> {
                AtomicLong holder = new AtomicLong(0);
                
                // Register the gauge once
                Gauge.builder(metricName, holder, AtomicLong::get)
                        .description(description)
                        .tags(Arrays.asList(
                                Tag.of("state-store", storeName),
                                Tag.of("column-family", "default")
                        ))
                        .register(meterRegistry);
                
                return holder;
            });
            
            // Update the value - get average from histogram
            long value = getHistogramValue(stats, histogram);
            valueHolder.set(value);
            
        } catch (Exception e) {
            log.debug("Failed to register histogram metric {}: {}", metricName, e.getMessage());
        }
    }
    
    /**
     * Helper method to safely get a ticker value from Statistics.
     * Returns 0 if the ticker is not available.
     * 
     * @param stats the Statistics object
     * @param ticker the ticker type to retrieve
     * @return the ticker value, or 0 if not available
     */
    private long getStatValue(Statistics stats, TickerType ticker) {
        try {
            return stats.getTickerCount(ticker);
        } catch (Exception e) {
            log.debug("Failed to get ticker value for {}: {}", ticker, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Helper method to safely get a histogram percentile value from Statistics.
     * Returns 0 if the histogram is not available.
     * 
     * @param stats the Statistics object
     * @param histogram the histogram type to retrieve
     * @return the histogram average value, or 0 if not available
     */
    private long getHistogramValue(Statistics stats, HistogramType histogram) {
        try {
            HistogramData data = stats.getHistogramData(histogram);
            if (data == null) {
                return 0;
            }
            
            // Return the average value from the histogram
            return (long) data.getAverage();
        } catch (Exception e) {
            log.debug("Failed to get histogram value for {}: {}", histogram, e.getMessage());
            return 0;
        }
    }
}
