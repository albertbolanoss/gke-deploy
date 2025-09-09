package com.labs.repartitioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.util.Iterator;
import java.util.Map;

@SpringBootApplication
@EnableKafkaStreams
public class GKEDeployApp {

	private static final Logger log = LoggerFactory.getLogger(GKEDeployApp.class);

	public static void main(String[] args) {
		SpringApplication.run(GKEDeployApp.class, args);
	}


	@Bean
	public CommandLineRunner printEnvironment(Environment env) {
		return args -> {
			log.info("=== Variables de ambiente cargadas ===");
			for (Iterator it = ((AbstractEnvironment) env).getPropertySources().iterator(); it.hasNext(); ) {
				PropertySource<?> propertySource = (PropertySource<?>) it.next();
				if (propertySource instanceof MapPropertySource) {
					Map<String, Object> props = ((MapPropertySource) propertySource).getSource();
					props.forEach((key, value) -> {
						if (key.startsWith("REDIS_") || key.startsWith("KAFKA_")) {
							log.info("Environment: {}: {}", key, value);
						}
					});
				}
			}
		};
	}
}
