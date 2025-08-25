package com.labs.repartitioner.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RedisConfigTest {
    private final RedisConfig config = new RedisConfig();

    @Test
    void redisTemplateConfiguredWithExpectedSerializers() {
        LettuceConnectionFactory connectionFactory = mock(LettuceConnectionFactory.class);
        var serializer = config.serializer(config.objectMapper());

        RedisTemplate<String, Object> template = config.objectRedisTemplate(connectionFactory, serializer);

        assertAll(
            () -> assertEquals(connectionFactory, template.getConnectionFactory()),
            () -> assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer()),
            () -> assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer()),
            () -> assertSame(serializer, template.getValueSerializer()),
            () -> assertSame(serializer, template.getHashValueSerializer())
        );
    }
}
