package com.labs.repartitioner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public ObjectMapper objectMapper() {
        var objectMappers = new ObjectMapper();
        objectMappers.registerModule(new JavaTimeModule());
        objectMappers.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return objectMappers;
    }
    @Bean
    public GenericJackson2JsonRedisSerializer serializer(ObjectMapper objectMapper) {
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisTemplate<String, Object> objectRedisTemplate(LettuceConnectionFactory connectionFactory,
                                                             GenericJackson2JsonRedisSerializer serializer) {
        var template = new RedisTemplate<String, Object>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.setConnectionFactory(connectionFactory);

        return template;
    }
}
