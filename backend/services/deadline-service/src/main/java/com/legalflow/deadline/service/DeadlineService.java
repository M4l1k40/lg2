package com.legalflow.deadline.service;

import com.legalflow.deadline.domain.Deadline;
import com.legalflow.deadline.domain.DeadlineStatus;
import com.legalflow.deadline.domain.Priority;
import com.legalflow.deadline.dto.DeadlineRequest;
import com.legalflow.deadline.integration.CaseServiceOwnershipVerifier;
import com.legalflow.deadline.repository.DeadlineRepository;
import com.legalflow.deadline.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeadlineService {

    private static final Map<DeadlineStatus, Set<DeadlineStatus>> STATUS_TRANSITIONS = Map.of(
            DeadlineStatus.PENDING, Set.of(DeadlineStatus.COMPLETED, DeadlineStatus.CANCELLED, DeadlineStatus.OVERDUE),
            DeadlineStatus.OVERDUE, Set.of(DeadlineStatus.COMPLETED, DeadlineStatus.CANCELLED),
            DeadlineStatus.COMPLETED, Set.of(),
            DeadlineStatus.CANCELLED, Set.of()
    );

    private final DeadlineRepository deadlineRepository;
    private final CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    public DeadlineService(DeadlineRepository deadlineRepository,
                          CaseServiceOwnershipVerifier caseServiceOwnershipVerifier) {
        this.deadlineRepository = deadlineRepository;
        this.caseServiceOwnershipVerifier = caseServiceOwnershipVerifier;
    }

    @Transactional(readOnly = true)
    public List<Deadline> getDeadlines(UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);
        return deadlineRepository.findAllByLawFirmIdOrderByDueDateAsc(lawFirmId);
    }

    @Transactional(readOnly = true)
    public List<Deadline> getApproachingDeadlines(UUID requestedLawFirmId, int days) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);
        if (days < 1 || days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "days must be between 1 and 365.");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return deadlineRepository.findAllByLawFirmIdAndStatusAndDueDateBetweenOrderByDueDateAsc(
                lawFirmId, DeadlineStatus.PENDING, now, now.plusDays(days));
    }

    @Transactional
    public Deadline createDeadline(DeadlineRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(request.getLawFirmId(), lawFirmId);
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());

        Deadline deadline = new Deadline(
                lawFirmId,
                request.getCaseId(),
                request.getTitle(),
                request.getDescription(),
                request.getDueDate(),
                request.getPriority(),
                DeadlineStatus.PENDING,
                TenantContext.currentUserSubjectOrNull());
        return deadlineRepository.save(deadline);
    }

    @Transactional(readOnly = true)
    public Deadline getDeadline(UUID id) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        return deadlineRepository.findByIdAndLawFirmId(id, lawFirmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Deadline not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Deadline> getDeadlinesByCase(UUID caseId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(caseId);
        return deadlineRepository.findAllByLawFirmIdAndCaseIdOrderByDueDateAsc(lawFirmId, caseId);
    }

    @Transactional
    public Deadline updateDeadline(UUID id, DeadlineRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(request.getLawFirmId(), lawFirmId);
        Deadline deadline = getDeadline(id);
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());

        deadline.setCaseId(request.getCaseId());
        deadline.setTitle(request.getTitle());
        deadline.setDescription(request.getDescription());
        deadline.setDueDate(request.getDueDate());
        deadline.setPriority(request.getPriority());

        return deadlineRepository.save(deadline);
    }

    @Transactional
    public Deadline updateStatus(UUID id, String requestedStatus) {
        Deadline deadline = getDeadline(id);
        DeadlineStatus currentStatus = deadline.getStatus();
        DeadlineStatus nextStatus;
        try {
            nextStatus = DeadlineStatus.valueOf(requestedStatus.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Unknown deadline status: " + requestedStatus);
        }
        Set<DeadlineStatus> allowedTransitions = STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        if (!allowedTransitions.contains(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Deadline status transition from " + currentStatus + " to " + nextStatus + " is not allowed.");
        }

        deadline.setStatus(nextStatus);
        return deadlineRepository.save(deadline);
    }

    @Transactional
    public void deleteDeadline(UUID id) {
        Deadline deadline = getDeadline(id);
        deadlineRepository.delete(deadline);
    }

    private void rejectMismatchedTenant(UUID requestedLawFirmId, UUID authenticatedLawFirmId) {
        if (requestedLawFirmId != null && !authenticatedLawFirmId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Requested lawFirmId does not match the authenticated law firm.");
        }
    }
}
