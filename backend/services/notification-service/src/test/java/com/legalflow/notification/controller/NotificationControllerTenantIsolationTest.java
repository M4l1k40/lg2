package com.legalflow.notification.controller;

import com.legalflow.notification.config.NotificationSecurityConfig;
import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationType;
import com.legalflow.notification.repository.NotificationRepository;
import com.legalflow.notification.service.NotificationRecipientContact;
import com.legalflow.notification.service.NotificationRecipientContactResolver;
import com.legalflow.notification.service.NotificationRoutingService;
import com.legalflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({NotificationService.class, NotificationSecurityConfig.class, NotificationExceptionHandler.class})
class NotificationControllerTenantIsolationTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID NOTIFICATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationRepository notificationRepository;

        @MockBean
        private NotificationRoutingService notificationRoutingService;

        @MockBean
        private NotificationRecipientContactResolver recipientContactResolver;

    @Test
    void createsNotificationUsingTenantAndSubFromJwt() throws Exception {
        when(notificationRoutingService.recipientTypeForCurrentRecipient())
                .thenReturn(NotificationRecipientType.LAWYER);
        when(notificationRoutingService.channelFor(NotificationRecipientType.LAWYER))
                .thenReturn(NotificationChannel.EMAIL);
        when(recipientContactResolver.resolve(NotificationRecipientType.LAWYER))
                .thenReturn(new NotificationRecipientContact("Lawyer A", "lawyer-a@example.test", null));
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", NOTIFICATION_ID);
            ReflectionTestUtils.setField(saved, "createdAt", CREATED_AT);
            return saved;
        });

        mockMvc.perform(post("/notifications")
                        .with(jwt().jwt(token -> token.subject(USER_A.toString())
                                .claim("lawFirmId", FIRM_A.toString())
                                .claim("sid", USER_B.toString())
                                .claim("realm_access", java.util.Map.of("roles", List.of("LAWYER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEADLINE_APPROACHING","title":"Due soon","message":"A deadline is approaching.","referenceType":"DEADLINE","referenceId":"cccccccc-cccc-cccc-cccc-cccccccccccc","lawFirmId":"44444444-4444-4444-4444-444444444444","recipientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","recipientType":"CLIENT","channel":"WHATSAPP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$.recipientId").value(USER_A.toString()))
                .andExpect(jsonPath("$.recipientType").value("LAWYER"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.read").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.readAt").doesNotExist());

        verify(notificationRepository, times(2)).saveAndFlush(any(Notification.class));
    }

    @Test
    void secretaryCannotForceWhatsAppChannel() throws Exception {
        when(notificationRoutingService.recipientTypeForCurrentRecipient())
                .thenReturn(NotificationRecipientType.SECRETARY);
        when(notificationRoutingService.channelFor(NotificationRecipientType.SECRETARY))
                .thenReturn(NotificationChannel.EMAIL);
        when(recipientContactResolver.resolve(NotificationRecipientType.SECRETARY))
                .thenReturn(new NotificationRecipientContact("Secretary A", "secretary@example.test", null));
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", NOTIFICATION_ID);
            ReflectionTestUtils.setField(saved, "createdAt", CREATED_AT);
            return saved;
        });

        mockMvc.perform(post("/notifications")
                        .with(jwt().jwt(token -> token.subject(USER_A.toString())
                                .claim("lawFirmId", FIRM_A.toString())
                                .claim("realm_access", java.util.Map.of("roles", List.of("SECRETARY")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DOCUMENT_ADDED","title":"Secretary notice","message":"A document was added.","recipientType":"LAWYER","channel":"WHATSAPP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientType").value("SECRETARY"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));
    }

        @Test
        void adminCanStillCreateInboxNotificationWithoutExternalRouting() throws Exception {
                when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
                        Notification saved = invocation.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", NOTIFICATION_ID);
                        ReflectionTestUtils.setField(saved, "createdAt", CREATED_AT);
                        return saved;
                });

                mockMvc.perform(post("/notifications")
                                                .with(jwt().jwt(token -> token.subject(USER_A.toString())
                                                                .claim("lawFirmId", FIRM_A.toString())
                                                                .claim("realm_access", java.util.Map.of("roles", List.of("ADMIN")))))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("""
                                                                {"type":"DOCUMENT_ADDED","title":"Admin inbox notice","message":"Internal inbox notice."}
                                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.recipientType").doesNotExist())
                                .andExpect(jsonPath("$.channel").doesNotExist())
                                .andExpect(jsonPath("$.status").value("FAILED"));

                verify(recipientContactResolver, never()).resolve(any());
                verify(notificationRoutingService, never()).send(any(Notification.class), any());
        }

    @Test
    void retrievesOwnedNotification() throws Exception {
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_A))
                .thenReturn(Optional.of(notification(NOTIFICATION_ID, FIRM_A, USER_A, false)));

        mockMvc.perform(get("/notifications/{id}", NOTIFICATION_ID).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$.recipientId").value(USER_A.toString()));
    }

    @Test
    void listsOnlyAuthenticatedUsersNotifications() throws Exception {
        when(notificationRepository.findAllByLawFirmIdAndRecipientIdOrderByCreatedAtDesc(FIRM_A, USER_A))
                .thenReturn(List.of(notification(NOTIFICATION_ID, FIRM_A, USER_A, false)));

        mockMvc.perform(get("/notifications").with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientId").value(USER_A.toString()))
                .andExpect(jsonPath("$[1]").doesNotExist());

        verify(notificationRepository)
                .findAllByLawFirmIdAndRecipientIdOrderByCreatedAtDesc(FIRM_A, USER_A);
    }

    @Test
    void listsOnlyUnreadNotifications() throws Exception {
        when(notificationRepository
                .findAllByLawFirmIdAndRecipientIdAndReadFalseOrderByCreatedAtDesc(FIRM_A, USER_A))
                .thenReturn(List.of(notification(NOTIFICATION_ID, FIRM_A, USER_A, false)));

        mockMvc.perform(get("/notifications/unread").with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(false));

        verify(notificationRepository)
                .findAllByLawFirmIdAndRecipientIdAndReadFalseOrderByCreatedAtDesc(FIRM_A, USER_A);
    }

    @Test
    void marksOwnedNotificationReadAndSetsReadAt() throws Exception {
        Notification notification = notification(NOTIFICATION_ID, FIRM_A, USER_A, false);
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_A))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/notifications/{id}/read", NOTIFICATION_ID).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        verify(notificationRepository).save(notification);
    }

    @Test
    void markingAlreadyReadNotificationIsIdempotent() throws Exception {
        Notification notification = notification(NOTIFICATION_ID, FIRM_A, USER_A, true);
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_A))
                .thenReturn(Optional.of(notification));

        mockMvc.perform(patch("/notifications/{id}/read", NOTIFICATION_ID).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void marksAllUnreadNotificationsRead() throws Exception {
        Notification first = notification(NOTIFICATION_ID, FIRM_A, USER_A, false);
        Notification second = notification(UUID.randomUUID(), FIRM_A, USER_A, false);
        when(notificationRepository.findAllByLawFirmIdAndRecipientIdAndReadFalse(FIRM_A, USER_A))
                .thenReturn(List.of(first, second));
        when(notificationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/notifications/read-all").with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        verify(notificationRepository).saveAll(any());
    }

    @Test
    void deletesOnlyOwnedNotification() throws Exception {
        Notification notification = notification(NOTIFICATION_ID, FIRM_A, USER_A, false);
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_A))
                .thenReturn(Optional.of(notification));

        mockMvc.perform(delete("/notifications/{id}", NOTIFICATION_ID).with(jwtForA()))
                .andExpect(status().isNoContent());

        verify(notificationRepository).delete(notification);
    }

    @Test
    void missingNotificationReturns404() throws Exception {
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_A))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/notifications/{id}", NOTIFICATION_ID).with(jwtForA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found: " + NOTIFICATION_ID));
    }

    @Test
    void anotherRecipientInSameFirmCannotReadOrMarkNotification() throws Exception {
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_B))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/notifications/{id}", NOTIFICATION_ID).with(jwtForBInFirmA()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/notifications/{id}/read", NOTIFICATION_ID).with(jwtForBInFirmA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotReadAnotherFirmsDeliveryStatus() throws Exception {
        when(notificationRepository.findByIdAndLawFirmIdAndRecipientId(NOTIFICATION_ID, FIRM_A, USER_A))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/notifications/{id}/status", NOTIFICATION_ID).with(jwtForA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void firmBQueriesOnlyItsOwnTenantRows() throws Exception {
        when(notificationRepository.findAllByLawFirmIdAndRecipientIdOrderByCreatedAtDesc(FIRM_B, USER_B))
                .thenReturn(List.of(notification(NOTIFICATION_ID, FIRM_B, USER_B, false)));

        mockMvc.perform(get("/notifications").with(jwtForB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lawFirmId").value(FIRM_B.toString()))
                .andExpect(jsonPath("$[0].recipientId").value(USER_B.toString()));

        verify(notificationRepository)
                .findAllByLawFirmIdAndRecipientIdOrderByCreatedAtDesc(FIRM_B, USER_B);
    }

    @Test
    void invalidFieldsReturn400WithoutSaving() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DOCUMENT_ADDED","title":" ","message":"valid message"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("400"));

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void invalidNotificationTypeReturns400WithoutSaving() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"NOT_A_TYPE","title":"Title","message":"Message"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid."));

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingTenantClaimReturns403() throws Exception {
        mockMvc.perform(get("/notifications")
                        .with(jwt().jwt(token -> token.subject(USER_A.toString()))))
                .andExpect(status().isForbidden());
    }

    private Notification notification(UUID id, UUID lawFirmId, UUID recipientId, boolean read) {
        Notification notification = new Notification(lawFirmId, recipientId, NotificationType.DEADLINE_CREATED,
                "Deadline created", "A deadline was created.", "DEADLINE", UUID.randomUUID());
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", CREATED_AT);
        if (read) {
            notification.markRead(CREATED_AT.plusMinutes(1));
        }
        return notification;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtForA() {
                return jwt().jwt(token -> token.subject(USER_A.toString()).claim("lawFirmId", FIRM_A.toString())
                                .claim("realm_access", java.util.Map.of("roles", List.of("LAWYER"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtForB() {
                return jwt().jwt(token -> token.subject(USER_B.toString()).claim("lawFirmId", FIRM_B.toString())
                                .claim("realm_access", java.util.Map.of("roles", List.of("LAWYER"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtForBInFirmA() {
                return jwt().jwt(token -> token.subject(USER_B.toString()).claim("lawFirmId", FIRM_A.toString())
                                .claim("realm_access", java.util.Map.of("roles", List.of("LAWYER"))));
    }
}
