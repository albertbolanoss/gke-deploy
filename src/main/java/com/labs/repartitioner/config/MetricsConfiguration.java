package com.labs.repartitioner.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.apache.kafka.streams.KafkaStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import java.util.Arrays;

/**
 * Configuration class for Micrometer metrics.
 * Applies common tags to all metrics for consistent identification in monitoring systems.
 * Registers JVM metrics binders for comprehensive JVM observability.
 * Integrates KafkaStreamsMicrometerListener with Kafka Streams for metrics collection.
 * Enables scheduled metrics collection for RocksDB.
 */
@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class MetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfiguration.class);

    /**
     * Configures common tags that will be applied to all metrics.
     * These tags enable filtering and grouping in monitoring platforms like Dynatrace.
     *
     * @param applicationName the application name from spring.application.name
     * @param environment the environment (dev, staging, production) from ENVIRONMENT variable
     * @param host the hostname from HOSTNAME variable
     * @return MeterRegistryCustomizer that adds common tags to all metrics
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            @Value("${spring.application.name}") String applicationName,
            @Value("${ENVIRONMENT:local}") String environment,
            @Value("${HOSTNAME:localhost}") String host) {

        return registry -> registry.config().commonTags(
                Arrays.asList(
                        Tag.of("application", applicationName),
                        Tag.of("environment", environment),
                        Tag.of("host", host)
                )
        );
    }

    /**
     * Registers ClassLoaderMetrics to monitor JVM class loading.
     * Tracks loaded classes, unloaded classes, and class loader statistics.
     *
     * @param meterRegistry the Micrometer registry to bind metrics to
     * @return ClassLoaderMetrics binder
     */
    @Bean
    public ClassLoaderMetrics classLoaderMetrics(MeterRegistry meterRegistry) {
        ClassLoaderMetrics metrics = new ClassLoaderMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Registers JvmMemoryMetrics to monitor JVM memory usage.
     * Tracks heap and non-heap memory usage, committed memory, and max memory.
     *
     * @param meterRegistry the Micrometer registry to bind metrics to
     * @return JvmMemoryMetrics binder
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics(MeterRegistry meterRegistry) {
        JvmMemoryMetrics metrics = new JvmMemoryMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Registers JvmGcMetrics to monitor garbage collection.
     * Tracks GC count, GC time, and memory pool statistics.
     *
     * @param meterRegistry the Micrometer registry to bind metrics to
     * @return JvmGcMetrics binder
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics(MeterRegistry meterRegistry) {
        JvmGcMetrics metrics = new JvmGcMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Registers ProcessorMetrics to monitor CPU usage.
     * Tracks system CPU usage and process CPU usage.
     *
     * @param meterRegistry the Micrometer registry to bind metrics to
     * @return ProcessorMetrics binder
     */
    @Bean
    public ProcessorMetrics processorMetrics(MeterRegistry meterRegistry) {
        ProcessorMetrics metrics = new ProcessorMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Registers JvmThreadMetrics to monitor JVM threads.
     * Tracks active threads, daemon threads, peak threads, and started threads.
     *
     * @param meterRegistry the Micrometer registry to bind metrics to
     * @return JvmThreadMetrics binder
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics(MeterRegistry meterRegistry) {
        JvmThreadMetrics metrics = new JvmThreadMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }


    /**
     * Registers KafkaStreamsMicrometerListener to capture Kafka Streams metrics.
     * The listener monitors Kafka Streams state changes and registers native metrics
     * in Micrometer for export to monitoring platforms like Dynatrace.
     *
     * @param meterRegistry the Micrometer registry to register metrics
     * @return KafkaStreamsMicrometerListener instance
     */
    @Bean
    public com.labs.repartitioner.metrics.KafkaStreamsMicrometerListener kafkaStreamsMicrometerListener(
            MeterRegistry meterRegistry) {
        return new com.labs.repartitioner.metrics.KafkaStreamsMicrometerListener(meterRegistry);
    }

    /**
     * Registers RocksDBMetricsCollector to capture RocksDB state store metrics.
     * The collector periodically reads RocksDB statistics and registers them as
     * Micrometer metrics for export to monitoring platforms like Dynatrace.
     *
     * Requirements: 11.3
     *
     * @param meterRegistry the Micrometer registry to register metrics
     * @return RocksDBMetricsCollector instance
     */
    @Bean
    public com.labs.repartitioner.metrics.RocksDBMetricsCollector rocksDBMetricsCollector(
            MeterRegistry meterRegistry) {
        com.labs.repartitioner.metrics.RocksDBMetricsCollector collector =
                new com.labs.repartitioner.metrics.RocksDBMetricsCollector(meterRegistry);
        collector.bindTo(meterRegistry);
        log.info("RocksDBMetricsCollector registered successfully");
        return collector;
    }

    /**
     * Configures the StreamsBuilderFactoryBean to integrate with KafkaStreamsMicrometerListener.
     * This method is called automatically by Spring after the StreamsBuilderFactoryBean is created
     * by @EnableKafkaStreams. It registers the listener and injects the KafkaStreams instance.
     *
     * @param factoryBean the StreamsBuilderFactoryBean created by @EnableKafkaStreams
     * @param listener the KafkaStreamsMicrometerListener to register
     */
    @Bean
    public org.springframework.beans.factory.config.BeanPostProcessor kafkaStreamsListenerIntegration(
            com.labs.repartitioner.metrics.KafkaStreamsMicrometerListener listener) {

        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof StreamsBuilderFactoryBean) {
                    StreamsBuilderFactoryBean factoryBean = (StreamsBuilderFactoryBean) bean;

                    log.info("Configuring KafkaStreamsMicrometerListener integration for bean: {}", beanName);

                    // Register the listener as a state listener
                    factoryBean.setStateListener(listener);

                    // Set up a callback to inject the KafkaStreams instance after initialization
                    factoryBean.setKafkaStreamsCustomizer(kafkaStreams -> {
                        log.info("Injecting KafkaStreams instance into listener");
                        listener.setKafkaStreams(kafkaStreams);
                    });

                    log.info("KafkaStreamsMicrometerListener integration configured successfully");
                }
                return bean;
            }
        };
    }

}

