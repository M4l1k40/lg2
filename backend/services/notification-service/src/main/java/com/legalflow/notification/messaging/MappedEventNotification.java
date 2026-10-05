package com.legalflow.notification.messaging;

import com.legalflow.notification.domain.NotificationType;

import java.util.List;

public record MappedEventNotification(NotificationType type, String title, String message,
                                      List<EventRecipient> recipients) {
}