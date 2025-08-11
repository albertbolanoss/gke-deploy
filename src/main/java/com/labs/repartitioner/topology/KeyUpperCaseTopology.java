package com.labs.repartitioner.topology;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

@Configuration
public class KeyUpperCaseTopology {
    private final RedisTemplate<String, Object> redisTemplate;
    private final Logger log = LoggerFactory.getLogger(KeyUpperCaseTopology.class);

    public KeyUpperCaseTopology(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public Topology createTopology(StreamsBuilder builder) {


        KStream<String, String> input = builder.stream("repartitioner-uppercase",
                Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> upperKeyStream = input.selectKey(
                (k, v) -> k == null ? null : k.toUpperCase());

        upperKeyStream.peek((k, v) -> {
            log.info("key: {} - Value: {}", k, v);

            var value =  Optional.ofNullable(redisTemplate.opsForValue().get(k));

            if (value.isEmpty())
                redisTemplate.opsForValue().set(k, v);

        });

        upperKeyStream.toTable(
                Named.as("uppercase-key-table"),
                Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("uppercase-key-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.String())
        );

        return builder.build();
    }
}
