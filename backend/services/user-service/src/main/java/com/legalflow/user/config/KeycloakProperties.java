package com.legalflow.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "legalflow.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String clientId, String clientSecret) {
}
