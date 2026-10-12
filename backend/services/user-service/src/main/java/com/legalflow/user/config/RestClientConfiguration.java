package com.legalflow.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfiguration {

    @Bean("keycloakRestClient")
    public RestClient keycloakRestClient(KeycloakProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(Duration.ofSeconds(5)))
                .build();
    }

    @Bean("clientServiceRestClient")
    public RestClient clientServiceRestClient(@Value("${CLIENT_SERVICE_URL:http://client-service:8081}") String clientServiceUrl) {
        return RestClient.builder()
                .baseUrl(clientServiceUrl)
                .requestFactory(requestFactory(Duration.ofSeconds(3)))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }
}
