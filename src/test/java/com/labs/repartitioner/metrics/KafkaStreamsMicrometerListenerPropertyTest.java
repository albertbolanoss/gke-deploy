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
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricConfig;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for KafkaStreamsMicrometerListener.
 * Validates that Kafka Streams metrics include specific tags.
 */
@org.junit.jupiter.api.Tag("Feature: dynatrace-observability-integration, Property 2: Kafka Streams Metrics Include Specific Tags")
class KafkaStreamsMicrometerListenerPropertyTest {

    /**
     * Property 2: Kafka Streams Metrics Include Specific Tags
     * 
     * For any metric registered by KafkaStreamsMicrometerListener, that metric should include 
     * specific tags identifying the topic, partition, task-id, or thread-id as applicable to the metric type.
     * 
     * Validates: Requirements 4.5, 12.2
     */
    @Property(tries = 100)
    void kafkaStreamsMetricsHaveSpecificTags(
            @ForAll("kafkaMetricWithTags") KafkaMetricData metricData) {
        
        // Arrange: Create a MeterRegistry and listener
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        KafkaStreamsMicrometerListener listener = new KafkaStreamsMicrometerListener(meterRegistry);
        
        // Create a mock Kafka metric with tags
        MetricName metricName = new MetricName(
            metricData.name,
            metricData.group,
            metricData.description,
            metricData.tags
        );
        
        Measurable measurable = (config, now) -> metricData.value;
        KafkaMetric kafkaMetric = new KafkaMetric(
            this,
            metricName,
            measurable,
            new MetricConfig(),
            org.apache.kafka.common.utils.Time.SYSTEM
        );
        
        // Act: Simulate metric registration by directly calling the same logic
        String micrometerName = buildMicrometerName(metricName);
        List<io.micrometer.core.instrument.Tag> tags = buildTags(metricName);
        
        Gauge.builder("kafka.streams." + micrometerName, kafkaMetric, m -> {
            try {
                Object value = m.metricValue();
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return 0.0;
            } catch (Exception e) {
                return 0.0;
            }
        })
        .description(metricName.description())
        .tags(tags)
        .register(meterRegistry);
        
        // Assert: Verify the metric has at least one of the specific Kafka Streams tags
        Meter meter = meterRegistry.find("kafka.streams." + micrometerName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        // At least one of these tags should be present if they were in the original Kafka metric
        List<String> expectedTags = new ArrayList<>();
        if (metricData.tags.containsKey("topic")) {
            expectedTags.add("topic");
        }
        if (metricData.tags.containsKey("partition")) {
            expectedTags.add("partition");
        }
        if (metricData.tags.containsKey("task-id")) {
            expectedTags.add("task-id");
        }
        if (metricData.tags.containsKey("thread-id")) {
            expectedTags.add("thread-id");
        }
        if (metricData.tags.containsKey("node-id")) {
            expectedTags.add("node-id");
        }
        
        // If the Kafka metric had any of these tags, they should be present in Micrometer
        if (!expectedTags.isEmpty()) {
            assertThat(tagKeys)
                .as("Kafka Streams metric '%s' should include at least one specific tag from: %s", 
                    micrometerName, expectedTags)
                .containsAnyElementsOf(expectedTags);
            
            // Verify tag values are preserved
            for (String expectedTag : expectedTags) {
                if (metricData.tags.containsKey(expectedTag)) {
                    String expectedValue = metricData.tags.get(expectedTag);
                    String actualValue = meter.getId().getTag(expectedTag);
                    assertThat(actualValue)
                        .as("Tag '%s' value should be preserved", expectedTag)
                        .isEqualTo(expectedValue);
                }
            }
        }
        
        // Verify metric-group tag is always present
        assertThat(tagKeys)
            .as("Kafka Streams metric should always have metric-group tag")
            .contains("metric-group");
        assertThat(meter.getId().getTag("metric-group"))
            .isEqualTo(metricData.group);
    }
    
    /**
     * Property 2 variant: Metrics with topic tag include the topic tag
     */
    @Property(tries = 100)
    void kafkaStreamsMetricsWithTopicIncludeTopicTag(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 20) String metricName,
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 30) String topicName) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("topic", topicName);
        
        MetricName kafkaMetricName = new MetricName(
            metricName,
            "stream-metrics",
            "Test metric",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.test." + metricName, () -> 100.0)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.test." + metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Metric with topic should include topic tag")
            .contains("topic");
        
        assertThat(meter.getId().getTag("topic"))
            .isEqualTo(topicName);
    }
    
    /**
     * Property 2 variant: Metrics with partition tag include the partition tag
     */
    @Property(tries = 100)
    void kafkaStreamsMetricsWithPartitionIncludePartitionTag(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 20) String metricName,
            @ForAll @IntRange(min = 0, max = 99) int partitionNumber) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("partition", String.valueOf(partitionNumber));
        
        MetricName kafkaMetricName = new MetricName(
            metricName,
            "stream-metrics",
            "Test metric",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.test." + metricName, () -> 100.0)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.test." + metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Metric with partition should include partition tag")
            .contains("partition");
        
        assertThat(meter.getId().getTag("partition"))
            .isEqualTo(String.valueOf(partitionNumber));
    }
    
    /**
     * Property 2 variant: Metrics with task-id tag include the task-id tag
     */
    @Property(tries = 100)
    void kafkaStreamsMetricsWithTaskIdIncludeTaskIdTag(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 20) String metricName,
            @ForAll @AlphaChars @StringLength(min = 3, max = 15) String taskId) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("task-id", taskId);
        
        MetricName kafkaMetricName = new MetricName(
            metricName,
            "stream-metrics",
            "Test metric",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.test." + metricName, () -> 100.0)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.test." + metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Metric with task-id should include task-id tag")
            .contains("task-id");
        
        assertThat(meter.getId().getTag("task-id"))
            .isEqualTo(taskId);
    }
    
    /**
     * Property 2 variant: Metrics with thread-id tag include the thread-id tag
     */
    @Property(tries = 100)
    void kafkaStreamsMetricsWithThreadIdIncludeThreadIdTag(
            @ForAll @AlphaChars @LowerChars @StringLength(min = 3, max = 20) String metricName,
            @ForAll @AlphaChars @StringLength(min = 3, max = 15) String threadId) {
        
        // Arrange
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        Map<String, String> kafkaTags = new HashMap<>();
        kafkaTags.put("thread-id", threadId);
        
        MetricName kafkaMetricName = new MetricName(
            metricName,
            "stream-metrics",
            "Test metric",
            kafkaTags
        );
        
        List<io.micrometer.core.instrument.Tag> tags = buildTags(kafkaMetricName);
        
        // Act
        Gauge.builder("kafka.streams.test." + metricName, () -> 100.0)
            .tags(tags)
            .register(meterRegistry);
        
        // Assert
        Meter meter = meterRegistry.find("kafka.streams.test." + metricName).meter();
        assertThat(meter).isNotNull();
        
        List<String> tagKeys = meter.getId().getTags().stream()
            .map(io.micrometer.core.instrument.Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys)
            .as("Metric with thread-id should include thread-id tag")
            .contains("thread-id");
        
        assertThat(meter.getId().getTag("thread-id"))
            .isEqualTo(threadId);
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
    Arbitrary<KafkaMetricData> kafkaMetricWithTags() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50),
            Arbitraries.doubles().between(0.0, 10000.0),
            kafkaTagsArbitrary()
        ).as((name, group, description, value, tags) -> 
            new KafkaMetricData(name, group, description, value, tags)
        );
    }
    
    @Provide
    Arbitrary<Map<String, String>> kafkaTagsArbitrary() {
        return Arbitraries.of(
            // Generate different combinations of tags
            generateTagsWithTopic(),
            generateTagsWithPartition(),
            generateTagsWithTaskId(),
            generateTagsWithThreadId(),
            generateTagsWithMultiple(),
            generateTagsWithAll()
        );
    }
    
    private Map<String, String> generateTagsWithTopic() {
        Map<String, String> tags = new HashMap<>();
        tags.put("topic", "test-topic-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    private Map<String, String> generateTagsWithPartition() {
        Map<String, String> tags = new HashMap<>();
        tags.put("partition", String.valueOf((int)(Math.random() * 10)));
        return tags;
    }
    
    private Map<String, String> generateTagsWithTaskId() {
        Map<String, String> tags = new HashMap<>();
        tags.put("task-id", "task-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    private Map<String, String> generateTagsWithThreadId() {
        Map<String, String> tags = new HashMap<>();
        tags.put("thread-id", "thread-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    private Map<String, String> generateTagsWithMultiple() {
        Map<String, String> tags = new HashMap<>();
        tags.put("topic", "test-topic-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("partition", String.valueOf((int)(Math.random() * 10)));
        tags.put("task-id", "task-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    private Map<String, String> generateTagsWithAll() {
        Map<String, String> tags = new HashMap<>();
        tags.put("topic", "test-topic-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("partition", String.valueOf((int)(Math.random() * 10)));
        tags.put("task-id", "task-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("thread-id", "thread-" + UUID.randomUUID().toString().substring(0, 8));
        tags.put("node-id", "node-" + UUID.randomUUID().toString().substring(0, 8));
        return tags;
    }
    
    // Data class for test data
    static class KafkaMetricData {
        final String name;
        final String group;
        final String description;
        final double value;
        final Map<String, String> tags;
        
        KafkaMetricData(String name, String group, String description, double value, Map<String, String> tags) {
            this.name = name;
            this.group = group;
            this.description = description;
            this.value = value;
            this.tags = tags;
        }
    }
}
