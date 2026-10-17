package com.legalflow.notification.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceAccountTokenProviderTest {

    private static final String TOKEN_URL = "http://keycloak:8080/realms/legalflow/protocol/openid-connect/token";
    private static final String TOKEN_BODY = "{\"access_token\":\"tok-1\",\"expires_in\":300}";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://keycloak:8080");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void fetchesATokenWithClientCredentialsAndReusesItUntilItExpires() {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "legalflow-notification-service",
                        "client_secret", "s3cret")))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        ServiceAccountTokenProvider provider = provider("s3cret");

        assertEquals(Optional.of("tok-1"), provider.accessToken());
        assertEquals(Optional.of("tok-1"), provider.accessToken());

        server.verify();
    }

    @Test
    void invalidateForcesANewTokenRequest() {
        server.expect(times(2), requestTo(TOKEN_URL))
                .andRespond(withSuccess(TOKEN_BODY, MediaType.APPLICATION_JSON));
        ServiceAccountTokenProvider provider = provider("s3cret");

        provider.accessToken();
        provider.invalidate();
        provider.accessToken();

        server.verify();
    }

    @Test
    void withoutSecretNoRequestIsMadeAndNoTokenIsReturned() {
        ServiceAccountTokenProvider provider = provider("");

        assertTrue(provider.accessToken().isEmpty());

        server.verify();
    }

    @Test
    void keycloakRefusalGivesNoTokenInsteadOfAnException() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertTrue(provider("wrong").accessToken().isEmpty());

        server.verify();
    }

    private ServiceAccountTokenProvider provider(String secret) {
        return new ServiceAccountTokenProvider(builder.build(), "legalflow", "legalflow-notification-service", secret);
    }
}
