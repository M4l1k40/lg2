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

    @Transactional(readOnly = true)
    public List<Consultation> getConsultations(UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);
        return consultationRepository.findAllByLawFirmIdOrderByCreatedAtDesc(lawFirmId);
    }

    @Transactional
    public Consultation createConsultation(CreateConsultationRequest request, UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);

        if (request == null || request.getClientId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is required.");
        }
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject is required.");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "description is required.");
        }

        clientServiceOwnershipVerifier.verifyClientBelongsToCurrentTenant(request.getClientId());

        Consultation consultation = new Consultation(
                lawFirmId,
                request.getClientId(),
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
        return consultationRepository.findByIdAndLawFirmId(id, lawFirmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Consultation not found: " + id));
    }

    @Transactional
    public Consultation updateConsultation(UUID id, UpdateConsultationRequest request) {
        Consultation consultation = getConsultation(id);
        if (consultation.getStatus() == ConsultationStatus.CLOSED
                || consultation.getStatus() == ConsultationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consultation cannot be updated in " + consultation.getStatus() + " state.");
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
        consultation.setLawyerId(request.getLawyerId());
        consultation.setStatus(ConsultationStatus.ASSIGNED);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation startConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
        ensureTransitionAllowed(consultation, ConsultationStatus.IN_PROGRESS, "start");
        consultation.setStatus(ConsultationStatus.IN_PROGRESS);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation answerConsultation(UUID id, AnswerConsultationRequest request) {
        Consultation consultation = getConsultation(id);
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
        ensureTransitionAllowed(consultation, ConsultationStatus.CLOSED, "close");
        consultation.setStatus(ConsultationStatus.CLOSED);
        consultation.setClosedAt(LocalDateTime.now());
        return consultationRepository.save(consultation);
    }

    @Transactional
    public Consultation cancelConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
        ensureTransitionAllowed(consultation, ConsultationStatus.CANCELLED, "cancel");
        consultation.setStatus(ConsultationStatus.CANCELLED);
        return consultationRepository.save(consultation);
    }

    @Transactional
    public void deleteConsultation(UUID id) {
        Consultation consultation = getConsultation(id);
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

    private void ensureTransitionAllowed(Consultation consultation, ConsultationStatus targetStatus, String action) {
        ConsultationStatus currentStatus = consultation.getStatus();
        Set<ConsultationStatus> allowed = TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Consultation status transition from " + currentStatus + " to " + targetStatus + " is not allowed for " + action + ".");
        }
    }
}
