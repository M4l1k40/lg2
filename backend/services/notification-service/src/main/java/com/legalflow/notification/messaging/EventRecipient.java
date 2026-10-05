package com.legalflow.notification.messaging;

import java.util.UUID;

public record EventRecipient(
        UUID recipientId,
        UUID lawFirmId,
        String recipientRole,
        String name,
        String email,
        String phone) {
}