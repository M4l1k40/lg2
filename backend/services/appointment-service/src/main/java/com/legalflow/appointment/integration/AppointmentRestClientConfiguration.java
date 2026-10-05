package com.legalflow.appointment.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AppointmentRestClientConfiguration {

    @Bean("clientServiceRestClient")
    public RestClient clientServiceRestClient(
            @Value("${CLIENT_SERVICE_URL}") String clientServiceUrl) {
        return buildClient(clientServiceUrl);
    }

    @Bean("caseServiceRestClient")
    public RestClient caseServiceRestClient(
            @Value("${CASE_SERVICE_URL}") String caseServiceUrl) {
        return buildClient(caseServiceUrl);
    }

    private RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
