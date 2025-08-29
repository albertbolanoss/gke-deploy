package com.labs.repartitioner.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KafkaTopicConfigTest {
    private KafkaTopicConfig kafkaTopicConfig;

    @BeforeEach
    void init() {
        kafkaTopicConfig = new KafkaTopicConfig();
    }

    @Test
    void createUppercaseTopicTest() {
        Assertions.assertNotNull(kafkaTopicConfig.createUppercaseTopic());
    }
}
