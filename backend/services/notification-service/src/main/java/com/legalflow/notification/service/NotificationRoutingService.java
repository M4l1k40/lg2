package com.legalflow.notification.service;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationRoutingService {

    private final Map<NotificationChannel, NotificationProvider> providers;

    public NotificationRoutingService(List<NotificationProvider> providers) {
        this.providers = new EnumMap<>(NotificationChannel.class);
        providers.forEach(provider -> this.providers.put(provider.channel(), provider));
    }

    public NotificationChannel channelFor(NotificationRecipientType recipientType) {
        return switch (recipientType) {
            case CLIENT -> NotificationChannel.WHATSAPP;
            case LAWYER, SECRETARY -> NotificationChannel.EMAIL;
        };
    }

    public NotificationRecipientType recipientTypeForCurrentRecipient() {
        return TenantContext.recipientTypeForCurrentRecipient();
    }

    public void send(Notification notification, NotificationRecipientContact recipient) {
        NotificationProvider provider = providers.get(notification.getChannel());
        if (provider == null) {
            throw new NotificationDeliveryException("No provider is configured for the notification channel.");
        }
        provider.send(notification, recipient);
    }
}