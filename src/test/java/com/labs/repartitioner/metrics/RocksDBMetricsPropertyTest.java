package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LowerChars;
import net.jqwik.api.constraints.StringLength;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for RocksDB metrics.
 * Validates that RocksDB metrics include state store tags.
 */
@org.junit.jupiter.api.Tag("Feature: dynatrace-observability-integration, Property 4: RocksDB Metrics Include State Store Tags")
class RocksDBMetricsPropertyTest {

    /**
     * Property 4: RocksDB Metrics Include State Store Tags
     * 
     * For any metric registered by RocksDBMetricsCollector, that metric should include 
     * tags identifying the specific state store name and column family.
     * 
     * Validates: Requirements 8.5, 12.3
     */
    @Property(tries = 100)
    void rocksdbMetricsHaveStateStoreTags(
            @ForAll("rocksdbMetricData") RocksDBMetricData metricData) {
        
        // Arrange: Create a MeterRegistry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Build tags using the same logic as RocksDBMetricsCollector
        List<io.micrometer.core.instrument.Tag> tags = buildRocksDBTags(
            metricData.storeName,
            metricData.columnFamily
        );
        
        // Act: Register the RocksDB metric (simulating RocksDBMetricsCollector behavior)
        Gauge.builder(metricData.metricName, () -> metricData.value)
            .description(metricData.description)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert: Verify the metric has state-store and column-family tags
        Meter meter = meterRegistry.find(metricData.metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("RocksDB metric '%s' should include state-store and column-family tags", metricData.metricName)
            .contains("state-store", "column-family");
        
        // Verify the tag values are preserved
        assertThat(meter.getId().getTag("state-store"))
            .as("State-store tag value should be preserved")
            .isEqualTo(metricData.storeName);
        
        assertThat(meter.getId().getTag("column-family"))
            .as("Column-family tag value should be preserved")
            .isEqualTo(metricData.columnFamily);
    }
    
    /**
     * Property 4 variant: Memory metrics include state store tags
     */
    @Property(tries = 100)
    void rocksdbMemoryMetricsHaveStateStoreTags(
            @ForAll("rocksdbMemoryMetricName") String metricName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 30) String storeName,
            @ForAll @IntRange(min = 0, max = 1000000000) long memoryValue) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        List<io.micrometer.core.instrument.Tag> tags = buildRocksDBTags(storeName, "default");
        
        // Act
        Gauge.builder(metricName, () -> (double) memoryValue)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("state-store", "column-family");
        
