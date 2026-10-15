package com.helpdesk;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class HelpdeskApplicationTest {
    @Test
    void mainDelegatesArgumentsToSpringApplication() {
        String[] args = {"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            HelpdeskApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(HelpdeskApplication.class, args));
        }
    }
}