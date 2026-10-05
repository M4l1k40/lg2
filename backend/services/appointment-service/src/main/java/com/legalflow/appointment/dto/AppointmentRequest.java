package com.legalflow.appointment.dto;

import com.legalflow.appointment.domain.AppointmentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public class AppointmentRequest {

    private UUID lawFirmId;

    private UUID caseId;

    @NotNull
    private UUID clientId;

    @NotNull
    private UUID lawyerId;

    @NotNull
    @Future
    private LocalDateTime dateTime;

    @NotNull
    private AppointmentType type;

    @Size(max = 500)
    private String location;

    @Size(max = 2000)
    private String notes;

    public UUID getLawFirmId() { return lawFirmId; }
    public void setLawFirmId(UUID lawFirmId) { this.lawFirmId = lawFirmId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public UUID getLawyerId() { return lawyerId; }
    public void setLawyerId(UUID lawyerId) { this.lawyerId = lawyerId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public AppointmentType getType() { return type; }
    public void setType(AppointmentType type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
