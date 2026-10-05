package com.legalflow.case_service.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ClientServiceOwnershipVerifierTest {

    private static final String CALLER_TOKEN = "caller-access-token";
    private static final UUID CLIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void downstream404MapsTo422AndForwardsCallerToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://client-service.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClientServiceOwnershipVerifier verifier = new ClientServiceOwnershipVerifier(builder.build());
        authenticateCaller();

        server.expect(requestTo("http://client-service.test/clients/" + CLIENT_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CALLER_TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyClientBelongsToCurrentTenant(CLIENT_ID));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertEquals("client not found in your law firm", exception.getReason());
        server.verify();
    }

    @Test
    void downstreamTimeoutMapsTo503AndNeverAllowsWriteThrough() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://client-service.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClientServiceOwnershipVerifier verifier = new ClientServiceOwnershipVerifier(builder.build());
        authenticateCaller();
        ResponseCreator timeout = request -> {
            throw new ResourceAccessException("Read timed out");
        };

        server.expect(requestTo("http://client-service.test/clients/" + CLIENT_ID))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + CALLER_TOKEN))
                .andRespond(timeout);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyClientBelongsToCurrentTenant(CLIENT_ID));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        server.verify();
    }

    @Test
    void downstream5xxMapsTo503() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://client-service.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClientServiceOwnershipVerifier verifier = new ClientServiceOwnershipVerifier(builder.build());
        authenticateCaller();

        server.expect(requestTo("http://client-service.test/clients/" + CLIENT_ID))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyClientBelongsToCurrentTenant(CLIENT_ID));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        server.verify();
    }

    private void authenticateCaller() {
        Jwt jwt = Jwt.withTokenValue(CALLER_TOKEN)
                .header("alg", "none")
                .claim("lawFirmId", "11111111-1111-1111-1111-111111111111")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
