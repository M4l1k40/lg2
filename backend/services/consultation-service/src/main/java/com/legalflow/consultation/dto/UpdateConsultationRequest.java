package com.legalflow.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateConsultationRequest {

    @NotBlank
    @Size(min = 3, max = 255)
    private String subject;

    @NotBlank
    @Size(min = 10, max = 4000)
    private String description;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
