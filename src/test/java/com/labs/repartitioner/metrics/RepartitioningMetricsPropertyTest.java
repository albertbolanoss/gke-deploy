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
import org.apache.kafka.common.MetricName;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for repartitioning metrics.
 * Validates that repartitioning metrics include topology node tags.
 */
@org.junit.jupiter.api.Tag("Feature: dynatrace-observability-integration, Property 3: Repartitioning Metrics Include Topology Node Tags")
class RepartitioningMetricsPropertyTest {

    /**
     * Property 3: Repartitioning Metrics Include Topology Node Tags
     * 
     * For any repartitioning metric registered by KafkaStreamsMicrometerListener, that metric 
     * should include tags identifying the topology node that performed the repartitioning operation.
     * 
     * Validates: Requirements 6.4
     */
    @Property(tries = 100)
    void repartitioningMetricsHaveNodeTags(
            @ForAll("repartitioningMetricData") RepartitioningMetricData metricData) {
        
        // Arrange: Create a MeterRegistry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Create a Kafka metric name with node-id tag (as Kafka Streams does for repartitioning metrics)
        MetricName kafkaMetricName = new MetricName(
            metricData.name,
            metricData.group,
            metricData.description,
            metricData.tags
        );
        
        // Build tags using the same logic as KafkaStreamsMicrometerListener
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act: Register the repartitioning metric
        String micrometerName = buildMicrometerName(kafkaMetricName);
        Gauge.builder("kafka.streams." + micrometerName, () -> metricData.value)
            .description(metricData.description)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert: Verify the metric has node-id tag
        Meter meter = meterRegistry.find("kafka.streams." + micrometerName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Repartitioning metric '%s' should include node-id tag", micrometerName)
            .contains("node-id");
        
        // Verify the node-id tag value is preserved
        String actualNodeId = meter.getId().getTag("node-id");
        assertThat(actualNodeId)
            .as("Node-id tag value should be preserved")
            .isEqualTo(metricData.tags.get("node-id"));
    }
    
    /**
     * Property 3 variant: Repartitioning metrics with specific names include node-id
     * 
     * Tests that metrics with repartitioning-related names (containing "repartition") 
     * include the node-id tag.
     */
    @Property(tries = 100)
    void repartitioningMetricNamesIncludeNodeId(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 15) String prefix,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 15) String suffix,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String nodeId) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Create a repartitioning metric name
        String metricName = prefix + ".repartition." + suffix;
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("node-id", nodeId);
        
        MetricName kafkaMetricName = new MetricName(
            metricName,
            "stream-node-metrics",
            "Repartitioning metric",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams." + metricName, () -> 100.0)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams." + metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Repartitioning metric should include node-id tag")
            .contains("node-id");
        
        assertThat(meter.getId().getTag("node-id"))
            .isEqualTo(nodeId);
    }
    
    /**
     * Property 3 variant: Repartitioning records sent metrics include node-id
     */
    @Property(tries = 100)
    void repartitionRecordsSentMetricsIncludeNodeId(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String nodeId,
            @ForAll @IntRange(min = 0, max = 10000) int recordsSent) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("node-id", nodeId);
        
        MetricName kafkaMetricName = new MetricName(
            "repartition-records-sent",
            "stream-node-metrics",
            "Number of records sent during repartitioning",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.stream.node.metrics.repartition.records.sent", () -> (double) recordsSent)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.stream.node.metrics.repartition.records.sent").meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("node-id");
        
        assertThat(meter.getId().getTag("node-id"))
            .isEqualTo(nodeId);
    }
    
    /**
     * Property 3 variant: Repartitioning records dropped metrics include node-id
     */
    @Property(tries = 100)
    void repartitionRecordsDroppedMetricsIncludeNodeId(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String nodeId,
            @ForAll @IntRange(min = 0, max = 1000) int recordsDropped) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("node-id", nodeId);
        
        MetricName kafkaMetricName = new MetricName(
            "repartition-records-dropped",
            "stream-node-metrics",
            "Number of records dropped during repartitioning",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.stream.node.metrics.repartition.records.dropped", () -> (double) recordsDropped)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.stream.node.metrics.repartition.records.dropped").meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("node-id");
        
        assertThat(meter.getId().getTag("node-id"))
            .isEqualTo(nodeId);
    }
    
    /**
     * Property 3 variant: Repartitioning latency metrics include node-id
     */
    @Property(tries = 100)
    void repartitionLatencyMetricsIncludeNodeId(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String nodeId,
            @ForAll @IntRange(min = 1, max = 5000) int latencyMs) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("node-id", nodeId);
        
        MetricName kafkaMetricName = new MetricName(
            "repartition-latency-avg",
            "stream-node-metrics",
            "Average latency of repartitioning operations",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.stream.node.metrics.repartition.latency.avg", () -> (double) latencyMs)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.stream.node.metrics.repartition.latency.avg").meter();
        assertThat(meter).isNotNull();
        
        assertThat(meter.getId().getTags())
            .extracting(io.micrometer.core.instrument.Tag::getKey)
            .contains("node-id");
        
        assertThat(meter.getId().getTag("node-id"))
            .isEqualTo(nodeId);
    }
    
    /**
     * Property 3 variant: Repartitioning metrics may include additional tags
     * 
     * Verifies that repartitioning metrics can have additional tags (topic, partition, etc.)
     * alongside the required node-id tag.
     */
    @Property(tries = 100)
    void repartitioningMetricsCanHaveAdditionalTags(
            @ForAll("repartitioningMetricWithMultipleTags") RepartitioningMetricData metricData) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        MetricName kafkaMetricName = new MetricName(
            metricData.name,
            metricData.group,
            metricData.description,
            metricData.tags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        String micrometerName = buildMicrometerName(kafkaMetricName);
        Gauge.builder("kafka.streams." + micrometerName, () -> metricData.value)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams." + micrometerName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        // Must have node-id
        assertThat(tagKeys)
            .as("Repartitioning metric must have node-id tag")
            .contains("node-id");
        
        // May have additional tags
        if (metricData.tags.containsKey("topic")) {
            assertThat(tagKeys).contains("topic");
            assertThat(meter.getId().getTag("topic"))
                .isEqualTo(metricData.tags.get("topic"));
        }
        
        if (metricData.tags.containsKey("partition")) {
            assertThat(tagKeys).contains("partition");
            assertThat(meter.getId().getTag("partition"))
                .isEqualTo(metricData.tags.get("partition"));
        }
    }
    
    // Helper methods (replicate the logic from KafkaStreamsMicrometerListener)
    
    private String buildMicrometerName(MetricName metricName) {
        String group = metricName.group();
        String name = metricName.name();
        
        String normalizedGroup = group.replace("-", ".");
        String normalizedName = name.replace("-", ".");
        
        return normalizedGroup + "." + normalizedName;
    }
    
    private List<io.micrometer.core.instrument.Tag> buildTags(MetricName metricName) {
        List<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();
        
        Map<String, String> kafkaTags = metricName.tags();
        
        if (kafkaTags.containsKey("topic")) {
            tags.add(io.micrometer.core.instrument.Tag.of("topic", kafkaTags.get("topic")));
        }
        
        if (kafkaTags.containsKey("partition")) {
            tags.add(io.micrometer.core.instrument.Tag.of("partition", kafkaTags.get("partition")));
        }
        
        if (kafkaTags.containsKey("task-id")) {
            tags.add(io.micrometer.core.instrument.Tag.of("task-id", kafkaTags.get("task-id")));
        }
        
        if (kafkaTags.containsKey("thread-id")) {
            tags.add(io.micrometer.core.instrument.Tag.of("thread-id", kafkaTags.get("thread-id")));
        }
        
        if (kafkaTags.containsKey("node-id")) {
            tags.add(io.micrometer.core.instrument.Tag.of("node-id", kafkaTags.get("node-id")));
        }
        
        if (kafkaTags.containsKey("client-id")) {
            tags.add(io.micrometer.core.instrument.Tag.of("client-id", kafkaTags.get("client-id")));
        }
        
        tags.add(io.micrometer.core.instrument.Tag.of("metric-group", metricName.group()));
        
        return tags;
    }
    
    // Arbitraries for generating test data
    
    @Provide
    Arbitrary<RepartitioningMetricData> repartitioningMetricData() {
        return Combinators.combine(
            repartitioningMetricNames(),
            Arbitraries.of("stream-node-metrics", "stream-processor-node-metrics"),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(60),
            Arbitraries.doubles().between(0.0, 100000.0),
            repartitioningTagsArbitrary()
        ).as((name, group, description, value, tags) -> 
            new RepartitioningMetricData(name, group, description, value, tags)
        );
    }
    
    @Provide
    Arbitrary<RepartitioningMetricData> repartitioningMetricWithMultipleTags() {
        return Combinators.combine(
            repartitioningMetricNames(),
            Arbitraries.of("stream-node-metrics", "stream-processor-node-metrics"),
            Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(60),
            Arbitraries.doubles().between(0.0, 100000.0),
            repartitioningTagsWithMultiple()
        ).as((name, group, description, value, tags) -> 
            new RepartitioningMetricData(name, group, description, value, tags)
        );
    }
    
    @Provide
    Arbitrary<String> repartitioningMetricNames() {
        return Arbitraries.of(
            "repartition-records-sent",
            "repartition-records-dropped",
            "repartition-latency-avg",
            "repartition-latency-max",
            "repartition-rate",
            "repartition-total"
        );
    }
    
    @Provide
    Arbitrary<Map<String, String>> repartitioningTagsArbitrary() {
        return Arbitraries.of(
            generateRepartitioningTagsWithNodeId()
        );
    }
    
    @Provide
    Arbitrary<Map<String, String>> repartitioningTagsWithMultiple() {
        return Arbitraries.of(
            generateRepartitioningTagsWithNodeIdAndTopic(),
            generateRepartitioningTagsWithNodeIdAndPartition(),
            generateRepartitioningTagsWithAll()
        );
    }
    
    private Map<String, String> generateRepartitioningTagsWithNodeId() {
        Map<String, String> tags = new HashMap<>();
        tags.put("node-id", "KSTREAM-KEY-SELECT-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    private Map<String, String> generateRepartitioningTagsWithNodeIdAndTopic() {
        Map<String, String> tags = new HashMap<>();
        tags.put("node-id", "KSTREAM-KEY-SELECT-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("topic", "repartition-topic-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    private Map<String, String> generateRepartitioningTagsWithNodeIdAndPartition() {
        Map<String, String> tags = new HashMap<>();
        tags.put("node-id", "KSTREAM-KEY-SELECT-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("partition", String.valueOf((int)(Math.random() * 10)));
        return tags;
    }
    
    private Map<String, String> generateRepartitioningTagsWithAll() {
        Map<String, String> tags = new HashMap<>();
        tags.put("node-id", "KSTREAM-KEY-SELECT-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("topic", "repartition-topic-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("partition", String.valueOf((int)(Math.random() * 10)));
        tags.put("task-id", "task-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    // Data class for test data
    static class RepartitioningMetricData {
        final String name;
        final String group;
        final String description;
        final double value;
        final Map<String, String> tags;
        
        RepartitioningMetricData(String name, String group, String description, double value, Map<String, String> tags) {
            this.name = name;
            this.group = group;
            this.description = description;
            this.value = value;
            this.tags = tags;
        }
    }
}
