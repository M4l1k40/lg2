package com.legalflow.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationType;
import com.legalflow.notification.repository.NotificationRepository;
import com.legalflow.notification.repository.ProcessedEventRepository;
import com.legalflow.notification.service.NotificationDeliveryException;
import com.legalflow.notification.service.NotificationProvider;
import com.legalflow.notification.service.NotificationRoutingService;
import com.legalflow.notification.service.NotificationRecipientContact;
import com.legalflow.notification.service.WhatsAppNotificationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventProcessorTest {

    @Test
    void processesANewDomainEventAndRejectsDuplicates() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
        NotificationProvider emailProvider = mock(NotificationProvider.class);
        WhatsAppNotificationProvider whatsappProvider = new WhatsAppNotificationProvider(
                RestClient.builder(), "MOCK", "META", "https://graph.facebook.com/v22.0", "", "");
        when(emailProvider.channel()).thenReturn(NotificationChannel.EMAIL);

        NotificationEventMapper mapper = new NotificationEventMapper(new ObjectMapper());
        NotificationRoutingService routingService = new NotificationRoutingService(
                List.of(emailProvider, whatsappProvider));
        NotificationEventProcessor processor = new NotificationEventProcessor(
                mapper,
                notificationRepository,
                processedEventRepository,
                routingService
        );

        UUID lawFirmId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID lawyerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID clientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID eventId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID aggregateId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(notificationRepository.findByEventIdAndLawFirmIdAndRecipientIdAndChannel(
                eventId, lawFirmId, lawyerId, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByEventIdAndLawFirmIdAndRecipientIdAndChannel(
                eventId, lawFirmId, clientId, NotificationChannel.WHATSAPP))
                .thenReturn(Optional.empty());
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now());
            }
            return notification;
        });
        AtomicBoolean processed = new AtomicBoolean(false);
        when(processedEventRepository.existsById(eventId)).thenAnswer(invocation -> processed.get());
        when(processedEventRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            processed.set(true);
            return invocation.getArgument(0);
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor", recipient("LAWYER", lawyerId, lawFirmId, "Lawyer A", "lawyer@example.test", null));
        payload.put("lawyer", recipient("LAWYER", lawyerId, lawFirmId, "Lawyer A", "lawyer@example.test", null));
        payload.put("client", recipient("CLIENT", clientId, lawFirmId, "Client A", null, "+15550100"));

        DomainEventEnvelope event = new DomainEventEnvelope(
                eventId,
                "CASE_CREATED",
                Instant.now(),
                lawFirmId,
                "case-service",
                "LegalCase",
                aggregateId,
                payload
        );

        processor.process(event);

        verify(notificationRepository, times(2)).saveAndFlush(any(Notification.class));
        verify(emailProvider, times(1)).send(any(Notification.class), any(NotificationRecipientContact.class));
        verify(processedEventRepository, times(1)).saveAndFlush(any());

        processor.process(event);

        verify(notificationRepository, times(2)).saveAndFlush(any(Notification.class));
        verify(processedEventRepository, times(1)).saveAndFlush(any());
        verify(emailProvider, times(1)).send(any(Notification.class), any(NotificationRecipientContact.class));
    }

    private Map<String, Object> recipient(String role, UUID recipientId, UUID lawFirmId,
                                         String name, String email, String phone) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recipientId", recipientId);
        map.put("lawFirmId", lawFirmId);
        map.put("recipientRole", role);
        map.put("name", name);
        map.put("email", email);
        map.put("phone", phone);
        return map;
    }
}
