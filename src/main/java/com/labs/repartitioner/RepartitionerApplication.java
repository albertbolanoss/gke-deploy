package com.labs.repartitioner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafkaStreams
public class RepartitionerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepartitionerApplication.class, args);
	}

}
