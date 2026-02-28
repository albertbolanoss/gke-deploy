package com.labs.repartitioner.metrics;

import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.Options;
import org.rocksdb.Statistics;
import org.rocksdb.StatsLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom RocksDB configuration setter that enables statistics collection for state stores.
 * This allows RocksDBMetricsCollector to access internal RocksDB metrics for monitoring.
 * 
 * Requirements: 7.1, 7.2, 7.3
 */
public class CustomRocksDBConfigSetter implements RocksDBConfigSetter {
    
    private static final Logger log = LoggerFactory.getLogger(CustomRocksDBConfigSetter.class);
    
    // Static map to store Statistics objects per store name
    // This allows external access for metrics collection
    private static final Map<String, Statistics> storeStatistics = new ConcurrentHashMap<>();
    
    @Override
    public void setConfig(String storeName, Options options, Map<String, Object> configs) {
        try {
            // Create and enable Statistics for this store
            Statistics statistics = new Statistics();
            statistics.setStatsLevel(StatsLevel.EXCEPT_DETAILED_TIMERS);
            
            // Set statistics on the Options object
            options.setStatistics(statistics);
            
            // Store the Statistics object for external access
            storeStatistics.put(storeName, statistics);
            
            log.info("Enabled RocksDB statistics for state store: {}", storeName);
        } catch (Exception e) {
            log.error("Failed to enable RocksDB statistics for store: {}", storeName, e);
            // Don't throw - allow RocksDB to continue without statistics
        }
    }
    
    @Override
    public void close(String storeName, Options options) {
        try {
            // Remove the Statistics object when the store is closed
            Statistics statistics = storeStatistics.remove(storeName);
            if (statistics != null) {
                log.info("Cleaned up RocksDB statistics for state store: {}", storeName);
            }
        } catch (Exception e) {
            log.warn("Error during cleanup of RocksDB statistics for store: {}", storeName, e);
        }
    }
    
    /**
     * Provides external access to Statistics objects for all state stores.
     * Used by RocksDBMetricsCollector to retrieve metrics.
     * 
     * @return Map of store name to Statistics object
     */
    public static Map<String, Statistics> getStoreStatistics() {
        return storeStatistics;
    }
}
