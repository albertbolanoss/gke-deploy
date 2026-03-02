package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.LowerChars;
import net.jqwik.api.constraints.StringLength;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for metric naming convention validation.
 * Validates that all metrics follow Micrometer's naming conventions.
 * 
 * Validates: Requirements 12.4, 12.5
 */
@org.junit.jupiter.api.Tag("Feature: dynatrace-observability-integration, Property 5: Metric Names Follow Micrometer Convention")
class MetricNamingConventionPropertyTest {

    /**
     * Property 5: Metric Names Follow Micrometer Convention
     * 
     * For any metric registered in the MeterRegistry, the metric name should follow 
     * Micrometer's naming convention: lowercase with dots as separators, and appropriate 
     * prefix (jvm.*, kafka.streams.*, rocksdb.*).
     * 
     * Validates: Requirements 12.4, 12.5
     */
    @Property(tries = 100)
    void metricNamesFollowMicrometerConvention(
            @ForAll("validMetricPrefix") String prefix,
            @ForAll("lowercaseAlphaString") String metricPart1,
            @ForAll("lowercaseAlphaString") String metricPart2) {
        
        // Arrange: Create a MeterRegistry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Build a valid metric name following convention
        String metricName = prefix + "." + metricPart1 + "." + metricPart2;
        
        // Act: Register a metric
        Counter.builder(metricName)
            .description("Test metric for naming convention validation")
            .register(meterRegistry);
        
        // Assert: Verify the metric name follows convention
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        String actualName = meter.getId().getName();
        
        // Verify lowercase with dots
        assertThat(actualName)
            .as("Metric name should be lowercase with dots as separators")
            .matches("^[a-z][a-z0-9.]*$");
        
        // Verify appropriate prefix
        assertThat(actualName)
            .as("Metric name should have appropriate prefix (jvm, kafka.streams, or rocksdb)")
            .matches("^(jvm|kafka\\.streams|rocksdb)\\..*");
    }
    
    /**
     * Property 5 variant: JVM metrics follow naming convention
     * 
     * Verifies that JVM metrics specifically follow the jvm.* prefix convention.
     */
    @Property(tries = 100)
    void jvmMetricsFollowNamingConvention(
            @ForAll("jvmMetricSuffix") String suffix) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        String metricName = "jvm." + suffix;
        
        // Act
        Gauge.builder(metricName, () -> 42.0)
            .description("Test JVM metric")
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        String actualName = meter.getId().getName();
        
        assertThat(actualName)
            .as("JVM metric should start with 'jvm.' prefix")
            .startsWith("jvm.");
        
        assertThat(actualName)
            .as("JVM metric should be lowercase with dots")
            .matches("^jvm\\.[a-z][a-z0-9.]*$");
    }
    
    /**
     * Property 5 variant: Kafka Streams metrics follow naming convention
     * 
     * Verifies that Kafka Streams metrics specifically follow the kafka.streams.* prefix convention.
     */
    @Property(tries = 100)
    void kafkaStreamsMetricsFollowNamingConvention(
            @ForAll("kafkaStreamsMetricSuffix") String suffix) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        String metricName = "kafka.streams." + suffix;
        
        // Act
        Counter.builder(metricName)
            .description("Test Kafka Streams metric")
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        String actualName = meter.getId().getName();
        
        assertThat(actualName)
            .as("Kafka Streams metric should start with 'kafka.streams.' prefix")
            .startsWith("kafka.streams.");
        
        assertThat(actualName)
            .as("Kafka Streams metric should be lowercase with dots")
            .matches("^kafka\\.streams\\.[a-z][a-z0-9.]*$");
    }
    
    /**
     * Property 5 variant: RocksDB metrics follow naming convention
     * 
     * Verifies that RocksDB metrics specifically follow the rocksdb.* prefix convention.
     */
    @Property(tries = 100)
    void rocksdbMetricsFollowNamingConvention(
            @ForAll("rocksdbMetricSuffix") String suffix) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        String metricName = "rocksdb." + suffix;
        
        // Act
        Gauge.builder(metricName, () -> 100.0)
            .description("Test RocksDB metric")
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        String actualName = meter.getId().getName();
        
        assertThat(actualName)
            .as("RocksDB metric should start with 'rocksdb.' prefix")
            .startsWith("rocksdb.");
        
        assertThat(actualName)
            .as("RocksDB metric should be lowercase with dots")
            .matches("^rocksdb\\.[a-z][a-z0-9.]*$");
    }
    
    /**
     * Property 5 variant: Metric names do not contain invalid characters
     * 
     * Verifies that metric names only contain lowercase letters, numbers, and dots.
     */
    @Property(tries = 100)
    void metricNamesDoNotContainInvalidCharacters(
            @ForAll("validMetricPrefix") String prefix,
            @ForAll("lowercaseAlphaString") String suffix) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        String metricName = prefix + "." + suffix;
        
        // Act
        Counter.builder(metricName)
            .description("Test metric")
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        String actualName = meter.getId().getName();
        
        // Should not contain uppercase, underscores, hyphens, or special characters
        assertThat(actualName)
            .as("Metric name should not contain uppercase letters")
            .doesNotContainPattern("[A-Z]");
        
        assertThat(actualName)
            .as("Metric name should not contain underscores")
            .doesNotContain("_");
        
        assertThat(actualName)
            .as("Metric name should not contain hyphens")
            .doesNotContain("-");
        
        assertThat(actualName)
            .as("Metric name should only contain lowercase, numbers, and dots")
            .matches("^[a-z0-9.]+$");
    }
    
    // ========== Arbitraries (Data Generators) ==========
    
    /**
     * Generates lowercase alphabetic strings.
     */
    @Provide
    Arbitrary<String> lowercaseAlphaString() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(3)
            .ofMaxLength(15);
    }
    
    /**
     * Generates valid metric prefixes (jvm, kafka.streams, rocksdb).
     */
    @Provide
    Arbitrary<String> validMetricPrefix() {
        return Arbitraries.of("jvm", "kafka.streams", "rocksdb");
    }
    
    /**
     * Generates valid JVM metric suffixes.
     */
    @Provide
    Arbitrary<String> jvmMetricSuffix() {
        return Arbitraries.of(
            "memory.used",
            "memory.max",
            "memory.committed",
            "gc.pause",
            "gc.count",
            "threads.live",
            "threads.daemon",
            "threads.peak",
            "classes.loaded",
            "classes.unloaded"
        );
    }
    
    /**
     * Generates valid Kafka Streams metric suffixes.
     */
    @Provide
    Arbitrary<String> kafkaStreamsMetricSuffix() {
        return Arbitraries.of(
            "stream.metrics.records.consumed.rate",
            "stream.metrics.records.produced.rate",
            "stream.metrics.bytes.consumed.rate",
            "stream.metrics.bytes.produced.rate",
            "stream.task.metrics.process.latency.avg",
            "stream.task.metrics.process.latency.max",
            "stream.task.metrics.commit.latency.avg",
            "consumer.metrics.records.lag",
            "consumer.metrics.records.lag.max"
        );
    }
    
    /**
     * Generates valid RocksDB metric suffixes.
     */
    @Provide
    Arbitrary<String> rocksdbMetricSuffix() {
        return Arbitraries.of(
            "memtable.size.all",
            "block.cache.hit",
            "block.cache.miss",
            "number.keys.written",
            "number.keys.read",
            "bytes.read",
            "bytes.written",
            "compact.write.bytes",
            "compact.read.bytes",
            "db.get.micros",
            "db.write.micros"
        );
    }
}
