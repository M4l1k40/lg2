package com.legalflow.document.service;

import com.legalflow.document.domain.Document;
import com.legalflow.document.domain.DocumentVisibility;
import com.legalflow.document.dto.DocumentRequest;
import com.legalflow.document.integration.CaseServiceOwnershipVerifier;
import com.legalflow.document.repository.DocumentRepository;
import com.legalflow.document.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    public DocumentService(DocumentRepository documentRepository,
                           CaseServiceOwnershipVerifier caseServiceOwnershipVerifier) {
        this.documentRepository = documentRepository;
        this.caseServiceOwnershipVerifier = caseServiceOwnershipVerifier;
    }

    @Transactional
    public Document createDocument(DocumentRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());
        Document document = new Document(lawFirmId, request.getCaseId(), TenantContext.currentUserIdOrNull(),
                request.getFileName(), request.getFileType(), request.getFileSize(), request.getStorageKey(),
                request.getDescription(), request.getVisibility());
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsByCase(UUID caseId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(caseId);
        return documentRepository.findAllByLawFirmIdAndCaseIdOrderByCreatedAtDesc(lawFirmId, caseId).stream()
                .filter(this::isVisibleByRole)
                .toList();
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID id) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        Document document = documentRepository.findByIdAndLawFirmId(id, lawFirmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found: " + id));
        if (!TenantContext.isStaff()) {
            // Un CLIENT n'accède qu'aux documents partagés d'un dossier qui lui appartient.
            try {
                caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(document.getCaseId());
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id);
                }
                throw exception;
            }
        }
        if (!isVisibleByRole(document)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id);
        }
        return document;
    }

    /**
     * Visibilité d'un document (le dossier a déjà été vérifié) :
     * CASE_MEMBERS : personnel du cabinet et client du dossier ;
     * LAWYER_ONLY : ADMIN et LAWYER ; PRIVATE : son auteur et ADMIN. Le CLIENT ne voit que CASE_MEMBERS.
     */
    private boolean isVisibleByRole(Document document) {
        DocumentVisibility visibility = document.getVisibility();
        if (!TenantContext.isStaff()) {
            return visibility == DocumentVisibility.CASE_MEMBERS;
        }
        if (visibility == DocumentVisibility.LAWYER_ONLY) {
            return TenantContext.hasRole("ADMIN") || TenantContext.isLawyer();
        }
        if (visibility == DocumentVisibility.PRIVATE) {
            UUID currentUserId = TenantContext.currentUserIdOrNull();
            boolean uploader = document.getUploadedBy() != null && document.getUploadedBy().equals(currentUserId);
            return uploader || TenantContext.hasRole("ADMIN");
        }
        return true;
    }

    @Transactional
    public Document updateDocument(UUID id, DocumentRequest request) {
        Document document = getDocument(id);
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());
        document.setCaseId(request.getCaseId());
        document.setFileName(request.getFileName());
        document.setFileType(request.getFileType());
        document.setFileSize(request.getFileSize());
        document.setStorageKey(request.getStorageKey());
        document.setDescription(request.getDescription());
        document.setVisibility(request.getVisibility());
        return documentRepository.save(document);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document document = getDocument(id);
        documentRepository.delete(document);
    }
}
