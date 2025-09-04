package com.labs.repartitioner;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class GKEDeployAppTest {

    @Test
    void contextLoads() {
        // ya existe en el proyecto, garantiza que el contexto arranca
    }

    @Test
    void mainShouldInvokeSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(GKEDeployApp.class, new String[]{}))
                    .thenReturn(null);

            assertDoesNotThrow(() -> GKEDeployApp.main(new String[]{}));

            spring.verify(() -> SpringApplication.run(GKEDeployApp.class, new String[]{}));
        }
    }


    @Test
    void shouldLogEnvironmentVariablesWithKafkaOrRedisPrefix() {
        Map<String, Object> props = new HashMap<>();
        props.put("REDIS_HOST", "localhost");
        props.put("KAFKA_BOOTSTRAP", "localhost:9092");
        props.put("OTHER_VAR", "value");

        MapPropertySource mapPropertySource = new MapPropertySource("mockSource", props);

        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(mapPropertySource);

        AbstractEnvironment mockEnv = Mockito.mock(AbstractEnvironment.class);
        Mockito.when(mockEnv.getPropertySources()).thenReturn(propertySources);

        CommandLineRunner runner = new GKEDeployApp().printEnvironment(mockEnv);

        assertDoesNotThrow(() -> runner.run(new String[]{}));
    }

    @Test
    void shouldHandleNonMapPropertySourcesGracefully() {
        PropertySource<String> nonMapSource = new PropertySource<>("nonMap") {
            @Override
            public Object getProperty(String name) {
                return null;
            }
        };

        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(nonMapSource);

        AbstractEnvironment mockEnv = Mockito.mock(AbstractEnvironment.class);
        Mockito.when(mockEnv.getPropertySources()).thenReturn(propertySources);

        CommandLineRunner runner = new GKEDeployApp().printEnvironment(mockEnv);

        assertDoesNotThrow(() -> runner.run(new String[]{}));
    }

}
