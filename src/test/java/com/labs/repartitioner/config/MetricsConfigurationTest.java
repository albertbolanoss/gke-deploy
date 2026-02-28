package com.labs.repartitioner.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsConfiguration.
 * Verifies that the MeterRegistryCustomizer bean is created correctly
 * and that common tags are applied to all metrics.
 * 
 * Requirements: 11.1, 3.1, 3.2, 3.3, 3.4, 3.5
 */
class MetricsConfigurationTest {

    private MetricsConfiguration metricsConfiguration;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        metricsConfiguration = new MetricsConfiguration();
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void metricsCommonTags_shouldCreateCustomizerBean() {
        // Arrange
        String applicationName = "LABS_GKE_DEPLOY";
        String environment = "test";
        String host = "test-host";

        // Act
        MeterRegistryCustomizer<MeterRegistry> customizer = 
            metricsConfiguration.metricsCommonTags(applicationName, environment, host);

        // Assert
        assertThat(customizer).isNotNull();
    }

    @Test
    void metricsCommonTags_shouldApplyCommonTagsToMetrics() {
        // Arrange
        String applicationName = "LABS_GKE_DEPLOY";
        String environment = "production";
        String host = "prod-server-01";

        MeterRegistryCustomizer<MeterRegistry> customizer = 
            metricsConfiguration.metricsCommonTags(applicationName, environment, host);

        // Act
        customizer.customize(meterRegistry);
        
        // Register a test metric
        Counter.builder("test.counter")
            .description("Test counter metric")
            .register(meterRegistry);

        // Assert
        Meter meter = meterRegistry.find("test.counter").meter();
        assertThat(meter).isNotNull();

        List<String> tagKeys = meter.getId().getTags().stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());

        assertThat(tagKeys)
            .as("Metric should have all common tags")
            .contains("application", "environment", "host");

