package com.labs.repartitioner.config;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KafkaStreams.State;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component("kafkaHealthCheck")
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthCheck implements org.springframework.boot.actuate.health.HealthIndicator {

    private final ObjectProvider<List<StreamsBuilderFactoryBean>> factoriesProvider;

    @Value("${spring.application.name:app}")
    private String appName;

    /** Umbral ISO-8601 (PT3M = 3 minutos) */
    @Value("${health.kafka.rebalancing-max:PT3M}")
    private Duration rebalancingMax;

    /** Timestamp por topología cuando entra a REBALANCING */
    private final ConcurrentMap<String, Instant> rebalancingSince = new ConcurrentHashMap<>();

    public KafkaHealthCheck(ObjectProvider<List<StreamsBuilderFactoryBean>> factoriesProvider) {
        this.factoriesProvider = factoriesProvider;
    }

    @Override
    public Health health() {
        List<StreamsBuilderFactoryBean> factories =
                Optional.ofNullable(factoriesProvider.getIfAvailable())
                        .orElseGet(Collections::emptyList);

        if (factories.isEmpty()) {
            return Health.unknown().withDetail("reason", "No StreamsBuilderFactoryBean beans found").build();
        }

        boolean anyDown = false;
        Map<String, Object> details = new LinkedHashMap<>();

        for (int i = 0; i < factories.size(); i++) {
            StreamsBuilderFactoryBean f = factories.get(i);
            String key = appName + "-topology-" + i;

            KafkaStreams ks = f.getKafkaStreams();
            if (ks == null) {
                rebalancingSince.remove(key);
                details.put(key, Map.of("state", "NOT_INITIALIZED", "threadCount", 0));
                anyDown = true;
                continue;
            }

            State state = ks.state();

            if (state == State.REBALANCING) {
                Instant since = rebalancingSince.computeIfAbsent(key, k -> Instant.now());
                Duration elapsed = Duration.between(since, Instant.now());
                boolean timeout = elapsed.compareTo(rebalancingMax) > 0;

                details.put(key, Map.of(
                        "state", timeout ? "REBALANCING_TIMEOUT" : "REBALANCING",
                        "rebalancingFor", elapsed.toString(),
                        "threshold", rebalancingMax.toString(),
                        "threadCount", ks.localThreadsMetadata().size()
                ));
                if (timeout) anyDown = true;
                continue;
            } else {
                rebalancingSince.remove(key);
            }

            boolean up = (state == State.RUNNING);
            details.put(key, Map.of(
                    "state", state.name(),
                    "threadCount", ks.localThreadsMetadata().size()
            ));
            if (!up) anyDown = true;
        }

        Status overall = anyDown ? Status.DOWN : Status.UP;
        return Health.status(overall).withDetail("application", appName).withDetails(details).build();
    }
}
