package com.legalflow.notification.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ServiceAccountRestClientConfiguration {

    @Bean("keycloakTokenRestClient")
    public RestClient keycloakTokenRestClient(
            @Value("${legalflow.service-account.keycloak-url:http://keycloak:8080}") String keycloakUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(keycloakUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
