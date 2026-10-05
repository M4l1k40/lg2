package com.legalflow.gateway;

import com.legalflow.gateway.config.GatewayRoutesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExposeDeadlineRoutes() {
        assertThat(routeLocator.getRoutes()
                .filter(route -> route.getUri().toString().contains("deadline-service")
                        || route.getId().equals("deadline-service"))
                .collectList()
                .block()).isNotEmpty();
    }

    @Test
    void shouldExposeAppointmentRoutes() {
        assertThat(routeLocator.getRoutes()
                .filter(route -> route.getUri().toString().contains("appointment-service")
                        || route.getId().equals("appointment-service"))
                .collectList()
                .block()).isNotEmpty();
    }

    @Test
    void shouldExposeNotificationRoutes() {
        assertThat(routeLocator.getRoutes()
                .filter(route -> route.getUri().toString().contains("notification-service")
                        || route.getId().equals("notification-service"))
                .collectList()
                .block()).isNotEmpty();
    }
}
