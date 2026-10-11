package com.legalflow.consultation.service;

import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;
import com.legalflow.consultation.dto.AnswerConsultationRequest;
import com.legalflow.consultation.dto.AssignConsultationRequest;
import com.legalflow.consultation.dto.CreateConsultationRequest;
import com.legalflow.consultation.dto.UpdateConsultationRequest;
import com.legalflow.consultation.integration.ClientServiceOwnershipVerifier;
import com.legalflow.consultation.repository.ConsultationRepository;
import com.legalflow.consultation.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Map.entry;

@Service
public class ConsultationService {

    private static final Map<ConsultationStatus, Set<ConsultationStatus>> TRANSITIONS = Map.ofEntries(
            entry(ConsultationStatus.PENDING, Set.of(ConsultationStatus.ASSIGNED, ConsultationStatus.CANCELLED)),
            entry(ConsultationStatus.ASSIGNED, Set.of(ConsultationStatus.IN_PROGRESS, ConsultationStatus.CANCELLED)),
            entry(ConsultationStatus.IN_PROGRESS, Set.of(ConsultationStatus.ANSWERED, ConsultationStatus.CANCELLED)),
            entry(ConsultationStatus.ANSWERED, Set.of(ConsultationStatus.CLOSED)),
            entry(ConsultationStatus.CLOSED, Set.of()),
            entry(ConsultationStatus.CANCELLED, Set.of())
    );

    private final ConsultationRepository consultationRepository;
    private final ClientServiceOwnershipVerifier clientServiceOwnershipVerifier;

    public ConsultationService(ConsultationRepository consultationRepository,
                              ClientServiceOwnershipVerifier clientServiceOwnershipVerifier) {
        this.consultationRepository = consultationRepository;
        this.clientServiceOwnershipVerifier = clientServiceOwnershipVerifier;
    }

