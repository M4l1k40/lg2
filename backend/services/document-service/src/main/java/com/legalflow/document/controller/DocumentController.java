package com.legalflow.document.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.legalflow.document.domain.Document;
import com.legalflow.document.dto.DocumentRequest;
import com.legalflow.document.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER')")
    public Document createDocument(@Valid @RequestBody DocumentRequest request) {
        return documentService.createDocument(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public Document getDocument(@PathVariable UUID id) {
        return documentService.getDocument(id);
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public List<Document> getDocumentsByCase(@PathVariable UUID caseId) {
        return documentService.getDocumentsByCase(caseId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER')")
    public Document updateDocument(@PathVariable UUID id, @Valid @RequestBody DocumentRequest request) {
        return documentService.updateDocument(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER')")
    public void deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
    }
}
