package com.legalflow.consultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consultations", indexes = {
        @Index(name = "idx_consultations_law_firm_id", columnList = "law_firm_id"),
        @Index(name = "idx_consultations_client_id", columnList = "client_id"),
        @Index(name = "idx_consultations_status", columnList = "status"),
        @Index(name = "idx_consultations_lawyer_id", columnList = "lawyer_id")
})
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "law_firm_id", nullable = false)
    private UUID lawFirmId;

    @NotNull
    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "lawyer_id")
    private UUID lawyerId;

    @NotBlank
    @Size(min = 3, max = 255)
    @Column(nullable = false, length = 255)
    private String subject;

    @NotBlank
    @Size(min = 10, max = 4000)
    @Column(nullable = false, length = 4000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConsultationStatus status;

    @Column(length = 4000)
    private String answer;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected Consultation() {
    }

    public Consultation(UUID lawFirmId, UUID clientId, UUID lawyerId, String subject,
                       String description, ConsultationStatus status, String answer) {
        this.lawFirmId = lawFirmId;
        this.clientId = clientId;
        this.lawyerId = lawyerId;
        this.subject = subject;
        this.description = description;
        this.status = status;
        this.answer = answer;
    }

    public UUID getId() { return id; }
    public UUID getLawFirmId() { return lawFirmId; }
    public void setLawFirmId(UUID lawFirmId) { this.lawFirmId = lawFirmId; }
    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }
    public UUID getLawyerId() { return lawyerId; }
    public void setLawyerId(UUID lawyerId) { this.lawyerId = lawyerId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ConsultationStatus getStatus() { return status; }
    public void setStatus(ConsultationStatus status) { this.status = status; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
