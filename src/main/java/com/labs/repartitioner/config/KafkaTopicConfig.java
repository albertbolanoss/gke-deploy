package com.labs.repartitioner.config;

import com.labs.repartitioner.constant.TopicEnum;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicConfig {
    @Bean
    public NewTopic createUppercaseTopic() {
        return TopicBuilder.name(TopicEnum.UPPERCASE.getName())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
