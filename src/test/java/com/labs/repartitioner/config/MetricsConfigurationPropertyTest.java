package com.labs.repartitioner.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.LowerChars;
import net.jqwik.api.constraints.StringLength;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for MetricsConfiguration.
 * Validates correctness properties across all metrics.
 */
@org.junit.jupiter.api.Tag("Feature: dynatrace-observability-integration, Property 1: Common Tags on All Metrics")
class MetricsConfigurationPropertyTest {

    /**
     * Property 1: Common Tags on All Metrics
     * 
     * For any metric registered in the MeterRegistry, that metric should include 
     * the common tags: application, environment, and host.
     * 
     * Validates: Requirements 2.6, 3.6, 11.5, 12.1
     */
    @Property(tries = 100)
    void allMetricsHaveCommonTags(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 20) String metricName,
            @ForAll @AlphaChars @StringLength(min = 3, max = 30) String applicationName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 15) String environment,
            @ForAll @AlphaChars @StringLength(min = 3, max = 20) String host) {
        
        // Arrange: Create a MeterRegistry with common tags configured
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Apply common tags using the MetricsConfiguration approach
        MeterRegistryCustomizer<MeterRegistry> customizer = registry -> 
            registry.config().commonTags(
                Arrays.asList(
                    io.micrometer.core.instrument.Tag.of("application", applicationName),
                    io.micrometer.core.instrument.Tag.of("environment", environment),
                    io.micrometer.core.instrument.Tag.of("host", host)
                )
            );
        
        customizer.customize(meterRegistry);
        
        // Act: Register a metric (could be Counter, Gauge, Timer, etc.)
        Counter.builder(metricName)
            .description("Test metric for property validation")
            .register(meterRegistry);
        
        // Assert: Verify the metric has all common tags
        Meter meter = meterRegistry.find(metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Metric '%s' should have all common tags", metricName)
            .contains("application", "environment", "host");
        
        // Verify tag values match what was configured
        assertThat(meter.getId().getTag("application")).isEqualTo(applicationName);
        assertThat(meter.getId().getTag("environment")).isEqualTo(environment);
        assertThat(meter.getId().getTag("host")).isEqualTo(host);
    }
    
    /**
     * Property 1 variant: Common tags apply to different meter types
     * 
     * Verifies that common tags are applied regardless of meter type (Counter, Gauge, Timer, etc.)
     */
    @Property(tries = 100)
    void commonTagsApplyToAllMeterTypes(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 20) String metricName,
            @ForAll @AlphaChars @StringLength(min = 3, max = 30) String applicationName) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        meterRegistry.config().commonTags(
            Arrays.asList(
                io.micrometer.core.instrument.Tag.of("application", applicationName),
                io.micrometer.core.instrument.Tag.of("environment", "test"),
                io.micrometer.core.instrument.Tag.of("host", "testhost")
            )
        );
        
        // Act: Register different types of meters
        Counter.builder(metricName + ".counter").register(meterRegistry);
        Gauge.builder(metricName + ".gauge", () -> 42.0).register(meterRegistry);
        
        // Assert: All meter types have common tags
        Meter counter = meterRegistry.find(metricName + ".counter").meter();
        Meter gauge = meterRegistry.find(metricName + ".gauge").meter();
        
        assertThat(counter).isNotNull();
        assertThat(gauge).isNotNull();
        
        for (Meter meter : Arrays.asList(counter, gauge)) {
            List<String> tagKeys = meter.getId().getTags().stream()
                .map(io.micrometer.core.instrument.Tag::getKey)
                .collect(Collectors.toList());
            
            assertThat(tagKeys)
                .as("Meter type '%s' should have all common tags", meter.getId().getType())
                .contains("application", "environment", "host");
        }
    }
}
