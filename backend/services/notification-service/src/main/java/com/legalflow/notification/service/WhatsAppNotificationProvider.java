package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class WhatsAppNotificationProvider implements NotificationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppNotificationProvider.class);

    private final RestClient restClient;
    private final String mode;
    private final String provider;
    private final String apiUrl;
    private final String accessToken;
    private final String phoneNumberId;

    public WhatsAppNotificationProvider(RestClient.Builder restClientBuilder,
                                        @Value("${notifications.whatsapp.mode:MOCK}") String mode,
                                        @Value("${notifications.whatsapp.provider:META}") String provider,
                                        @Value("${notifications.whatsapp.api-url:}") String apiUrl,
                                        @Value("${notifications.whatsapp.access-token:}") String accessToken,
                                        @Value("${notifications.whatsapp.phone-number-id:}") String phoneNumberId) {
        this.restClient = restClientBuilder.build();
        this.mode = mode;
        this.provider = provider;
        this.apiUrl = apiUrl;
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public void send(Notification notification, NotificationRecipientContact recipient) {
        if (recipient.phone() == null || recipient.phone().isBlank()) {
            throw new NotificationDeliveryException("Recipient WhatsApp phone number is missing.");
        }
        if ("MOCK".equalsIgnoreCase(mode)) {
            LOGGER.info("Mock WhatsApp notification for recipient {}: {}",
                    notification.getRecipientId(), notification.getMessage());
            return;
        }
        if (!"REAL".equalsIgnoreCase(mode)) {
            throw new NotificationDeliveryException("WHATSAPP_MODE must be MOCK or REAL.");
        }
        if (!"META".equalsIgnoreCase(provider)) {
            throw new NotificationDeliveryException("Unsupported WHATSAPP_PROVIDER.");
        }
        if (isBlank(apiUrl) || isBlank(accessToken) || isBlank(phoneNumberId)) {
            throw new NotificationDeliveryException("WhatsApp provider configuration is incomplete.");
        }

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", recipient.phone(),
                "type", "text",
                "text", Map.of("body", notification.getMessage()));
        try {
            restClient.post()
                    .uri(apiUrl.replaceAll("/+$", "") + "/" + phoneNumberId + "/messages")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new NotificationDeliveryException("WhatsApp provider failed to send notification.", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}