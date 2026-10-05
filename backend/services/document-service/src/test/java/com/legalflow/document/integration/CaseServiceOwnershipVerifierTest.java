package com.legalflow.document.integration;

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

class CaseServiceOwnershipVerifierTest {

    private static final UUID CASE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsCallerBearerTokenAndMapsForeignCaseTo422() {
        String callerToken = "caller-access-token";
        RestClient.Builder builder = RestClient.builder().baseUrl("http://case-service.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaseServiceOwnershipVerifier verifier = new CaseServiceOwnershipVerifier(builder.build());
        authenticate(callerToken);
        server.expect(requestTo("http://case-service.test/cases/" + CASE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyCaseBelongsToCurrentTenant(CASE_ID));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertEquals("case not found in your law firm", exception.getReason());
        server.verify();
    }

    @Test
    void downstreamTimeoutMapsTo503() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://case-service.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaseServiceOwnershipVerifier verifier = new CaseServiceOwnershipVerifier(builder.build());
        authenticate("caller-access-token");
        ResponseCreator timeout = request -> { throw new ResourceAccessException("Read timed out"); };
        server.expect(requestTo("http://case-service.test/cases/" + CASE_ID)).andRespond(timeout);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> verifier.verifyCaseBelongsToCurrentTenant(CASE_ID));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        server.verify();
    }

    private void authenticate(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claim("lawFirmId", "11111111-1111-1111-1111-111111111111")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
