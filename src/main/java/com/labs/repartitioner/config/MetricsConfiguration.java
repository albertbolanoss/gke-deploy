package com.labs.repartitioner.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Configuration class for Micrometer metrics.
 * Applies common tags to all metrics for consistent identification in monitoring systems.
 * Registers JVM metrics binders for comprehensive JVM observability.
 */
@Configuration
public class MetricsConfiguration {

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
     * @return ClassLoaderMetrics binder
     */
    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    /**
     * Registers JvmMemoryMetrics to monitor JVM memory usage.
     * Tracks heap and non-heap memory usage, committed memory, and max memory.
     *
     * @return JvmMemoryMetrics binder
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Registers JvmGcMetrics to monitor garbage collection.
     * Tracks GC count, GC time, and memory pool statistics.
     *
     * @return JvmGcMetrics binder
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Registers ProcessorMetrics to monitor CPU usage.
     * Tracks system CPU usage and process CPU usage.
     *
     * @return ProcessorMetrics binder
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Registers JvmThreadMetrics to monitor JVM threads.
     * Tracks active threads, daemon threads, peak threads, and started threads.
     *
     * @return JvmThreadMetrics binder
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }
}
