package com.legalflow.events;

import java.util.List;

/** Types d'événements compris par le {@code notification-service}. La clé de routage RabbitMQ est le type. */
public final class EventTypes {

    public static final String CASE_CREATED = "CASE_CREATED";

    public static final String DEADLINE_CREATED = "DEADLINE_CREATED";
    public static final String DEADLINE_APPROACHING = "DEADLINE_APPROACHING";
    public static final String DEADLINE_OVERDUE = "DEADLINE_OVERDUE";

    public static final String INVOICE_CREATED = "INVOICE_CREATED";
    public static final String INVOICE_ISSUED = "INVOICE_ISSUED";
    public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String INVOICE_OVERDUE = "INVOICE_OVERDUE";

    public static final String CONSULTATION_CREATED = "CONSULTATION_CREATED";
    public static final String CONSULTATION_ASSIGNED = "CONSULTATION_ASSIGNED";
    public static final String CONSULTATION_ANSWERED = "CONSULTATION_ANSWERED";
    public static final String CONSULTATION_CLOSED = "CONSULTATION_CLOSED";
    public static final String CONSULTATION_CANCELLED = "CONSULTATION_CANCELLED";

    public static final String APPOINTMENT_CREATED = "APPOINTMENT_CREATED";
    public static final String APPOINTMENT_UPDATED = "APPOINTMENT_UPDATED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String APPOINTMENT_REMINDER = "APPOINTMENT_REMINDER";

    public static final String JUDICIAL_CASE_REGISTERED = "JUDICIAL_CASE_REGISTERED";
    public static final String JUDICIAL_STATUS_CHANGED = "JUDICIAL_STATUS_CHANGED";
    public static final String HEARING_CREATED = "HEARING_CREATED";
    public static final String HEARING_STATUS_CHANGED = "HEARING_STATUS_CHANGED";

    public static final List<String> ALL = List.of(
            CASE_CREATED,
            DEADLINE_CREATED, DEADLINE_APPROACHING, DEADLINE_OVERDUE,
            INVOICE_CREATED, INVOICE_ISSUED, PAYMENT_RECEIVED, INVOICE_OVERDUE,
            CONSULTATION_CREATED, CONSULTATION_ASSIGNED, CONSULTATION_ANSWERED,
            CONSULTATION_CLOSED, CONSULTATION_CANCELLED,
            APPOINTMENT_CREATED, APPOINTMENT_UPDATED, APPOINTMENT_CANCELLED, APPOINTMENT_REMINDER,
            JUDICIAL_CASE_REGISTERED, JUDICIAL_STATUS_CHANGED, HEARING_CREATED, HEARING_STATUS_CHANGED);

    private EventTypes() {
    }
}
