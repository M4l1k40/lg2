package com.legalflow.document.dto;

import com.legalflow.document.domain.DocumentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public class DocumentRequest {

    @NotNull
    private UUID caseId;

    @NotBlank
    private String fileName;

    @NotBlank
    private String fileType;

    @NotNull
    @Positive
    private Long fileSize;

    @NotBlank
    private String storageKey;

    private String description;

    @NotNull
    private DocumentVisibility visibility;

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public DocumentVisibility getVisibility() { return visibility; }
    public void setVisibility(DocumentVisibility visibility) { this.visibility = visibility; }
}
