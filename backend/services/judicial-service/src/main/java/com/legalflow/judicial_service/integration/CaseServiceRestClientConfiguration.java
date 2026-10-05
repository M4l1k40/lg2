package com.legalflow.judicial_service.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CaseServiceRestClientConfiguration {

    @Bean
    public RestClient caseServiceRestClient(
            @Value("${CASE_SERVICE_URL}") String caseServiceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl(caseServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
