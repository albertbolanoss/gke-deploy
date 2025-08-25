package com.labs.repartitioner.config;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KafkaStreams.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaHealthCheckTest {

    @Mock ObjectProvider<List<StreamsBuilderFactoryBean>> provider;
    @Mock StreamsBuilderFactoryBean factory1;
    @Mock StreamsBuilderFactoryBean factory2;
    @Mock KafkaStreams ks1;
    @Mock KafkaStreams ks2;

    KafkaHealthCheck sut;

    @BeforeEach
    void setup() {
        sut = new KafkaHealthCheck(provider);
        // Valores fáciles de leer en los asserts
        ReflectionTestUtils.setField(sut, "appName", "Repartitioner");
        ReflectionTestUtils.setField(sut, "rebalancingMax", Duration.ofSeconds(180)); // PT3M
    }

    @Test
    void health_returnsUnknown_whenNoFactories() {
        when(provider.getIfAvailable()).thenReturn(emptyList());

        Health health = sut.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals("No StreamsBuilderFactoryBean beans found", health.getDetails().get("reason"));
    }

    @Test
    void health_returnsDown_whenKafkaStreamsIsNull() {
        when(provider.getIfAvailable()).thenReturn(List.of(factory1));
        when(factory1.getKafkaStreams()).thenReturn(null);

        Health health = sut.health();

        assertEquals(Status.DOWN, health.getStatus());
        // detalle “NOT_INITIALIZED” está dentro del mapa de topologías
        Map<?,?> details = health.getDetails();
        @SuppressWarnings("unchecked")
        Map<String, Object> topo = (Map<String, Object>) details.get("Repartitioner-topology-0");
        assertEquals("NOT_INITIALIZED", topo.get("state"));
    }

    @Test
    void health_returnsUp_whenRunning() {
        when(provider.getIfAvailable()).thenReturn(List.of(factory1));
        when(factory1.getKafkaStreams()).thenReturn(ks1);
        when(ks1.state()).thenReturn(State.RUNNING);
        when(ks1.localThreadsMetadata()).thenReturn(emptySet()); // evitar NPE en detalle

        Health health = sut.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("RUNNING",
                ((Map<?,?>) health.getDetails().get("Repartitioner-topology-0")).get("state"));
    }

    @Test
    void health_returnsUp_whenRebalancing_underThreshold() {
        when(provider.getIfAvailable()).thenReturn(List.of(factory1));
        when(factory1.getKafkaStreams()).thenReturn(ks1);
        when(ks1.state()).thenReturn(State.REBALANCING);
        when(ks1.localThreadsMetadata()).thenReturn(emptySet());

        Health health = sut.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("REBALANCING",
                ((Map<?,?>) health.getDetails().get("Repartitioner-topology-0")).get("state"));
    }

    @Test
    void health_returnsDown_whenRebalancing_overThreshold() {
        when(provider.getIfAvailable()).thenReturn(List.of(factory1));
        when(factory1.getKafkaStreams()).thenReturn(ks1);
        when(ks1.state()).thenReturn(State.REBALANCING);
        when(ks1.localThreadsMetadata()).thenReturn(emptySet());

        // Reducimos el umbral para facilitar el test
        ReflectionTestUtils.setField(sut, "rebalancingMax", Duration.ofSeconds(1));

        // Forzamos que ya está rebalancing desde hace tiempo
        // (simulamos la clave "appName-topology-0")
        @SuppressWarnings("unchecked")
        Map<String, Instant> since =
                (Map<String, Instant>) ReflectionTestUtils.getField(sut, "rebalancingSince");
        since.put("Repartitioner-topology-0", Instant.now().minusSeconds(10));

        Health health = sut.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("REBALANCING_TIMEOUT",
                ((Map<?,?>) health.getDetails().get("Repartitioner-topology-0")).get("state"));
    }

    @Test
    void health_aggregatesMultipleTopologies_downIfAnyIsBad() {
        when(provider.getIfAvailable()).thenReturn(List.of(factory1, factory2));

        // Topología 0: RUNNING
        when(factory1.getKafkaStreams()).thenReturn(ks1);
        when(ks1.state()).thenReturn(State.RUNNING);
        when(ks1.localThreadsMetadata()).thenReturn(emptySet());

        // Topología 1: ERROR
        when(factory2.getKafkaStreams()).thenReturn(ks2);
        when(ks2.state()).thenReturn(State.ERROR);
        when(ks2.localThreadsMetadata()).thenReturn(emptySet());

        Health health = sut.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("RUNNING",
                ((Map<?,?>) health.getDetails().get("Repartitioner-topology-0")).get("state"));
        assertEquals("ERROR",
                ((Map<?,?>) health.getDetails().get("Repartitioner-topology-1")).get("state"));
    }
}
