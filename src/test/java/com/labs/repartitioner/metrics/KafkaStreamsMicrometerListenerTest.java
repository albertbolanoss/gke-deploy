package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaStreamsMicrometerListener.
 * Verifies that the listener registers correctly, captures metrics,
 * and handles errors gracefully.
 * 
 * Requirements: 4.1, 4.2, 4.3, 4.4, 13.3
 */
class KafkaStreamsMicrometerListenerTest {

    private MeterRegistry meterRegistry;
    private KafkaStreamsMicrometerListener listener;

    @Mock
    private KafkaStreams kafkaStreams;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        listener = new KafkaStreamsMicrometerListener(meterRegistry);
    }

    // Test: Listener Registration - Requirement 11.2

    @Test
    void constructor_shouldInitializeListener() {
        // Assert
        assertThat(listener).isNotNull();
    }

    @Test
    void setKafkaStreams_shouldSetKafkaStreamsInstance() {
        // Act
        listener.setKafkaStreams(kafkaStreams);

        // Assert - Verify no exception is thrown and listener is ready
        assertThat(listener).isNotNull();
    }

    @Test
    void onChange_shouldRegisterMetricsWhenStateBecomesRunning() {
        // Arrange
        Map<MetricName, ? extends Metric> mockMetrics = createMockMetrics();
        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        verify(kafkaStreams, times(1)).metrics();
        
        // Verify metrics were registered
        List<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).isNotEmpty();
    }

    @Test
    void onChange_shouldNotRegisterMetricsWhenStateIsNotRunning() {
        // Arrange
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.REBALANCING, KafkaStreams.State.RUNNING);

        // Assert
        verify(kafkaStreams, never()).metrics();
        
        List<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).isEmpty();
    }

    @Test
    void onChange_shouldNotFailWhenKafkaStreamsIsNull() {
        // Act & Assert - Should not throw exception
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);
        
        // Verify no metrics were registered
        List<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).isEmpty();
    }

    // Test: Throughput Metrics - Requirements 4.1, 4.2

    @Test
    void onChange_shouldRegisterThroughputMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        
        // Add throughput metrics
        mockMetrics.put(
            createMetricName("stream-metrics", "records-consumed-rate", Map.of("topic", "test-topic")),
            createMockMetric(1500.5)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "records-produced-rate", Map.of("topic", "output-topic")),
            createMockMetric(1450.3)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "bytes-consumed-rate", Map.of("topic", "test-topic")),
            createMockMetric(45678.2)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "bytes-produced-rate", Map.of("topic", "output-topic")),
            createMockMetric(43210.1)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.records.consumed.rate").gauge())
            .as("Records consumed rate metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.records.produced.rate").gauge())
            .as("Records produced rate metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.bytes.consumed.rate").gauge())
            .as("Bytes consumed rate metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.bytes.produced.rate").gauge())
            .as("Bytes produced rate metric should be registered")
            .isNotNull();
    }

    // Test: Latency Metrics - Requirement 4.3

    @Test
    void onChange_shouldRegisterLatencyMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        
        // Add latency metrics
        mockMetrics.put(
            createMetricName("stream-metrics", "process-latency-avg", Map.of("task-id", "0_0")),
            createMockMetric(25.5)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "process-latency-max", Map.of("task-id", "0_0")),
            createMockMetric(150.0)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "commit-latency-avg", Map.of("task-id", "0_0")),
            createMockMetric(10.2)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "commit-latency-max", Map.of("task-id", "0_0")),
            createMockMetric(50.0)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.process.latency.avg").gauge())
            .as("Process latency avg metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.process.latency.max").gauge())
            .as("Process latency max metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.commit.latency.avg").gauge())
            .as("Commit latency avg metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.commit.latency.max").gauge())
            .as("Commit latency max metric should be registered")
            .isNotNull();
    }

    // Test: Consumer Lag Metrics - Requirement 4.4

    @Test
    void onChange_shouldRegisterConsumerLagMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        
        // Add consumer lag metrics
        mockMetrics.put(
            createMetricName("stream-metrics", "records-lag", Map.of("partition", "0")),
            createMockMetric(100.0)
        );
        mockMetrics.put(
            createMetricName("stream-metrics", "records-lag-max", Map.of("partition", "0")),
            createMockMetric(500.0)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.records.lag").gauge())
            .as("Records lag metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.records.lag.max").gauge())
            .as("Records lag max metric should be registered")
            .isNotNull();
    }

    // Test: Metric Tags - Requirement 4.5

    @Test
    void onChange_shouldIncludeTopicTagInMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        mockMetrics.put(
            createMetricName("stream-metrics", "records-consumed-rate", Map.of("topic", "test-topic")),
            createMockMetric(1500.5)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        Gauge gauge = meterRegistry.find("kafka.streams.stream.metrics.records.consumed.rate").gauge();
        assertThat(gauge).isNotNull();
        
        List<String> tagKeys = gauge.getId().getTags().stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys).contains("topic");
        assertThat(gauge.getId().getTag("topic")).isEqualTo("test-topic");
    }

    @Test
    void onChange_shouldIncludePartitionTagInMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        mockMetrics.put(
            createMetricName("stream-metrics", "records-lag", Map.of("partition", "2")),
            createMockMetric(100.0)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        Gauge gauge = meterRegistry.find("kafka.streams.stream.metrics.records.lag").gauge();
        assertThat(gauge).isNotNull();
        
        List<String> tagKeys = gauge.getId().getTags().stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys).contains("partition");
        assertThat(gauge.getId().getTag("partition")).isEqualTo("2");
    }

    @Test
    void onChange_shouldIncludeTaskIdTagInMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        mockMetrics.put(
            createMetricName("stream-metrics", "process-latency-avg", Map.of("task-id", "0_1")),
            createMockMetric(25.5)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        Gauge gauge = meterRegistry.find("kafka.streams.stream.metrics.process.latency.avg").gauge();
        assertThat(gauge).isNotNull();
        
        List<String> tagKeys = gauge.getId().getTags().stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys).contains("task-id");
        assertThat(gauge.getId().getTag("task-id")).isEqualTo("0_1");
    }

    @Test
    void onChange_shouldIncludeThreadIdTagInMetrics() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        mockMetrics.put(
            createMetricName("stream-thread-metrics", "commit-latency-avg", Map.of("thread-id", "stream-thread-1")),
            createMockMetric(10.2)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert
        Gauge gauge = meterRegistry.find("kafka.streams.stream.thread.metrics.commit.latency.avg").gauge();
        assertThat(gauge).isNotNull();
        
        List<String> tagKeys = gauge.getId().getTags().stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        
        assertThat(tagKeys).contains("thread-id");
        assertThat(gauge.getId().getTag("thread-id")).isEqualTo("stream-thread-1");
    }

    // Test: Error Handling - Requirement 13.3

    @Test
    void onChange_shouldContinueWhenMetricRegistrationFails() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        
        // Add a valid metric
        mockMetrics.put(
            createMetricName("stream-metrics", "records-consumed-rate", Map.of("topic", "test-topic")),
            createMockMetric(1500.5)
        );
        
        // Add a metric that will cause an error (null value)
        Metric faultyMetric = mock(Metric.class);
        when(faultyMetric.metricValue()).thenThrow(new RuntimeException("Metric not available"));
        mockMetrics.put(
            createMetricName("stream-metrics", "faulty-metric", Map.of()),
            faultyMetric
        );
        
        // Add another valid metric after the faulty one
        mockMetrics.put(
            createMetricName("stream-metrics", "records-produced-rate", Map.of("topic", "output-topic")),
            createMockMetric(1450.3)
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act - Should not throw exception
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert - Valid metrics should still be registered
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.records.consumed.rate").gauge())
            .as("Valid metric before faulty metric should be registered")
            .isNotNull();
        
        assertThat(meterRegistry.find("kafka.streams.stream.metrics.records.produced.rate").gauge())
            .as("Valid metric after faulty metric should be registered")
            .isNotNull();
    }

    @Test
    void onChange_shouldHandleExceptionWhenRetrievingMetrics() {
        // Arrange
        when(kafkaStreams.metrics()).thenThrow(new RuntimeException("Failed to retrieve metrics"));
        listener.setKafkaStreams(kafkaStreams);

        // Act & Assert - Should not throw exception
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);
        
        // Verify no metrics were registered
        List<Meter> meters = meterRegistry.getMeters();
        assertThat(meters).isEmpty();
    }

    @Test
    void onChange_shouldHandleNullMetricValue() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        
        Metric nullMetric = mock(Metric.class);
        when(nullMetric.metricValue()).thenReturn(null);
        mockMetrics.put(
            createMetricName("stream-metrics", "null-metric", Map.of()),
            nullMetric
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act - Should not throw exception
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert - Metric should be registered with default value
        Gauge gauge = meterRegistry.find("kafka.streams.stream.metrics.null.metric").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    void onChange_shouldHandleNonNumericMetricValue() {
        // Arrange
        Map<MetricName, Metric> mockMetrics = new HashMap<>();
        
        Metric stringMetric = mock(Metric.class);
        when(stringMetric.metricValue()).thenReturn("not-a-number");
        mockMetrics.put(
            createMetricName("stream-metrics", "string-metric", Map.of()),
            stringMetric
        );

        when(kafkaStreams.metrics()).thenAnswer(invocation -> mockMetrics);
        listener.setKafkaStreams(kafkaStreams);

        // Act - Should not throw exception
        listener.onChange(KafkaStreams.State.RUNNING, KafkaStreams.State.REBALANCING);

        // Assert - Metric should be registered with default value
        Gauge gauge = meterRegistry.find("kafka.streams.stream.metrics.string.metric").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    // Helper Methods

    private Map<MetricName, Metric> createMockMetrics() {
        Map<MetricName, Metric> metrics = new HashMap<>();
        
        // Add sample metrics
        metrics.put(
            createMetricName("stream-metrics", "records-consumed-rate", Map.of("topic", "test-topic")),
            createMockMetric(1500.5)
        );
        metrics.put(
            createMetricName("stream-metrics", "process-latency-avg", Map.of("task-id", "0_0")),
            createMockMetric(25.5)
        );
        
        return metrics;
    }

    private MetricName createMetricName(String group, String name, Map<String, String> tags) {
        return new MetricName(name, group, "Test metric description", tags);
    }

    private Metric createMockMetric(double value) {
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn(value);
        return metric;
    }
}
