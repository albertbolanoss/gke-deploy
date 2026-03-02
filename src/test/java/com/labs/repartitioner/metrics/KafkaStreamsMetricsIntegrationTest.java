package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify Kafka Streams metrics are properly exposed through Actuator endpoints.
 * This test validates checkpoint 7: Verificar métricas de Kafka Streams localmente.
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"uppercase", "uppercase-repartition"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.enabled=true",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.streams.auto-startup=true",
    "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
    "management.metrics.export.prometheus.enabled=true"
})
class KafkaStreamsMetricsIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldRegisterJvmMetrics() {
        // Given: Wait a moment for metrics to be registered
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Checking JVM metrics in the registry
        List<String> jvmMetrics = meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .filter(name -> name.startsWith("jvm."))
            .collect(Collectors.toList());

        // Then: JVM metrics should be present
        assertThat(jvmMetrics).isNotEmpty();
        assertThat(jvmMetrics).anyMatch(name -> name.contains("memory"));
        assertThat(jvmMetrics).anyMatch(name -> name.contains("threads"));
    }

    @Test
    void shouldRegisterKafkaStreamsMetrics() {
        // Given: Wait a moment for Kafka Streams to initialize and register metrics
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Checking Kafka Streams metrics in the registry
        List<String> kafkaMetrics = meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .filter(name -> name.contains("kafka"))
            .collect(Collectors.toList());

        // Then: Kafka Streams metrics should be present
        assertThat(kafkaMetrics).isNotEmpty();
    }

    @Test
    void shouldIncludeCommonTagsInMetrics() {
        // Given: Wait a moment for metrics to be registered
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Checking metrics in the registry
        List<Meter> metersWithCommonTags = meterRegistry.getMeters().stream()
            .filter(meter -> {
                List<String> tagKeys = meter.getId().getTags().stream()
                    .map(Tag::getKey)
                    .collect(Collectors.toList());
                return tagKeys.contains("application") && 
                       tagKeys.contains("environment") && 
                       tagKeys.contains("host");
            })
            .collect(Collectors.toList());

        // Then: At least some metrics should have common tags
        assertThat(metersWithCommonTags).isNotEmpty();
    }

    @Test
    void shouldIncludeApplicationTag() {
        // Given: Wait a moment for metrics to be registered
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Checking metrics with application tag
        List<Meter> metersWithAppTag = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getTags().stream()
                .anyMatch(tag -> tag.getKey().equals("application") && 
                                tag.getValue().equals("LABS_GKE_DEPLOY")))
            .collect(Collectors.toList());

        // Then: Metrics should include application tag
        assertThat(metersWithAppTag).isNotEmpty();
    }

    @Test
    void shouldIncludeEnvironmentTag() {
        // Given: Wait a moment for metrics to be registered
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Checking metrics with environment tag
        List<Meter> metersWithEnvTag = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getTags().stream()
                .anyMatch(tag -> tag.getKey().equals("environment") && 
                                tag.getValue().equals("local")))
            .collect(Collectors.toList());

        // Then: Metrics should include environment tag
        assertThat(metersWithEnvTag).isNotEmpty();
    }
}
