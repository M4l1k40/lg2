package com.legalflow.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointments_law_firm_id", columnList = "law_firm_id"),
        @Index(name = "idx_appointments_law_firm_case", columnList = "law_firm_id,case_id"),
        @Index(name = "idx_appointments_law_firm_datetime", columnList = "law_firm_id,date_time")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "law_firm_id", nullable = false)
    private UUID lawFirmId;

    @Column(name = "case_id")
    private UUID caseId;

    @NotNull
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @NotNull
    @Column(name = "lawyer_id", nullable = false)
    private UUID lawyerId;

    @NotNull
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentStatus status;

    @Column(length = 500)
    private String location;

    @Column(length = 2000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Appointment() {
    }

    public Appointment(UUID lawFirmId, UUID caseId, UUID clientId, UUID lawyerId,
                       LocalDateTime dateTime, AppointmentType type, AppointmentStatus status,
                       String location, String notes) {
        this.lawFirmId = lawFirmId;
        this.caseId = caseId;
        this.clientId = clientId;
        this.lawyerId = lawyerId;
        this.dateTime = dateTime;
        this.type = type;
        this.status = status;
        this.location = location;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public UUID getLawFirmId() { return lawFirmId; }
    public UUID getCaseId() { return caseId; }
    public UUID getClientId() { return clientId; }
    public UUID getLawyerId() { return lawyerId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public AppointmentType getType() { return type; }
    public AppointmentStatus getStatus() { return status; }
    public String getLocation() { return location; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
}
