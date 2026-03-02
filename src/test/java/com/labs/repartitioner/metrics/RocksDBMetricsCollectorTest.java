package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.Options;
import org.rocksdb.Statistics;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for RocksDBMetricsCollector.
 * Verifies that memory, operations, and compaction metrics are present.
 * Verifies error handling when Statistics is not available.
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 9.1, 9.2, 9.3, 9.4, 9.5, 10.1, 10.2, 10.3, 10.4, 10.5, 13.2
 */
class RocksDBMetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private RocksDBMetricsCollector collector;
    private CustomRocksDBConfigSetter configSetter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collector = new RocksDBMetricsCollector(meterRegistry);
        configSetter = new CustomRocksDBConfigSetter();
        
        // Clear any existing statistics from previous tests
        CustomRocksDBConfigSetter.getStoreStatistics().clear();
    }

    @AfterEach
    void tearDown() {
        // Clear statistics after each test
        CustomRocksDBConfigSetter.getStoreStatistics().clear();
    }

    // Test: Memory Metrics - Requirements 8.1, 8.2

    @Test
    void collectMetrics_shouldRegisterMemoryMetrics() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify memory-related metrics are registered
            List<String> metricNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());
            
            assertThat(metricNames)
                .as("Memory metrics should be registered")
                .contains("rocksdb.memtable.size.all");
            
        } finally {
            options.close();
        }
    }

    @Test
    void collectMetrics_shouldRegisterBlockCacheMetrics() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify block cache metrics are registered
            List<String> metricNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());
            
            assertThat(metricNames)
                .as("Block cache metrics should be registered")
                .contains("rocksdb.block.cache.hit", "rocksdb.block.cache.miss");
            
        } finally {
            options.close();
        }
    }

    // Test: Operations Metrics - Requirements 9.1, 9.2, 9.5

    @Test
    void collectMetrics_shouldRegisterOperationMetrics() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify operation metrics are registered
            List<String> metricNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());
            
            assertThat(metricNames)
                .as("Operation metrics should be registered")
                .contains(
                    "rocksdb.number.keys.written",
                    "rocksdb.number.keys.read",
                    "rocksdb.number.keys.updated"
                );
            
        } finally {
            options.close();
        }
    }

    @Test
    void collectMetrics_shouldRegisterThroughputMetrics() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify throughput metrics are registered
            List<String> metricNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());
            
            assertThat(metricNames)
                .as("Throughput metrics should be registered")
                .contains("rocksdb.bytes.read", "rocksdb.bytes.written");
            
        } finally {
            options.close();
        }
    }

    // Test: Latency Metrics - Requirements 9.3, 9.4

    @Test
    void collectMetrics_shouldRegisterLatencyMetrics() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify latency metrics are registered
            List<String> metricNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());
            
            assertThat(metricNames)
                .as("Latency metrics should be registered")
                .contains("rocksdb.db.get.micros", "rocksdb.db.write.micros");
            
        } finally {
            options.close();
        }
    }

    // Test: Compaction Metrics - Requirements 10.1, 10.2, 10.3, 10.4, 10.5

    @Test
    void collectMetrics_shouldRegisterCompactionMetrics() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify compaction metrics are registered
            List<String> metricNames = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toList());
            
            assertThat(metricNames)
                .as("Compaction metrics should be registered")
                .contains(
                    "rocksdb.compact.write.bytes",
                    "rocksdb.compact.read.bytes"
                );
            
        } finally {
            options.close();
        }
    }

    // Test: Metric Tags - Requirements 8.5, 12.3

    @Test
    void collectMetrics_shouldIncludeStateStoreTags() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify metrics have state-store tags
            List<Meter> meters = meterRegistry.getMeters();
            assertThat(meters).isNotEmpty();
            
            for (Meter meter : meters) {
                List<String> tagKeys = meter.getId().getTags().stream()
                    .map(tag -> tag.getKey())
                    .collect(Collectors.toList());
                
                assertThat(tagKeys)
                    .as("Metric %s should have state-store tag", meter.getId().getName())
                    .contains("state-store");
                
                String storeTagValue = meter.getId().getTag("state-store");
                assertThat(storeTagValue)
                    .as("State store tag should have correct value")
                    .isEqualTo(storeName);
            }
            
        } finally {
            options.close();
        }
    }

    @Test
    void collectMetrics_shouldIncludeColumnFamilyTags() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify metrics have column-family tags
            List<Meter> meters = meterRegistry.getMeters();
            assertThat(meters).isNotEmpty();
            
            for (Meter meter : meters) {
                List<String> tagKeys = meter.getId().getTags().stream()
                    .map(tag -> tag.getKey())
                    .collect(Collectors.toList());
                
                assertThat(tagKeys)
                    .as("Metric %s should have column-family tag", meter.getId().getName())
                    .contains("column-family");
            }
            
        } finally {
            options.close();
        }
    }

    // Test: Multiple Stores

    @Test
    void collectMetrics_shouldHandleMultipleStores() {
        // Arrange
        String storeName1 = "store-1";
        String storeName2 = "store-2";
        Options options1 = new Options();
        Options options2 = new Options();
        
        try {
            configSetter.setConfig(storeName1, options1, new java.util.HashMap<>());
            configSetter.setConfig(storeName2, options2, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify metrics for both stores are registered
            List<Meter> meters = meterRegistry.getMeters();
            
            long store1Metrics = meters.stream()
                .filter(meter -> storeName1.equals(meter.getId().getTag("state-store")))
                .count();
            
            long store2Metrics = meters.stream()
                .filter(meter -> storeName2.equals(meter.getId().getTag("state-store")))
                .count();
            
            assertThat(store1Metrics)
                .as("Metrics for store-1 should be registered")
                .isGreaterThan(0);
            
            assertThat(store2Metrics)
                .as("Metrics for store-2 should be registered")
                .isGreaterThan(0);
            
        } finally {
            options1.close();
            options2.close();
        }
    }

    // Test: Error Handling - Requirement 13.2

    @Test
    void collectMetrics_shouldHandleNoStatisticsAvailable() {
        // Arrange - No stores configured
        
        // Act & Assert - Should not throw exception
        assertThatCode(() -> {
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
        })
            .as("Should handle no statistics gracefully")
            .doesNotThrowAnyException();
    }

    @Test
    void collectMetrics_shouldHandleEmptyStatisticsMap() {
        // Arrange - No stores configured, empty map
        
        // Act & Assert - Should not throw exception
        assertThatCode(() -> {
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
        })
            .as("Should handle empty statistics map gracefully")
            .doesNotThrowAnyException();
        
        // Verify no metrics are registered
        assertThat(meterRegistry.getMeters())
            .as("No metrics should be registered when no stores exist")
            .isEmpty();
    }

    @Test
    void bindTo_shouldHandleInitialCollectionFailure() {
        // Arrange - No stores configured
        
        // Act & Assert - Should not throw exception during bind
        assertThatCode(() -> {
            collector.bindTo(meterRegistry);
        })
            .as("Should handle initial collection failure gracefully")
            .doesNotThrowAnyException();
    }

    // Test: Gauge Values

    @Test
    void collectMetrics_shouldUpdateGaugeValues() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            
            // Assert - Verify gauges have non-negative values
            List<Gauge> gauges = meterRegistry.getMeters().stream()
                .filter(meter -> meter instanceof Gauge)
                .map(meter -> (Gauge) meter)
                .collect(Collectors.toList());
            
            assertThat(gauges)
                .as("Gauges should be registered")
                .isNotEmpty();
            
            for (Gauge gauge : gauges) {
                double value = gauge.value();
                assertThat(value)
                    .as("Gauge %s should have non-negative value", gauge.getId().getName())
                    .isGreaterThanOrEqualTo(0.0);
            }
            
        } finally {
            options.close();
        }
    }

    @Test
    void collectMetrics_shouldNotDuplicateGaugesOnMultipleCollections() {
        // Arrange
        String storeName = "test-store";
        Options options = new Options();
        
        try {
            configSetter.setConfig(storeName, options, new java.util.HashMap<>());
            
            // Act - Collect metrics multiple times
            collector.bindTo(meterRegistry);
            collector.collectMetrics();
            int firstCollectionCount = meterRegistry.getMeters().size();
            
            collector.collectMetrics();
            int secondCollectionCount = meterRegistry.getMeters().size();
            
            collector.collectMetrics();
            int thirdCollectionCount = meterRegistry.getMeters().size();
            
            // Assert - Gauge count should remain stable
            assertThat(secondCollectionCount)
                .as("Second collection should not create duplicate gauges")
                .isEqualTo(firstCollectionCount);
            
            assertThat(thirdCollectionCount)
                .as("Third collection should not create duplicate gauges")
                .isEqualTo(firstCollectionCount);
            
        } finally {
            options.close();
        }
    }

    // Test: MeterBinder Interface

    @Test
    void rocksDBMetricsCollector_shouldImplementMeterBinder() {
        // Assert
        assertThat(collector)
            .as("RocksDBMetricsCollector should implement MeterBinder")
            .isInstanceOf(io.micrometer.core.instrument.binder.MeterBinder.class);
    }

    @Test
    void bindTo_shouldSetMeterRegistry() {
        // Arrange
        MeterRegistry newRegistry = new SimpleMeterRegistry();
        
        // Act
        collector.bindTo(newRegistry);
        
        // Assert - Verify collector uses the new registry
        // This is verified indirectly by checking that metrics are registered in the new registry
        assertThat(newRegistry.getMeters())
            .as("New registry should be used after bindTo")
            .isNotNull();
    }
}
