package com.legalflow.case_service.controller;

import com.legalflow.case_service.domain.CaseEvent;
import com.legalflow.case_service.domain.LegalCase;
import com.legalflow.case_service.service.CaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    public List<LegalCase> findAll(@RequestParam(required = false) UUID lawFirmId) {
        return caseService.findAllByLawFirmId(lawFirmId);
    }

    @GetMapping("/{id}")
    public LegalCase findById(@PathVariable UUID id) {
        return caseService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegalCase create(@Valid @RequestBody LegalCase legalCase) {
        return caseService.createCase(legalCase);
    }

    @GetMapping("/{id}/events")
    public List<CaseEvent> getEvents(@PathVariable UUID id) {
        return caseService.findEventsByCaseId(id);
    }
}
