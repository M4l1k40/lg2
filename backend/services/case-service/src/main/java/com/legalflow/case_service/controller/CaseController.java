package com.legalflow.case_service.controller;

import com.legalflow.case_service.domain.CaseEvent;
import com.legalflow.case_service.domain.LegalCase;
import com.legalflow.case_service.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    /** Personnel : tous les dossiers du cabinet. Client : uniquement ses propres dossiers. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY','CLIENT')")
    public List<LegalCase> findAll(@RequestParam(required = false) UUID lawFirmId) {
        return caseService.findAllByLawFirmId(lawFirmId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY','CLIENT')")
    public LegalCase findById(@PathVariable UUID id) {
        return caseService.findById(id);
    }

    /** Création de dossier : réservée au personnel. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY')")
    public LegalCase create(@Valid @RequestBody LegalCase legalCase) {
        return caseService.createCase(legalCase);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("hasAnyRole('ADMIN','LAWYER','SECRETARY','CLIENT')")
    public List<CaseEvent> getEvents(@PathVariable UUID id) {
        return caseService.findEventsByCaseId(id);
    }
}
