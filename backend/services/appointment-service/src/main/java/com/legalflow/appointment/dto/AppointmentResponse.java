package com.legalflow.appointment.dto;

import com.legalflow.appointment.domain.Appointment;
import com.legalflow.appointment.domain.AppointmentStatus;
import com.legalflow.appointment.domain.AppointmentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID lawFirmId,
        UUID caseId,
        UUID clientId,
        UUID lawyerId,
        LocalDateTime dateTime,
        AppointmentType type,
        AppointmentStatus status,
        String location,
        String notes,
        LocalDateTime createdAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(), appointment.getLawFirmId(), appointment.getCaseId(),
                appointment.getClientId(), appointment.getLawyerId(), appointment.getDateTime(),
                appointment.getType(), appointment.getStatus(), appointment.getLocation(),
                appointment.getNotes(), appointment.getCreatedAt());
    }
}
