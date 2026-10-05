package com.legalflow.consultation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AssignConsultationRequest {

    @NotNull
    private UUID lawyerId;

    public UUID getLawyerId() { return lawyerId; }
    public void setLawyerId(UUID lawyerId) { this.lawyerId = lawyerId; }
}
