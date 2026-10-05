package com.legalflow.notification.service;

import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationRoutingServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void routesClientsToWhatsApp() {
        assertEquals(NotificationChannel.WHATSAPP, routingService().channelFor(NotificationRecipientType.CLIENT));
    }

    @Test
    void routesLawyersToEmail() {
        assertEquals(NotificationChannel.EMAIL, routingService().channelFor(NotificationRecipientType.LAWYER));
    }

    @Test
    void routesSecretariesToEmail() {
        assertEquals(NotificationChannel.EMAIL, routingService().channelFor(NotificationRecipientType.SECRETARY));
    }

    @Test
    void doesNotInventARecipientTypeForPlatformAdmin() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("realm_access", java.util.Map.of("roles", List.of("ADMIN")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertNull(routingService().recipientTypeForCurrentRecipient());
    }

    private NotificationRoutingService routingService() {
        NotificationProvider emailProvider = mock(NotificationProvider.class);
        NotificationProvider whatsAppProvider = mock(NotificationProvider.class);
        when(emailProvider.channel()).thenReturn(NotificationChannel.EMAIL);
        when(whatsAppProvider.channel()).thenReturn(NotificationChannel.WHATSAPP);
        return new NotificationRoutingService(List.of(emailProvider, whatsAppProvider));
    }
}