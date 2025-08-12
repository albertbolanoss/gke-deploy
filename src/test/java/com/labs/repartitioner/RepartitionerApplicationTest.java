package com.labs.repartitioner;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class RepartitionerApplicationTest {

    @Test
    void contextLoads() {
        // ya existe en el proyecto, garantiza que el contexto arranca
    }

    @Test
    void mainShouldInvokeSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(RepartitionerApplication.class, new String[]{}))
                    .thenReturn(null);

            assertDoesNotThrow(() -> RepartitionerApplication.main(new String[]{}));

            spring.verify(() -> SpringApplication.run(RepartitionerApplication.class, new String[]{}));
        }
    }
}