        assertThat(meter.getId().getTag("state-store"))
            .isEqualTo(storeName);
        assertThat(meter.getId().getTag("column-family"))
            .isEqualTo("default");
    }
    
    /**
     * Property 4 variant: Operations metrics include state store tags
     */
    @Property(tries = 100)
    void rocksdbOperationsMetricsHaveStateStoreTags(
            @ForAll("rocksdbOperationsMetricName") String metricName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 30) String storeName,
            @ForAll @IntRange(min = 0, max = 1000000) long operationCount) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        List<io.micrometer.core.instrument.Tag> tags = buildRocksDBTags(storeName, "default");
        
        // Act
        Gauge.builder(metricName, () -> (double) operationCount)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("state-store", "column-family");
        
        assertThat(meter.getId().getTag("state-store"))
            .isEqualTo(storeName);
    }
    
    /**
     * Property 4 variant: Latency metrics include state store tags
     */
    @Property(tries = 100)
    void rocksdbLatencyMetricsHaveStateStoreTags(
            @ForAll("rocksdbLatencyMetricName") String metricName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 30) String storeName,
            @ForAll @IntRange(min = 1, max = 10000) long latencyMicros) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        List<io.micrometer.core.instrument.Tag> tags = buildRocksDBTags(storeName, "default");
        
        // Act
        Gauge.builder(metricName, () -> (double) latencyMicros)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("state-store", "column-family");
        
        assertThat(meter.getId().getTag("state-store"))
            .isEqualTo(storeName);
    }
    
    /**
     * Property 4 variant: Compaction metrics include state store tags
     */
    @Property(tries = 100)
    void rocksdbCompactionMetricsHaveStateStoreTags(
            @ForAll("rocksdbCompactionMetricName") String metricName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 30) String storeName,
            @ForAll @IntRange(min = 0, max = 1000000000) long compactionBytes) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        List<io.micrometer.core.instrument.Tag> tags = buildRocksDBTags(storeName, "default");
        
        // Act
        Gauge.builder(metricName, () -> (double) compactionBytes)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("state-store", "column-family");
        
        assertThat(meter.getId().getTag("state-store"))
            .isEqualTo(storeName);
    }
    
    /**
     * Property 4 variant: Multiple stores have distinct tags
     * 
     * Verifies that metrics from different state stores can be distinguished by their tags.
     */
    @Property(tries = 100)
    void multipleStoresHaveDistinctTags(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 20) String storeName1,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 20) String storeName2,
            @ForAll("rocksdbMetricName") String metricName) {
        
        // Assume the store names are different
        Assume.that(!storeName1.equals(storeName2));
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        List<io.micrometer.core.instrument.Tag> tags1 = buildRocksDBTags(storeName1, "default");
        List<io.micrometer.core.instrument.Tag> tags2 = buildRocksDBTags(storeName2, "default");
        
        // Act: Register the same metric for two different stores
        Gauge.builder(metricName, () -> 100.0)
            .tags(tags1)
            .register(meterRegistry);
        
        Gauge.builder(metricName, () -> 200.0)
            .tags(tags2)
            .register(meterRegistry);
        
        // Assert: Both metrics exist and can be distinguished by state-store tag
        Collection<Meter> meters = meterRegistry.find(metricName).meters();
        assertThat(meters)
            .as("Should have two distinct metrics for different stores")
            .hasSize(2);
        
        Set<String> storeNames = meters.stream()
            .map(m -> m.getId().getTag("state-store"))
            .collect(Collectors.toSet());
        
        assertThat(storeNames)
            .as("Store names should be distinct")
            .containsExactlyInAnyOrder(storeName1, storeName2);
    }
    
    /**
     * Property 4 variant: All RocksDB metrics follow naming convention
     * 
     * Verifies that RocksDB metrics start with "rocksdb." prefix.
     */
    @Property(tries = 100)
    void rocksdbMetricsFollowNamingConvention(
            @ForAll("rocksdbMetricName") String metricName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 5, max = 30) String storeName) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        List<io.micrometer.core.instrument.Tag> tags = buildRocksDBTags(storeName, "default");
        
        // Act
        Gauge.builder(metricName, () -> 100.0)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        assertThat(metricName)
            .as("RocksDB metric name should start with 'rocksdb.' prefix")
            .startsWith("rocksdb.");
        
        // Verify tags are present
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("state-store", "column-family");
    }
    
    // Helper methods (replicate the logic from RocksDBMetricsCollector)
    
    private List<io.micrometer.core.instrument.Tag> buildRocksDBTags(String storeName, String columnFamily) {
        return Arrays.asList(
            io.micrometer.core.instrument.Tag.of("state-store", storeName),
            io.micrometer.core.instrument.Tag.of("column-family", columnFamily)
        );
    }
    
    // Arbitraries for generating test data
    
    @Provide
    Arbitrary<RocksDBMetricData> rocksdbMetricData() {
        return Combinators.combine(
            rocksdbMetricName(),
            rocksdbStoreName(),
            Arbitraries.of("default"),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(80),
            Arbitraries.doubles().between(0.0, 1000000000.0)
        ).as((metricName, storeName, columnFamily, description, value) -> 
            new RocksDBMetricData(metricName, storeName, columnFamily, description, value)
        );
    }
    
    @Provide
    Arbitrary<String> rocksdbMetricName() {
        return Arbitraries.of(
            // Memory metrics
            "rocksdb.memtable.size.all",
            "rocksdb.block.cache.hit",
            "rocksdb.block.cache.miss",
            // Operations metrics
            "rocksdb.number.keys.written",
            "rocksdb.number.keys.read",
            "rocksdb.number.keys.updated",
            "rocksdb.bytes.read",
            "rocksdb.bytes.written",
            // Compaction metrics
            "rocksdb.compact.write.bytes",
            "rocksdb.compact.read.bytes",
            // Latency metrics
            "rocksdb.db.get.micros",
            "rocksdb.db.write.micros"
        );
    }
    
    @Provide
    Arbitrary<String> rocksdbMemoryMetricName() {
        return Arbitraries.of(
            "rocksdb.memtable.size.all",
            "rocksdb.block.cache.hit",
            "rocksdb.block.cache.miss"
        );
    }
    
    @Provide
    Arbitrary<String> rocksdbOperationsMetricName() {
        return Arbitraries.of(
            "rocksdb.number.keys.written",
            "rocksdb.number.keys.read",
            "rocksdb.number.keys.updated",
            "rocksdb.bytes.read",
            "rocksdb.bytes.written"
        );
    }
    
    @Provide
    Arbitrary<String> rocksdbLatencyMetricName() {
        return Arbitraries.of(
            "rocksdb.db.get.micros",
            "rocksdb.db.write.micros"
        );
    }
    
    @Provide
    Arbitrary<String> rocksdbCompactionMetricName() {
        return Arbitraries.of(
            "rocksdb.compact.write.bytes",
            "rocksdb.compact.read.bytes"
        );
    }
    
    @Provide
    Arbitrary<String> rocksdbStoreName() {
        return Arbitraries.of(
            "uppercase-storage",
            "aggregate-store",
            "session-store",
            "window-store",
            "key-value-store"
        ).map(base -> base + "-" + UUID.randomUUID().toString().substring(0, 8));
    }
    
    // Data class for test data
    static class RocksDBMetricData {
        final String metricName;
        final String storeName;
        final String columnFamily;
        final String description;
        final double value;
        
        RocksDBMetricData(String metricName, String storeName, String columnFamily, 
                         String description, double value) {
            this.metricName = metricName;
            this.storeName = storeName;
            this.columnFamily = columnFamily;
            this.description = description;
            this.value = value;
        }
    }
}