    /**
     * ADMIN/SECRETARY : toutes les consultations du cabinet.
     * LAWYER : les demandes à prendre en charge et celles qui lui sont assignées.
     * CLIENT : uniquement les siennes.
     */
    @Transactional(readOnly = true)
    public List<Consultation> getConsultations(UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);
        if (TenantContext.isOfficeStaff()) {
            return consultationRepository.findAllByLawFirmIdOrderByCreatedAtDesc(lawFirmId);
        }
        if (TenantContext.isLawyer()) {
            return consultationRepository.findVisibleToLawyer(lawFirmId, TenantContext.requireUserId());
        }
        return consultationRepository.findAllByLawFirmIdAndClientIdOrderByCreatedAtDesc(
                lawFirmId, TenantContext.requireClientId());
    }

    @Transactional
    public Consultation createConsultation(CreateConsultationRequest request, UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);

        UUID clientId;
        if (TenantContext.isOfficeStaff() || TenantContext.isLawyer()) {
            if (request == null || request.getClientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is required.");
            }
            clientId = request.getClientId();
        } else {
            // Un CLIENT ne peut créer une demande que pour lui-même : son identifiant vient de son token.
            clientId = TenantContext.requireClientId();
            if (request != null && request.getClientId() != null && !clientId.equals(request.getClientId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A client can only create consultations for themselves.");
            }
        }
        if (request == null || request.getSubject() == null || request.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject is required.");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description is required.");
        }

        clientServiceOwnershipVerifier.verifyClientBelongsToCurrentTenant(clientId);

        Consultation consultation = new Consultation(
                lawFirmId,
                clientId,
                null,
                request.getSubject().trim(),
                request.getDescription().trim(),
                ConsultationStatus.PENDING,
                null
        );
        return consultationRepository.save(consultation);
    }

    @Transactional(readOnly = true)
    public Consultation getConsultation(UUID id) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        Consultation consultation = consultationRepository.findByIdAndLawFirmId(id, lawFirmId)
                .orElseThrow(() -> notFound(id));
        requireVisibleToCurrentUser(consultation, id);
        return consultation;
    }

    @Transactional
    public Consultation updateConsultation(UUID id, UpdateConsultationRequest request) {
        Consultation consultation = getConsultation(id);
        if (consultation.getStatus() == ConsultationStatus.CLOSED
                || consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consultation cannot be updated in " + consultation.getStatus() + " state.");
        }
        if (!TenantContext.isOfficeStaff()) {
            rejectLawyerOnly("edit");
            if (consultation.getStatus() != ConsultationStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Consultation can only be edited by its client while PENDING.");
            }
        }

        consultation.setSubject(request.getSubject().trim());
        consultation.setDescription(request.getDescription().trim());
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation assignConsultation(UUID id, AssignConsultationRequest request) {
        Consultation consultation = getConsultation(id);
        ensureTransitionAllowed(consultation, ConsultationStatus.ASSIGNED, "assign");
        if (request == null || request.getLawyerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lawyerId is required.");
        }
        if (!TenantContext.isOfficeStaff()) {
            // Un avocat ne peut que se prendre lui-même en charge.
            if (!TenantContext.isLawyer() || !TenantContext.requireUserId().equals(request.getLawyerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A lawyer can only assign a consultation to themselves.");
            }
        }
        consultation.setLawyerId(request.getLawyerId());
        consultation.setStatus(ConsultationStatus.ASSIGNED);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation startConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
        requireAssignedLawyer(consultation, "start");
        ensureTransitionAllowed(consultation, ConsultationStatus.IN_PROGRESS, "start");
        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation answerConsultation(UUID id, AnswerConsultationRequest request) {
        Consultation consultation = getConsultation(id);
        requireAssignedLawyer(consultation, "answer");
        ensureTransitionAllowed(consultation, ConsultationStatus.ANSWERED, "answer");
        if (request == null || request.getAnswer() == null || request.getAnswer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answer is required.");
        }
        consultation.setAnswer(request.getAnswer().trim());
        consultation.setAnsweredAt(LocalDateTime.now());
        consultation.setStatus(ConsultationStatus.ANSWERED);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation closeConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
        if (!TenantContext.isOfficeStaff()) {
            requireAssignedLawyer(consultation, "close");
        }
        ensureTransitionAllowed(consultation, ConsultationStatus.CLOSED, "close");
        consultation.setStatus(ConsultationStatus.CLOSED);
        consultation.setClosedAt(LocalDateTime.now());
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation cancelConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
        rejectLawyerOnly("cancel");
        ensureTransitionAllowed(consultation, ConsultationStatus.CANCELLED, "cancel");
        consultation.setStatus(ConsultationStatus.CANCELLED);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public void deleteConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
        rejectLawyerOnly("delete");
        if (consultation.getStatus() == ConsultationStatus.CLOSED
                || consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consultation is already " + consultation.getStatus() + ".");
        }
        consultation.setStatus(ConsultationStatus.CANCELLED);
        consultationRepository.save(consultation);
    }

    private void rejectMismatchedTenant(UUID requestedLawFirmId, UUID authenticatedLawFirmId) {
        if (requestedLawFirmId != null && !authenticatedLawFirmId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Requested lawFirmId does not match the authenticated law firm.");
        }
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found: " + id);
    }

    /** 404 (et non 403) pour ne rien révéler de l'existence des consultations des autres. */
    private void requireVisibleToCurrentUser(Consultation consultation, UUID id) {
        if (TenantContext.isOfficeStaff()) {
            return;
        }
        if (TenantContext.isLawyer()) {
            boolean unassignedRequest = consultation.getStatus() == ConsultationStatus.PENDING;
            if (unassignedRequest || TenantContext.requireUserId().equals(consultation.getLawyerId())) {
                return;
            }
            throw notFound(id);
        }
        if (!TenantContext.requireClientId().equals(consultation.getClientId())) {
            throw notFound(id);
        }
    }

    private void requireAssignedLawyer(Consultation consultation, String action) {
        if (!TenantContext.isLawyer() || !TenantContext.requireUserId().equals(consultation.getLawyerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the assigned lawyer can " + action + " this consultation.");
        }
    }

    private void rejectLawyerOnly(String action) {
        if (!TenantContext.isOfficeStaff() && TenantContext.isLawyer()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lawyers cannot " + action + " consultations.");
        }
    }

    private void ensureTransitionAllowed(Consultation consultation, ConsultationStatus targetStatus, String action) {
        ConsultationStatus currentStatus = consultation.getStatus();
        Set<ConsultationStatus> allowed = TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consultation status transition from " + currentStatus + " to " + targetStatus + " is not allowed for " + action + ".");
        }
    }
}
