package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class WhatsAppNotificationProviderTest {

    @Test
    void mockModeAcceptsAValidClientPhoneWithoutCallingAnExternalApi() {
        WhatsAppNotificationProvider provider = new WhatsAppNotificationProvider(
                RestClient.builder(), "MOCK", "META", "", "", "");

        provider.send(notification(), new NotificationRecipientContact("Client", null, "+15550100"));

        assertEquals(NotificationChannel.WHATSAPP, provider.channel());
    }

    @Test
    void realModeWithoutApiCredentialsFailsClearly() {
        WhatsAppNotificationProvider provider = new WhatsAppNotificationProvider(
                RestClient.builder(), "REAL", "META", "https://graph.facebook.com/v22.0", "", "");

        NotificationDeliveryException exception = assertThrows(NotificationDeliveryException.class,
                () -> provider.send(notification(), new NotificationRecipientContact("Client", null, "+15550100")));

        assertEquals("WhatsApp provider configuration is incomplete.", exception.getMessage());
    }

        @Test
        void realModeConvertsWhatsAppApiRejectionToDeliveryFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://graph.facebook.com/v22.0/phone-id/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer fake-test-token"))
            .andRespond(withServerError());
        WhatsAppNotificationProvider provider = new WhatsAppNotificationProvider(
            builder, "REAL", "META", "https://graph.facebook.com/v22.0",
            "fake-test-token", "phone-id");

        NotificationDeliveryException exception = assertThrows(NotificationDeliveryException.class,
            () -> provider.send(notification(), new NotificationRecipientContact("Client", null, "+15550100")));

        assertEquals("WhatsApp provider failed to send notification.", exception.getMessage());
        server.verify();
        }

    private Notification notification() {
        Notification notification = new Notification(UUID.randomUUID(), UUID.randomUUID(),
                NotificationRecipientType.CLIENT, NotificationChannel.WHATSAPP, "Deadline approaching",
                "Deadline approaching", "A legal deadline is approaching.", NotificationType.DEADLINE_APPROACHING,
                null, null);
        ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
        return notification;
    }
}