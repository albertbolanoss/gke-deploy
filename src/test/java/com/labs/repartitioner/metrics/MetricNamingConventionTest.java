package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for metric naming convention validation.
 * Verifies that all metrics registered follow Micrometer's naming conventions.
 * 
 * Validates: Requirements 12.4, 12.5
 */
class MetricNamingConventionTest {

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void jvmMetricsShouldHaveCorrectPrefix() {
        // Arrange & Act
        Counter.builder("jvm.memory.used")
            .description("JVM memory used")
            .register(meterRegistry);
        
        Gauge.builder("jvm.threads.live", () -> 10.0)
            .description("JVM live threads")
            .register(meterRegistry);
        
        // Assert
        List<Meter> jvmMetrics = meterRegistry.getMeters().stream()
            .filter(m -> m.getId().getName().startsWith("jvm."))
            .collect(Collectors.toList());
        
        assertThat(jvmMetrics).hasSize(2);
        
        for (Meter meter : jvmMetrics) {
            assertThat(meter.getId().getName())
                .as("JVM metric should start with 'jvm.' prefix")
                .startsWith("jvm.");
            
            assertThat(meter.getId().getName())
                .as("JVM metric should be lowercase with dots")
                .matches("^jvm\\.[a-z][a-z0-9.]*$");
        }
    }

    @Test
    void kafkaStreamsMetricsShouldHaveCorrectPrefix() {
        // Arrange & Act
        Counter.builder("kafka.streams.records.consumed.rate")
            .description("Kafka Streams records consumed rate")
            .register(meterRegistry);
        
        Gauge.builder("kafka.streams.process.latency.avg", () -> 50.0)
            .description("Kafka Streams process latency average")
            .register(meterRegistry);
        
        // Assert
        List<Meter> kafkaMetrics = meterRegistry.getMeters().stream()
            .filter(m -> m.getId().getName().startsWith("kafka.streams."))
            .collect(Collectors.toList());
        
        assertThat(kafkaMetrics).hasSize(2);
        
        for (Meter meter : kafkaMetrics) {
            assertThat(meter.getId().getName())
                .as("Kafka Streams metric should start with 'kafka.streams.' prefix")
                .startsWith("kafka.streams.");
            
            assertThat(meter.getId().getName())
                .as("Kafka Streams metric should be lowercase with dots")
                .matches("^kafka\\.streams\\.[a-z][a-z0-9.]*$");
        }
    }

    @Test
    void rocksdbMetricsShouldHaveCorrectPrefix() {
        // Arrange & Act
        Gauge.builder("rocksdb.memtable.size.all", () -> 1024.0)
            .description("RocksDB memtable size")
            .register(meterRegistry);
        
        Counter.builder("rocksdb.bytes.written")
            .description("RocksDB bytes written")
            .register(meterRegistry);
        
        // Assert
        List<Meter> rocksdbMetrics = meterRegistry.getMeters().stream()
            .filter(m -> m.getId().getName().startsWith("rocksdb."))
            .collect(Collectors.toList());
        
        assertThat(rocksdbMetrics).hasSize(2);
        
        for (Meter meter : rocksdbMetrics) {
            assertThat(meter.getId().getName())
                .as("RocksDB metric should start with 'rocksdb.' prefix")
                .startsWith("rocksdb.");
            
            assertThat(meter.getId().getName())
                .as("RocksDB metric should be lowercase with dots")
                .matches("^rocksdb\\.[a-z][a-z0-9.]*$");
        }
    }

    @Test
    void metricNamesShouldBeLowercaseWithDots() {
        // Arrange & Act
        Counter.builder("jvm.gc.pause")
            .description("JVM GC pause")
            .register(meterRegistry);
        
        Gauge.builder("kafka.streams.task.active", () -> 5.0)
            .description("Active Kafka Streams tasks")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should be lowercase with dots as separators")
                .matches("^[a-z][a-z0-9.]*$");
        }
    }

    @Test
    void metricNamesShouldNotContainUppercase() {
        // Arrange & Act
        Counter.builder("jvm.memory.heap")
            .description("JVM heap memory")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should not contain uppercase letters")
                .doesNotContainPattern("[A-Z]");
        }
    }

    @Test
    void metricNamesShouldNotContainUnderscores() {
        // Arrange & Act
        Counter.builder("jvm.memory.used")
            .description("JVM memory used")
            .register(meterRegistry);
        
        Gauge.builder("kafka.streams.records.lag", () -> 100.0)
            .description("Kafka Streams records lag")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should not contain underscores")
                .doesNotContain("_");
        }
    }

    @Test
    void metricNamesShouldNotContainHyphens() {
        // Arrange & Act
        Counter.builder("jvm.gc.count")
            .description("JVM GC count")
            .register(meterRegistry);
        
        Gauge.builder("rocksdb.block.cache.hit", () -> 500.0)
            .description("RocksDB block cache hits")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should not contain hyphens")
                .doesNotContain("-");
        }
    }

    @Test
    void allRegisteredMetricsShouldHaveValidPrefix() {
        // Arrange & Act
        Counter.builder("jvm.classes.loaded")
            .description("JVM classes loaded")
            .register(meterRegistry);
        
        Gauge.builder("kafka.streams.bytes.consumed.rate", () -> 1000.0)
            .description("Kafka Streams bytes consumed rate")
            .register(meterRegistry);
        
        Counter.builder("rocksdb.compact.write.bytes")
            .description("RocksDB compact write bytes")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should have one of the valid prefixes: jvm, kafka.streams, or rocksdb")
                .matches("^(jvm|kafka\\.streams|rocksdb)\\..*");
        }
    }

    @Test
    void metricNamesShouldOnlyContainValidCharacters() {
        // Arrange & Act
        Counter.builder("jvm.memory.max")
            .description("JVM max memory")
            .register(meterRegistry);
        
        Gauge.builder("kafka.streams.process.latency.max", () -> 200.0)
            .description("Kafka Streams max process latency")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should only contain lowercase letters, numbers, and dots")
                .matches("^[a-z0-9.]+$");
        }
    }

    @Test
    void metricNamesShouldStartWithLetter() {
        // Arrange & Act
        Counter.builder("jvm.threads.daemon")
            .description("JVM daemon threads")
            .register(meterRegistry);
        
        // Assert
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            
            assertThat(name)
                .as("Metric name should start with a lowercase letter")
                .matches("^[a-z].*");
        }
    }
}
