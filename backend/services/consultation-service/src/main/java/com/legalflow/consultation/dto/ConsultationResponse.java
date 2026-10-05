package com.legalflow.consultation.dto;

import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class ConsultationResponse {

    private UUID id;
    private UUID lawFirmId;
    private UUID clientId;
    private UUID lawyerId;
    private String subject;
    private String description;
    private ConsultationStatus status;
    private String answer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime answeredAt;
    private LocalDateTime closedAt;

    public ConsultationResponse() {
    }

    public ConsultationResponse(UUID id, UUID lawFirmId, UUID clientId, UUID lawyerId, String subject,
                               String description, ConsultationStatus status, String answer,
                               LocalDateTime createdAt, LocalDateTime updatedAt,
                               LocalDateTime answeredAt, LocalDateTime closedAt) {
        this.id = id;
        this.lawFirmId = lawFirmId;
        this.clientId = clientId;
        this.lawyerId = lawyerId;
        this.subject = subject;
        this.description = description;
        this.status = status;
        this.answer = answer;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.answeredAt = answeredAt;
        this.closedAt = closedAt;
    }

    public static ConsultationResponse from(Consultation consultation) {
        return new ConsultationResponse(
                consultation.getId(),
                consultation.getLawFirmId(),
                consultation.getClientId(),
                consultation.getLawyerId(),
                consultation.getSubject(),
                consultation.getDescription(),
                consultation.getStatus(),
                consultation.getAnswer(),
                consultation.getCreatedAt(),
                consultation.getUpdatedAt(),
                consultation.getAnsweredAt(),
                consultation.getClosedAt()
        );
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
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
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
