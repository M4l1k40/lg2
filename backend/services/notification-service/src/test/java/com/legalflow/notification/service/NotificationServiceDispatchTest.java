package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationStatus;
import com.legalflow.notification.domain.NotificationType;
import com.legalflow.notification.dto.NotificationCreateRequest;
import com.legalflow.notification.dto.NotificationResponse;
import com.legalflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceDispatchTest {

    private static final UUID FIRM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RECIPIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private NotificationRepository repository;
    private NotificationProvider emailProvider;
    private WhatsAppNotificationProvider whatsAppProvider;
    private NotificationRecipientContactResolver contactResolver;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        emailProvider = mock(NotificationProvider.class);
        whatsAppProvider = new WhatsAppNotificationProvider(RestClient.builder(), "MOCK", "META", "", "", "");
        contactResolver = mock(NotificationRecipientContactResolver.class);
        when(emailProvider.channel()).thenReturn(NotificationChannel.EMAIL);

        NotificationRoutingService routingService = new NotificationRoutingService(
                List.of(emailProvider, whatsAppProvider));
        notificationService = new NotificationService(repository, routingService, contactResolver);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now());
            }
            return notification;
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientUsingMockWhatsAppIsPersistedAsSent() {
        setAuthenticatedRecipient(NotificationRecipientType.CLIENT);
        NotificationRecipientContact recipient = new NotificationRecipientContact("Client A", null, "+15550100");
        when(contactResolver.resolve(NotificationRecipientType.CLIENT)).thenReturn(recipient);

        NotificationResponse response = notificationService.create(request());

        assertEquals(NotificationRecipientType.CLIENT, response.recipientType());
        assertEquals(NotificationChannel.WHATSAPP, response.channel());
        assertEquals(NotificationStatus.SENT, response.status());
        assertNotNull(response.sentAt());
        verify(emailProvider, never()).send(any(Notification.class), any());
        verify(repository, times(2)).saveAndFlush(any(Notification.class));
    }

    @Test
    void successfulLawyerEmailDeliveryIsPersistedAsSent() {
        setAuthenticatedRecipient(NotificationRecipientType.LAWYER);
        NotificationRecipientContact recipient = new NotificationRecipientContact("Lawyer A", "lawyer@example.test", null);
        when(contactResolver.resolve(NotificationRecipientType.LAWYER)).thenReturn(recipient);

        NotificationResponse response = notificationService.create(request());

        assertEquals(NotificationRecipientType.LAWYER, response.recipientType());
        assertEquals(NotificationChannel.EMAIL, response.channel());
        assertEquals(NotificationStatus.SENT, response.status());
        assertNotNull(response.sentAt());
        verify(emailProvider).send(any(Notification.class), eq(recipient));
    }

    @Test
    void emailDeliveryFailureIsPersistedAsFailed() {
        setAuthenticatedRecipient(NotificationRecipientType.LAWYER);
        NotificationRecipientContact recipient = new NotificationRecipientContact("Lawyer A", "lawyer@example.test", null);
        when(contactResolver.resolve(NotificationRecipientType.LAWYER)).thenReturn(recipient);
        doThrow(new NotificationDeliveryException("Email provider failed to send notification."))
                .when(emailProvider).send(any(Notification.class), eq(recipient));

        NotificationResponse response = notificationService.create(request());

        assertEquals(NotificationChannel.EMAIL, response.channel());
        assertEquals(NotificationStatus.FAILED, response.status());
        assertEquals("Email provider failed to send notification.", response.errorMessage());
        verify(emailProvider).send(any(Notification.class), eq(recipient));
    }

    @Test
    void whatsAppDeliveryFailureIsPersistedAsFailed() {
        setAuthenticatedRecipient(NotificationRecipientType.CLIENT);
        NotificationRecipientContact recipient = new NotificationRecipientContact("Client A", null, "+15550100");
        when(contactResolver.resolve(NotificationRecipientType.CLIENT)).thenReturn(recipient);
        WhatsAppNotificationProvider failingProvider = new WhatsAppNotificationProvider(
            RestClient.builder(), "REAL", "META", "https://graph.facebook.com/v22.0", "", "");
        notificationService = new NotificationService(repository,
            new NotificationRoutingService(List.of(emailProvider, failingProvider)), contactResolver);

        NotificationResponse response = notificationService.create(request());

        assertEquals(NotificationChannel.WHATSAPP, response.channel());
        assertEquals(NotificationStatus.FAILED, response.status());
        assertEquals("WhatsApp provider configuration is incomplete.", response.errorMessage());
    }

    private NotificationCreateRequest request() {
        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setType(NotificationType.DEADLINE_APPROACHING);
        request.setTitle("Deadline approaching");
        request.setMessage("A legal deadline is approaching.");
        return request;
    }

    private void setAuthenticatedRecipient(NotificationRecipientType recipientType) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(RECIPIENT_ID.toString())
                .claim("lawFirmId", FIRM_ID.toString())
                .claim("realm_access", Map.of("roles", List.of(recipientType.name())))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}