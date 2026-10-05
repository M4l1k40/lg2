package com.legalflow.notification.dto;

import com.legalflow.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class NotificationCreateRequest {

    @NotNull
    private NotificationType type;

    @NotBlank
    @Size(max = 160)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String message;

    @Size(max = 160)
    private String subject;

    @Size(max = 64)
    private String referenceType;

    private UUID referenceId;

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }
}
