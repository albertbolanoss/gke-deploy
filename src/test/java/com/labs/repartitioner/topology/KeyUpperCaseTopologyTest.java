package com.labs.repartitioner.topology;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyUpperCaseTopologyTest {
    private KeyUpperCaseTopology keyUpperCaseTopology;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private TestInputTopic<String, String> inputTopic;

    private TopologyTestDriver testDriver;

    private KeyValueStore<String, String> keyValueStore;

    private final String INPUT_TOPIC = "repartitioner-uppercase";

    private final String STORE_NAME  = "uppercase-key-store";


    @BeforeEach
    void init() {
        keyUpperCaseTopology = new KeyUpperCaseTopology(redisTemplate);

        var builder = new StreamsBuilder();
        var topology = keyUpperCaseTopology.createTopology(builder);

        var properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        testDriver = new TopologyTestDriver(topology, properties);

        inputTopic = testDriver.createInputTopic(INPUT_TOPIC, new StringSerializer(), new StringSerializer());
        keyValueStore = testDriver.getKeyValueStore("uppercase-key-store");
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) testDriver.close();
    }

    @Test
    void uppercasesKey_andWritesToStateStore() {
        var key = "foo";

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        inputTopic.pipeInput(key, "bar");

        Assertions.assertEquals("bar", keyValueStore.get(key.toUpperCase()));
    }

    @Test
    void writesToRedis_whenKeyNotPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        inputTopic.pipeInput("abc", "123");

        verify(valueOps, times(1)).set("ABC", "123");
    }

    @Test
    void doesNotWriteToRedis_whenKeyAlreadyPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.get("XYZ")).thenReturn("old");

        inputTopic.pipeInput("xyz", "new");

        verify(valueOps, never()).set(eq("XYZ"), any());
    }

    @Test
    void skipsRedisInteraction_whenKeyIsNull() {
        inputTopic.pipeInput(null, "value");

        verify(redisTemplate, never()).opsForValue();
    }
}
