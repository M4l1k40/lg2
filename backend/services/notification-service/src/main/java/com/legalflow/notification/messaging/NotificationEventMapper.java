package com.legalflow.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationEventMapper {

    private static final Map<String, List<String>> RECIPIENT_KEYS = Map.ofEntries(
            Map.entry("CASE_CREATED", List.of("actor", "lawyer")),
            Map.entry("DEADLINE_CREATED", List.of("actor", "creator")),
            Map.entry("DEADLINE_APPROACHING", List.of("creator", "actor")),
            Map.entry("DEADLINE_OVERDUE", List.of("creator", "actor")),
            Map.entry("INVOICE_CREATED", List.of("client", "actor")),
            Map.entry("INVOICE_ISSUED", List.of("client", "actor")),
            Map.entry("PAYMENT_RECEIVED", List.of("client", "actor")),
            Map.entry("INVOICE_OVERDUE", List.of("client", "actor")),
            Map.entry("CONSULTATION_CREATED", List.of("client", "actor")),
            Map.entry("CONSULTATION_ASSIGNED", List.of("lawyer", "actor")),
            Map.entry("CONSULTATION_ANSWERED", List.of("client", "actor")),
            Map.entry("CONSULTATION_CLOSED", List.of("client", "actor")),
            Map.entry("CONSULTATION_CANCELLED", List.of("client", "actor")),
            Map.entry("APPOINTMENT_CREATED", List.of("client", "lawyer", "actor")),
            Map.entry("APPOINTMENT_UPDATED", List.of("client", "lawyer", "actor")),
            Map.entry("APPOINTMENT_CANCELLED", List.of("client", "lawyer", "actor")),
            Map.entry("APPOINTMENT_REMINDER", List.of("client", "lawyer")),
            Map.entry("JUDICIAL_CASE_REGISTERED", List.of("actor")),
            Map.entry("JUDICIAL_STATUS_CHANGED", List.of("actor")),
            Map.entry("HEARING_CREATED", List.of("actor")),
            Map.entry("HEARING_STATUS_CHANGED", List.of("actor"))
    );

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("CASE_CREATED", "Case created"),
            Map.entry("DEADLINE_CREATED", "Deadline created"),
            Map.entry("DEADLINE_APPROACHING", "Deadline approaching"),
            Map.entry("DEADLINE_OVERDUE", "Deadline overdue"),
            Map.entry("INVOICE_CREATED", "Invoice created"),
            Map.entry("INVOICE_ISSUED", "Invoice issued"),
            Map.entry("PAYMENT_RECEIVED", "Payment received"),
            Map.entry("INVOICE_OVERDUE", "Invoice overdue"),
            Map.entry("CONSULTATION_CREATED", "Consultation created"),
            Map.entry("CONSULTATION_ASSIGNED", "Consultation assigned"),
            Map.entry("CONSULTATION_ANSWERED", "Consultation answered"),
            Map.entry("CONSULTATION_CLOSED", "Consultation closed"),
            Map.entry("CONSULTATION_CANCELLED", "Consultation cancelled"),
            Map.entry("APPOINTMENT_CREATED", "Appointment created"),
            Map.entry("APPOINTMENT_UPDATED", "Appointment updated"),
            Map.entry("APPOINTMENT_CANCELLED", "Appointment cancelled"),
            Map.entry("APPOINTMENT_REMINDER", "Appointment reminder"),
            Map.entry("JUDICIAL_CASE_REGISTERED", "Judicial case registered"),
            Map.entry("JUDICIAL_STATUS_CHANGED", "Judicial status changed"),
            Map.entry("HEARING_CREATED", "Hearing created"),
            Map.entry("HEARING_STATUS_CHANGED", "Hearing status changed")
    );

    private final ObjectMapper objectMapper;

    public NotificationEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MappedEventNotification map(DomainEventEnvelope event) {
        validateEnvelope(event);
        List<String> recipientKeys = RECIPIENT_KEYS.get(event.eventType());
        if (recipientKeys == null) {
            throw new IllegalArgumentException("Unsupported notification event type: " + event.eventType());
        }

        List<EventRecipient> recipients = new ArrayList<>();
        Map<String, EventRecipient> distinctRecipients = new LinkedHashMap<>();
        for (String recipientKey : recipientKeys) {
            Object recipientValue = event.payload().get(recipientKey);
            if (recipientValue == null) {
                continue;
            }
            EventRecipient recipient = objectMapper.convertValue(recipientValue, EventRecipient.class);
            validateRecipient(event, recipient);
            try {
                NotificationRecipientType.valueOf(recipient.recipientRole());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            distinctRecipients.putIfAbsent(recipient.recipientId() + ":" + recipient.recipientRole(), recipient);
        }
        recipients.addAll(distinctRecipients.values());
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("Event has no notification recipients: " + event.eventId());
        }

        NotificationType type = NotificationType.valueOf(event.eventType());
        Object payloadMessage = event.payload().get("message");
        String message = payloadMessage instanceof String value && !value.isBlank()
            ? value
            : defaultMessage(event);
        return new MappedEventNotification(type, TITLES.get(event.eventType()),
            message, recipients);
    }

    private void validateEnvelope(DomainEventEnvelope event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null
                || event.lawFirmId() == null || event.source() == null || event.source().isBlank()
                || event.aggregateType() == null || event.aggregateType().isBlank()
                || event.aggregateId() == null || event.eventType() == null || event.payload() == null) {
            throw new IllegalArgumentException("Event envelope is missing a required field.");
        }
    }

    private void validateRecipient(DomainEventEnvelope event, EventRecipient recipient) {
        if (recipient.recipientId() == null || recipient.lawFirmId() == null
                || recipient.recipientRole() == null || recipient.recipientRole().isBlank()) {
            throw new IllegalArgumentException("Event recipient is missing identity or role.");
        }
        if (!event.lawFirmId().equals(recipient.lawFirmId())) {
            throw new IllegalArgumentException("Event recipient tenant does not match event tenant.");
        }
    }

    private String defaultMessage(DomainEventEnvelope event) {
        return TITLES.get(event.eventType()) + ": " + event.aggregateType() + " " + event.aggregateId();
    }
}