        assertThat(meter.getId().getTag("application")).isEqualTo(applicationName);
        assertThat(meter.getId().getTag("environment")).isEqualTo(environment);
        assertThat(meter.getId().getTag("host")).isEqualTo(host);
    }

    @Test
    void metricsCommonTags_shouldApplyDefaultEnvironmentValue() {
        // Arrange
        String applicationName = "LABS_GKE_DEPLOY";
        String environment = "local"; // Default value
        String host = "localhost";

        MeterRegistryCustomizer<MeterRegistry> customizer = 
            metricsConfiguration.metricsCommonTags(applicationName, environment, host);

        // Act
        customizer.customize(meterRegistry);
        Counter.builder("test.metric").register(meterRegistry);

        // Assert
        Meter meter = meterRegistry.find("test.metric").meter();
        assertThat(meter.getId().getTag("environment")).isEqualTo("local");
    }

    @Test
    void metricsCommonTags_shouldApplyDefaultHostValue() {
        // Arrange
        String applicationName = "LABS_GKE_DEPLOY";
        String environment = "dev";
        String host = "localhost"; // Default value

        MeterRegistryCustomizer<MeterRegistry> customizer = 
            metricsConfiguration.metricsCommonTags(applicationName, environment, host);

        // Act
        customizer.customize(meterRegistry);
        Counter.builder("test.metric").register(meterRegistry);

        // Assert
        Meter meter = meterRegistry.find("test.metric").meter();
        assertThat(meter.getId().getTag("host")).isEqualTo("localhost");
    }

    @Test
    void metricsCommonTags_shouldApplyToAllMeterTypes() {
        // Arrange
        String applicationName = "LABS_GKE_DEPLOY";
        String environment = "staging";
        String host = "staging-host";

        MeterRegistryCustomizer<MeterRegistry> customizer = 
            metricsConfiguration.metricsCommonTags(applicationName, environment, host);

        // Act
        customizer.customize(meterRegistry);
        
        // Register different types of meters
        Counter.builder("test.counter").register(meterRegistry);
        Gauge.builder("test.gauge", () -> 100.0).register(meterRegistry);

        // Assert
        Meter counter = meterRegistry.find("test.counter").meter();
        Meter gauge = meterRegistry.find("test.gauge").meter();

        assertThat(counter).isNotNull();
        assertThat(gauge).isNotNull();

        // Verify both meter types have common tags
        for (Meter meter : List.of(counter, gauge)) {
            List<String> tagKeys = meter.getId().getTags().stream()
                .map(Tag::getKey)
                .collect(Collectors.toList());

            assertThat(tagKeys)
                .as("Meter type '%s' should have all common tags", meter.getId().getType())
                .contains("application", "environment", "host");

            assertThat(meter.getId().getTag("application")).isEqualTo(applicationName);
            assertThat(meter.getId().getTag("environment")).isEqualTo(environment);
            assertThat(meter.getId().getTag("host")).isEqualTo(host);
        }
    }

    @Test
    void metricsCommonTags_shouldApplyToMultipleMetrics() {
        // Arrange
        String applicationName = "LABS_GKE_DEPLOY";
        String environment = "dev";
        String host = "dev-host";

        MeterRegistryCustomizer<MeterRegistry> customizer = 
            metricsConfiguration.metricsCommonTags(applicationName, environment, host);

        // Act
        customizer.customize(meterRegistry);
        
        // Register multiple metrics
        Counter.builder("metric.one").register(meterRegistry);
        Counter.builder("metric.two").register(meterRegistry);
        Counter.builder("metric.three").register(meterRegistry);

        // Assert
        List<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).hasSize(3);

        // Verify all metrics have common tags
        for (Meter meter : meters) {
            List<String> tagKeys = meter.getId().getTags().stream()
                .map(Tag::getKey)
                .collect(Collectors.toList());

            assertThat(tagKeys)
                .as("Metric '%s' should have all common tags", meter.getId().getName())
                .contains("application", "environment", "host");

            assertThat(meter.getId().getTag("application")).isEqualTo(applicationName);
            assertThat(meter.getId().getTag("environment")).isEqualTo(environment);
            assertThat(meter.getId().getTag("host")).isEqualTo(host);
        }
    }

    // JVM Metrics Availability Tests - Requirements: 3.1, 3.2, 3.3, 3.4, 3.5

    @Test
    void classLoaderMetrics_shouldBeRegistered() {
        // Act
        var classLoaderMetrics = metricsConfiguration.classLoaderMetrics();
        classLoaderMetrics.bindTo(meterRegistry);

        // Assert
        assertThat(classLoaderMetrics).isNotNull();
        
        // Verify class loader metrics are present
        assertThat(meterRegistry.find("jvm.classes.loaded").gauge())
            .as("JVM classes loaded metric should be present")
            .isNotNull();
    }

    @Test
    void jvmMemoryMetrics_shouldBeRegistered() {
        // Act
        var jvmMemoryMetrics = metricsConfiguration.jvmMemoryMetrics();
        jvmMemoryMetrics.bindTo(meterRegistry);

        // Assert
        assertThat(jvmMemoryMetrics).isNotNull();
        
        // Verify memory metrics are present (heap and non-heap)
        assertThat(meterRegistry.find("jvm.memory.used").gauge())
            .as("JVM memory used metric should be present")
            .isNotNull();
        
        assertThat(meterRegistry.find("jvm.memory.committed").gauge())
            .as("JVM memory committed metric should be present")
            .isNotNull();
        
        assertThat(meterRegistry.find("jvm.memory.max").gauge())
            .as("JVM memory max metric should be present")
            .isNotNull();
    }

    @Test
    void jvmGcMetrics_shouldBeRegistered() {
        // Act
        var jvmGcMetrics = metricsConfiguration.jvmGcMetrics();
        jvmGcMetrics.bindTo(meterRegistry);

        // Assert
        assertThat(jvmGcMetrics).isNotNull();
        
        // Verify GC metrics are present
        assertThat(meterRegistry.find("jvm.gc.memory.allocated").counter())
            .as("JVM GC memory allocated metric should be present")
            .isNotNull();
    }

    @Test
    void processorMetrics_shouldBeRegistered() {
        // Act
        var processorMetrics = metricsConfiguration.processorMetrics();
        processorMetrics.bindTo(meterRegistry);

        // Assert
        assertThat(processorMetrics).isNotNull();
        
        // Verify CPU metrics are present
        assertThat(meterRegistry.find("system.cpu.usage").gauge())
            .as("System CPU usage metric should be present")
            .isNotNull();
        
        assertThat(meterRegistry.find("system.cpu.count").gauge())
            .as("System CPU count metric should be present")
            .isNotNull();
    }

    @Test
    void jvmThreadMetrics_shouldBeRegistered() {
        // Act
        var jvmThreadMetrics = metricsConfiguration.jvmThreadMetrics();
        jvmThreadMetrics.bindTo(meterRegistry);

        // Assert
        assertThat(jvmThreadMetrics).isNotNull();
        
        // Verify thread metrics are present
        assertThat(meterRegistry.find("jvm.threads.live").gauge())
            .as("JVM threads live metric should be present")
            .isNotNull();
        
        assertThat(meterRegistry.find("jvm.threads.daemon").gauge())
            .as("JVM threads daemon metric should be present")
            .isNotNull();
        
        assertThat(meterRegistry.find("jvm.threads.peak").gauge())
            .as("JVM threads peak metric should be present")
            .isNotNull();
    }

    @Test
    void allJvmMetrics_shouldBePresentInRegistry() {
        // Arrange
        var classLoaderMetrics = metricsConfiguration.classLoaderMetrics();
        var jvmMemoryMetrics = metricsConfiguration.jvmMemoryMetrics();
        var jvmGcMetrics = metricsConfiguration.jvmGcMetrics();
        var processorMetrics = metricsConfiguration.processorMetrics();
        var jvmThreadMetrics = metricsConfiguration.jvmThreadMetrics();

        // Act - Bind all metrics
        classLoaderMetrics.bindTo(meterRegistry);
        jvmMemoryMetrics.bindTo(meterRegistry);
        jvmGcMetrics.bindTo(meterRegistry);
        processorMetrics.bindTo(meterRegistry);
        jvmThreadMetrics.bindTo(meterRegistry);

        // Assert - Verify all JVM metric categories are present
        List<Meter> allMeters = meterRegistry.getMeters();
        
        List<String> metricNames = allMeters.stream()
            .map(meter -> meter.getId().getName())
            .collect(Collectors.toList());

        // CPU metrics (Requirement 3.1)
        assertThat(metricNames)
            .as("CPU metrics should be present")
            .anyMatch(name -> name.startsWith("system.cpu"));

        // Memory metrics (Requirement 3.2)
        assertThat(metricNames)
            .as("Memory metrics should be present")
            .anyMatch(name -> name.startsWith("jvm.memory"));

        // GC metrics (Requirement 3.3)
        assertThat(metricNames)
            .as("GC metrics should be present")
            .anyMatch(name -> name.startsWith("jvm.gc"));

        // Thread metrics (Requirement 3.4)
        assertThat(metricNames)
            .as("Thread metrics should be present")
            .anyMatch(name -> name.startsWith("jvm.threads"));

        // Class loader metrics (Requirement 3.5)
        assertThat(metricNames)
            .as("Class loader metrics should be present")
            .anyMatch(name -> name.startsWith("jvm.classes"));
    }
}
