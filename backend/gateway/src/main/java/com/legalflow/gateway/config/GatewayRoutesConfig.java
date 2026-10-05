package com.legalflow.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        String clientServiceUrl = Optional.ofNullable(System.getenv("CLIENT_SERVICE_URL"))
                .orElse("http://client-service:8081");
        String caseServiceUrl = Optional.ofNullable(System.getenv("CASE_SERVICE_URL"))
                .orElse("http://case-service:8082");
        String judicialServiceUrl = Optional.ofNullable(System.getenv("JUDICIAL_SERVICE_URL"))
                .orElse("http://judicial-service:8084");
        String documentServiceUrl = Optional.ofNullable(System.getenv("DOCUMENT_SERVICE_URL"))
                .orElse("http://document-service:8085");
        String deadlineServiceUrl = Optional.ofNullable(System.getenv("DEADLINE_SERVICE_URL"))
                .orElse("http://deadline-service:8086");
        String billingServiceUrl = Optional.ofNullable(System.getenv("BILLING_SERVICE_URL"))
                .orElse("http://billing-service:8088");
        String consultationServiceUrl = Optional.ofNullable(System.getenv("CONSULTATION_SERVICE_URL"))
                .orElse("http://consultation-service:8089");
        String appointmentServiceUrl = Optional.ofNullable(System.getenv("APPOINTMENT_SERVICE_URL"))
                .orElse("http://appointment-service:8083");
        String notificationServiceUrl = Optional.ofNullable(System.getenv("NOTIFICATION_SERVICE_URL"))
                .orElse("http://notification-service:8087");

        return builder.routes()
                .route("client-service", route -> route
                        .path("/api/clients/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(clientServiceUrl))
                .route("case-service", route -> route
                        .path("/api/cases/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(caseServiceUrl))
                .route("judicial-courts", route -> route
                        .path("/api/courts/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(judicialServiceUrl))
                .route("judicial-cases", route -> route
                        .path("/api/judicial-cases/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(judicialServiceUrl))
                .route("judicial-judges", route -> route
                        .path("/api/judges/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(judicialServiceUrl))
                .route("judicial-hearings", route -> route
                        .path("/api/hearings/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(judicialServiceUrl))
                .route("document-service", route -> route
                        .path("/api/documents/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(documentServiceUrl))
                .route("deadline-service", route -> route
                        .path("/api/deadlines/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(deadlineServiceUrl))
                .route("billing-service", route -> route
                        .path("/api/invoices/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(billingServiceUrl))
                .route("consultation-service", route -> route
                        .path("/api/consultations/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(consultationServiceUrl))
                .route("appointment-service", route -> route
                        .path("/api/appointments/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(appointmentServiceUrl))
                .route("notification-service", route -> route
                        .path("/api/notifications/**")
                        .filters(filter -> filter.stripPrefix(1))
                        .uri(notificationServiceUrl))
                .build();
    }
}
