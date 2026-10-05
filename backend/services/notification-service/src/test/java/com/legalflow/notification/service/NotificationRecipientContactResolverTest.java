package com.legalflow.notification.service;

import com.legalflow.notification.domain.NotificationRecipientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationRecipientContactResolverTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesClientPhoneFromTenantScopedClientServiceUsingCallerToken() {
        UUID clientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://client-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://client-service/clients/" + clientId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer client-token"))
                .andRespond(withSuccess("""
                        {"firstName":"Client","lastName":"A","email":"client@example.test","phone":"+15550100"}
                        """, MediaType.APPLICATION_JSON));
        NotificationRecipientContactResolver resolver = new NotificationRecipientContactResolver(builder.build());
        Jwt jwt = Jwt.withTokenValue("client-token")
                .header("alg", "none")
                .subject(clientId.toString())
                .claim("lawFirmId", "11111111-1111-1111-1111-111111111111")
                .claim("realm_access", Map.of("roles", List.of("CLIENT")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        NotificationRecipientContact contact = resolver.resolve(NotificationRecipientType.CLIENT);

        assertEquals("Client A", contact.name());
        assertEquals("client@example.test", contact.email());
        assertEquals("+15550100", contact.phone());
        server.verify();
    }
}