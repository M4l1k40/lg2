package com.legalflow.case_service.service;

import com.legalflow.case_service.domain.CaseEvent;
import com.legalflow.case_service.domain.CaseStatus;
import com.legalflow.case_service.domain.LegalCase;
import com.legalflow.case_service.integration.ClientServiceOwnershipVerifier;
import com.legalflow.case_service.repository.CaseEventRepository;
import com.legalflow.case_service.repository.LegalCaseRepository;
import com.legalflow.case_service.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CaseService {

    private final LegalCaseRepository legalCaseRepository;
    private final CaseEventRepository caseEventRepository;
    private final ClientServiceOwnershipVerifier clientServiceOwnershipVerifier;

    public CaseService(LegalCaseRepository legalCaseRepository,
                       CaseEventRepository caseEventRepository,
                       ClientServiceOwnershipVerifier clientServiceOwnershipVerifier) {
        this.legalCaseRepository = legalCaseRepository;
        this.caseEventRepository = caseEventRepository;
        this.clientServiceOwnershipVerifier = clientServiceOwnershipVerifier;
    }

    @Transactional(readOnly = true)
    public List<LegalCase> findAllByLawFirmId(UUID requestedLawFirmId) {
        UUID tenantId = TenantContext.requireLawFirmId();
        if (requestedLawFirmId != null && !tenantId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "lawFirmId query parameter does not match your tenant.");
        }
        if (!TenantContext.isStaff()) {
            // Un utilisateur CLIENT ne voit que ses propres dossiers
            return legalCaseRepository.findAllByLawFirmIdAndClientId(tenantId, TenantContext.requireClientId());
        }
        return legalCaseRepository.findAllByLawFirmId(tenantId);
    }

    @Transactional(readOnly = true)
    public LegalCase findById(UUID id) {
        UUID tenantId = TenantContext.requireLawFirmId();
        LegalCase legalCase = legalCaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + id));
        if (!tenantId.equals(legalCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + id);
        }
        requireVisibleToCurrentUser(legalCase, id);
        return legalCase;
    }

    @Transactional(readOnly = true)
    public List<CaseEvent> findEventsByCaseId(UUID caseId) {
        UUID tenantId = TenantContext.requireLawFirmId();
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId));
        if (!tenantId.equals(legalCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId);
        }
        requireVisibleToCurrentUser(legalCase, caseId);
        return caseEventRepository.findAllByCaseIdOrderByCreatedAtAsc(caseId);
    }

    @Transactional
    public LegalCase save(LegalCase legalCase) {
        UUID tenantId = TenantContext.requireLawFirmId();
        if (legalCase.getLawFirmId() != null && !tenantId.equals(legalCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "lawFirmId in request body does not match your tenant.");
        }
        legalCase.setLawFirmId(tenantId);
        clientServiceOwnershipVerifier.verifyClientBelongsToCurrentTenant(legalCase.getClientId());
        // TODO: validate lawyerId once a user/lawyer directory service exists
        LegalCase savedCase = legalCaseRepository.save(legalCase);
        CaseEvent event = new CaseEvent(savedCase.getId(), "CASE_CREATED", "Case created", "system");
        savedCase.addEvent(event);
        caseEventRepository.save(event);
        return savedCase;
    }

    @Transactional
    public LegalCase createCase(LegalCase legalCase) {
        legalCase.setStatus(CaseStatus.OPEN);
        return save(legalCase);
    }

    /** Un CLIENT ne peut accéder qu'à ses propres dossiers (404 sinon, pour ne rien révéler). */
    private void requireVisibleToCurrentUser(LegalCase legalCase, UUID caseId) {
        if (!TenantContext.isStaff() && !TenantContext.requireClientId().equals(legalCase.getClientId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId);
        }
    }
}
