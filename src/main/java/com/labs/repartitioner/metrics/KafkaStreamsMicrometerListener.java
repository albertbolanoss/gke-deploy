package com.labs.repartitioner.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.streams.KafkaStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listener that captures native Kafka Streams metrics and registers them in Micrometer.
 * Implements KafkaStreams.StateListener to detect state changes and trigger metric registration.
 * 
 * This listener captures:
 * - Throughput metrics (records-consumed-rate, records-produced-rate, bytes-consumed-rate, bytes-produced-rate)
 * - Latency metrics (process-latency-avg, process-latency-max, commit-latency-avg, commit-latency-max)
 * - Consumer lag metrics (records-lag, records-lag-max)
 * - Thread state metrics (thread-state)
 * - Task metrics (active-tasks, standby-tasks)
 * - Repartitioning metrics (repartition-records-sent, repartition-records-dropped)
 */
public class KafkaStreamsMicrometerListener implements KafkaStreams.StateListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsMicrometerListener.class);
    
    private final MeterRegistry meterRegistry;
    private KafkaStreams kafkaStreams;

    private Set<String> criticalMetrics = Set.of(
            // Thread metrics
            "commit-latency-avg", "commit-latency-max", "commit-rate",
            "process-latency-avg", "process-latency-max", "process-rate",
            "poll-latency-avg", "poll-rate",

            // Consumer metrics
            "records-consumed-rate", "bytes-consumed-rate",
            "records-lag", "records-lag-max",
            "fetch-latency-avg", "fetch-latency-max",

            // Producer metrics
            "record-send-rate", "byte-rate",
            "record-error-rate", "record-retry-rate",

            // Task metrics
            "task-created-rate", "task-closed-rate"
    );


    /**
     * Creates a new KafkaStreamsMicrometerListener.
     *
     * @param meterRegistry the Micrometer registry to register metrics
     */
    public KafkaStreamsMicrometerListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("KafkaStreamsMicrometerListener initialized");
    }

    /**
     * Sets the KafkaStreams instance to monitor.
     * This should be called after KafkaStreams is initialized.
     *
     * @param kafkaStreams the KafkaStreams instance
     */
    public void setKafkaStreams(KafkaStreams kafkaStreams) {
        this.kafkaStreams = kafkaStreams;
        log.info("KafkaStreams instance set in listener");
    }

    /**
     * Called when the Kafka Streams state changes.
     * Triggers metric registration when the state becomes RUNNING.
     *
     * @param newState the new state
     * @param oldState the previous state
     */
    @Override
    public void onChange(KafkaStreams.State newState, KafkaStreams.State oldState) {
        log.info("Kafka Streams state changed from {} to {}", oldState, newState);
        
        if (newState == KafkaStreams.State.RUNNING && kafkaStreams != null) {
            try {
                registerKafkaStreamsMetrics();
                log.info("Kafka Streams metrics registered successfully");
            } catch (Exception e) {
                log.error("Failed to register Kafka Streams metrics", e);
            }
        }
    }


    private void registerKafkaStreamsMetrics() {
        if (kafkaStreams == null) {
            log.warn("KafkaStreams instance is null, cannot register metrics");
            return;
        }

        try {
            Map<MetricName, ? extends Metric> metrics = kafkaStreams.metrics();
            log.info("Found {} total Kafka Streams metrics", metrics.size());


            int registeredCount = 0;
            int matchedCount = 0;

//            // First pass: log all available metric names to understand what we have
//            log.info("=== Available Kafka Streams Metric Names (first 20) ===");
//            int count = 0;
//            for (MetricName metricName : metrics.keySet()) {
//                if (count++ < 20) {
//                    log.info("Metric: name='{}', group='{}', tags={}",
//                            metricName.name(), metricName.group(), metricName.tags());
//                }
//            }

            // Second pass: register critical metrics
            for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
                MetricName metricName = entry.getKey();

                if (CollectionUtils.isEmpty(criticalMetrics) || criticalMetrics.contains(metricName.name())) {
                    matchedCount++;
                    try {
                        registerMetricAsGauge(metricName, entry.getValue());
                        registeredCount++;
                        log.info("Registered critical metric: {}", metricName.name());
                    } catch (Exception e) {
                        log.warn("Failed to register metric: {}", metricName.name(), e);
                    }
                }
            }

            log.info("Registered {} out of {} matched critical metrics (total available: {})", 
                    registeredCount, matchedCount, metrics.size());

        } catch (Exception e) {
            log.error("Failed to retrieve Kafka Streams metrics", e);
        }
    }


    /**
     * Registers a single Kafka Streams metric as a Micrometer gauge.
     * Converts Kafka metric tags to Micrometer tags and creates a gauge that reads the metric value.
     *
     * @param metricName the Kafka metric name with metadata
     * @param metric the Kafka metric object
     */
    private void registerMetricAsGauge(MetricName metricName, Metric metric) {
        try {
            // Build Micrometer metric name with kafka.streams prefix
            String micrometerName = buildMicrometerName(metricName);
            
            // Convert Kafka tags to Micrometer tags
            List<Tag> tags = buildTags(metricName);
            
            // Register as gauge
            Gauge.builder("kafka.streams." + micrometerName, metric, m -> {
                try {
                    Object value = m.metricValue();
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    return 0.0;
                } catch (Exception e) {
                    log.debug("Failed to read metric value for {}: {}", micrometerName, e.getMessage());
                    return 0.0;
                }
            })
            .description(metricName.description())
            .tags(tags)
            .register(meterRegistry);
            
            log.debug("Registered metric: kafka.streams.{} with tags: {}", micrometerName, tags);
        } catch (Exception e) {
            log.warn("Failed to register gauge for metric: {}", metricName.name(), e);
        }
    }

    /**
     * Builds a Micrometer-compatible metric name from a Kafka MetricName.
     * Combines group and name with dots as separators.
     *
     * @param metricName the Kafka metric name
     * @return the Micrometer metric name
     */
    private String buildMicrometerName(MetricName metricName) {
        String group = metricName.group();
        String name = metricName.name();
        
        // Normalize to lowercase with dots
        String normalizedGroup = group.replace("-", ".");
        String normalizedName = name.replace("-", ".");
        
        return normalizedGroup + "." + normalizedName;
    }

    /**
     * Builds Micrometer tags from Kafka metric tags.
     * Includes relevant tags like topic, partition, task-id, thread-id, node-id.
     *
     * @param metricName the Kafka metric name with tags
     * @return list of Micrometer tags
     */
    private List<Tag> buildTags(MetricName metricName) {
        List<Tag> tags = new ArrayList<>();
        
        Map<String, String> kafkaTags = metricName.tags();
        
        // Add relevant tags for filtering and grouping
        if (kafkaTags.containsKey("topic")) {
            tags.add(Tag.of("topic", kafkaTags.get("topic")));
        }
        
        if (kafkaTags.containsKey("partition")) {
            tags.add(Tag.of("partition", kafkaTags.get("partition")));
        }
        
        if (kafkaTags.containsKey("task-id")) {
            tags.add(Tag.of("task-id", kafkaTags.get("task-id")));
        }
        
        if (kafkaTags.containsKey("thread-id")) {
            tags.add(Tag.of("thread-id", kafkaTags.get("thread-id")));
        }
        
        if (kafkaTags.containsKey("node-id")) {
            tags.add(Tag.of("node-id", kafkaTags.get("node-id")));
        }
        
        if (kafkaTags.containsKey("client-id")) {
            tags.add(Tag.of("client-id", kafkaTags.get("client-id")));
        }
        
        // Add metric group as a tag for easier filtering
        tags.add(Tag.of("metric-group", metricName.group()));
        
        return tags;
    }
}
