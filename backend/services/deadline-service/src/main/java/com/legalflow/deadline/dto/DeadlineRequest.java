package com.legalflow.deadline.dto;

import com.legalflow.deadline.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;
import java.util.UUID;

public class DeadlineRequest {

    private UUID lawFirmId;

    @NotNull
    private UUID caseId;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull
    @Future
    private LocalDateTime dueDate;

    @NotNull
    private Priority priority;

    public UUID getLawFirmId() { return lawFirmId; }
    public void setLawFirmId(UUID lawFirmId) { this.lawFirmId = lawFirmId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
}
