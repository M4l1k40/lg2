package com.legalflow.document.service;

import com.legalflow.document.domain.Document;
import com.legalflow.document.domain.DocumentVisibility;
import com.legalflow.document.dto.DocumentRequest;
import com.legalflow.document.dto.DocumentStatsResponse;
import com.legalflow.document.integration.CaseServiceOwnershipVerifier;
import com.legalflow.document.repository.DocumentRepository;
import com.legalflow.document.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        if (!TenantContext.isOfficeStaff()) {
            // Un avocat ou un client n'accède qu'aux documents d'un dossier qu'il peut voir (le case-service décide).
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

    /**
     * Comptage pour les tableaux de bord. Seuls les documents que l'appelant a le droit de voir sont comptés
     * (mêmes règles de visibilité que la lecture) : un total ne doit pas révéler l'existence de documents
     * réservés. Les bornes sont des instants fournis par le navigateur (minuit local, il y a 7 jours).
     */
    @Transactional(readOnly = true)
    public DocumentStatsResponse getStats(Instant weekStart, Instant todayStart) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        List<Document> visible = documentRepository.findAllByLawFirmIdOrderByCreatedAtDesc(lawFirmId).stream()
                .filter(this::isVisibleByRole)
                .toList();
        // createdAt est un LocalDateTime enregistré dans le fuseau de la JVM : on convertit les bornes dans le même fuseau.
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime week = LocalDateTime.ofInstant(weekStart, zone);
        LocalDateTime today = LocalDateTime.ofInstant(todayStart, zone);
        return new DocumentStatsResponse(visible.size(), createdSince(visible, week), createdSince(visible, today));
    }

    private static long createdSince(List<Document> documents, LocalDateTime since) {
        return documents.stream()
                .filter(document -> document.getCreatedAt() != null && !document.getCreatedAt().isBefore(since))
                .count();
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
