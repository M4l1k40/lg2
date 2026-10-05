package com.legalflow.judicial_service.service;

import com.legalflow.judicial_service.domain.Court;
import com.legalflow.judicial_service.domain.Hearing;
import com.legalflow.judicial_service.domain.HearingRequest;
import com.legalflow.judicial_service.domain.HearingStatus;
import com.legalflow.judicial_service.domain.Judge;
import com.legalflow.judicial_service.domain.JudicialCase;
import com.legalflow.judicial_service.domain.JudicialCaseStatus;
import com.legalflow.judicial_service.domain.JudicialEvent;
import com.legalflow.judicial_service.domain.JudgeRequest;
import com.legalflow.judicial_service.integration.CaseServiceOwnershipVerifier;
import com.legalflow.judicial_service.repository.CourtRepository;
import com.legalflow.judicial_service.repository.HearingRepository;
import com.legalflow.judicial_service.repository.JudgeRepository;
import com.legalflow.judicial_service.repository.JudicialCaseRepository;
import com.legalflow.judicial_service.repository.JudicialEventRepository;
import com.legalflow.judicial_service.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class JudicialService {

    private static final Map<JudicialCaseStatus, Set<JudicialCaseStatus>> JUDICIAL_CASE_TRANSITIONS = Map.of(
        JudicialCaseStatus.REGISTERED, Set.of(JudicialCaseStatus.IN_PROGRESS, JudicialCaseStatus.HEARING_SCHEDULED),
        JudicialCaseStatus.IN_PROGRESS, Set.of(JudicialCaseStatus.HEARING_SCHEDULED, JudicialCaseStatus.DELIBERATION, JudicialCaseStatus.CLOSED),
        JudicialCaseStatus.HEARING_SCHEDULED, Set.of(JudicialCaseStatus.DELIBERATION, JudicialCaseStatus.CLOSED),
        JudicialCaseStatus.DELIBERATION, Set.of(JudicialCaseStatus.JUDGMENT_ISSUED, JudicialCaseStatus.CLOSED),
        JudicialCaseStatus.JUDGMENT_ISSUED, Set.of(JudicialCaseStatus.CLOSED),
        JudicialCaseStatus.CLOSED, Set.of());

    private static final Map<HearingStatus, Set<HearingStatus>> HEARING_TRANSITIONS = Map.of(
        HearingStatus.SCHEDULED, Set.of(HearingStatus.COMPLETED, HearingStatus.POSTPONED, HearingStatus.CANCELLED),
        HearingStatus.COMPLETED, Set.of(),
        HearingStatus.POSTPONED, Set.of(HearingStatus.SCHEDULED, HearingStatus.CANCELLED),
        HearingStatus.CANCELLED, Set.of());

    private final CourtRepository courtRepository;
    private final JudgeRepository judgeRepository;
    private final JudicialCaseRepository judicialCaseRepository;
    private final HearingRepository hearingRepository;
    private final JudicialEventRepository judicialEventRepository;
    private final CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    public JudicialService(CourtRepository courtRepository,
                          JudgeRepository judgeRepository,
                          JudicialCaseRepository judicialCaseRepository,
                          HearingRepository hearingRepository,
                          JudicialEventRepository judicialEventRepository,
                          CaseServiceOwnershipVerifier caseServiceOwnershipVerifier) {
        this.courtRepository = courtRepository;
        this.judgeRepository = judgeRepository;
        this.judicialCaseRepository = judicialCaseRepository;
        this.hearingRepository = hearingRepository;
        this.judicialEventRepository = judicialEventRepository;
        this.caseServiceOwnershipVerifier = caseServiceOwnershipVerifier;
    }

    @Transactional(readOnly = true)
    public List<Court> findAllCourts() {
        return courtRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Court findCourtById(UUID id) {
        return courtRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found: " + id));
    }

    @Transactional
    public Court createCourt(Court court) {
        return courtRepository.save(court);
    }

    @Transactional(readOnly = true)
    public List<Judge> findJudgesByCourtId(UUID courtId) {
        return judgeRepository.findByCourtIdOrderByLastNameAscFirstNameAsc(courtId);
    }

    @Transactional
    public Judge createJudge(UUID courtId, Judge judge) {
        Court court = findCourtById(courtId);
        judge.setCourt(court);
        return judgeRepository.save(judge);
    }

    @Transactional
    public Judge createJudge(JudgeRequest request) {
        Court court = findCourtById(request.getCourtId());
        Judge judge = new Judge(court, request.getFirstName(), request.getLastName(), request.getRole());
        return judgeRepository.save(judge);
    }

    @Transactional(readOnly = true)
    public List<JudicialCase> findJudicialCasesByLawFirmId(UUID requestedLawFirmId) {
        UUID tenantId = TenantContext.requireLawFirmId();
        if (requestedLawFirmId != null && !tenantId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "lawFirmId query parameter does not match your tenant.");
        }
        return judicialCaseRepository.findAllByLawFirmIdOrderByOpeningDateDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public JudicialCase findJudicialCaseById(UUID id) {
        UUID tenantId = TenantContext.requireLawFirmId();
        JudicialCase judicialCase = judicialCaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + id));
        if (!tenantId.equals(judicialCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + id);
        }
        return judicialCase;
    }

    @Transactional
    public JudicialCase createJudicialCase(JudicialCase judicialCase) {
        UUID tenantId = TenantContext.requireLawFirmId();
        if (judicialCase.getLawFirmId() != null && !tenantId.equals(judicialCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "lawFirmId in request body does not match your tenant.");
        }

        validateJudicialCaseReferences(judicialCase.getCourtId(), judicialCase.getJudgeId());
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(judicialCase.getCaseId());
        judicialCase.setLawFirmId(tenantId);
        if (judicialCase.getStatus() == null) {
            judicialCase.setStatus(JudicialCaseStatus.REGISTERED);
        }

        JudicialCase saved = judicialCaseRepository.save(judicialCase);
        JudicialEvent event = new JudicialEvent("JUDICIAL_CASE_REGISTERED",
                "Judicial case registered for caseId=" + saved.getCaseId(), LocalDateTime.now(), "system");
        saved.addEvent(event);
        judicialCaseRepository.save(saved);
        return saved;
    }

    private void validateJudicialCaseReferences(UUID courtId, UUID judgeId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Court not found: " + courtId));

        Judge judge = judgeRepository.findById(judgeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Judge not found: " + judgeId));

        if (!court.getId().equals(judge.getCourt() == null ? null : judge.getCourt().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Judge does not belong to the selected court.");
        }
    }

    @Transactional
    public JudicialCase updateJudicialCaseStatus(UUID id, JudicialCaseStatus newStatus) {
        UUID tenantId = TenantContext.requireLawFirmId();
        JudicialCase judicialCase = judicialCaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + id));
        if (!tenantId.equals(judicialCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + id);
        }

        JudicialCaseStatus current = judicialCase.getStatus();
        if (current == null) {
            throw new IllegalArgumentException("Current judicial case status is missing");
        }

        if (!isValidStatusTransition(current, newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Judicial case status transition from " + current + " to " + newStatus + " is not allowed");
        }

        judicialCase.setStatus(newStatus);
        JudicialEvent event = new JudicialEvent("JUDICIAL_STATUS_CHANGED",
                "Judicial case status changed from " + current + " to " + newStatus,
                LocalDateTime.now(), "system");
        judicialCase.addEvent(event);
        return judicialCaseRepository.save(judicialCase);
    }

    @Transactional(readOnly = true)
    public List<Hearing> findHearingsByJudicialCaseId(UUID judicialCaseId) {
        UUID tenantId = TenantContext.requireLawFirmId();
        JudicialCase judicialCase = judicialCaseRepository.findById(judicialCaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + judicialCaseId));
        if (!tenantId.equals(judicialCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + judicialCaseId);
        }
        return hearingRepository.findAllByJudicialCase_IdOrderByDateTimeAsc(judicialCaseId);
    }

    @Transactional(readOnly = true)
    public List<JudicialEvent> findEventsByJudicialCaseId(UUID judicialCaseId) {
        UUID tenantId = TenantContext.requireLawFirmId();
        JudicialCase judicialCase = judicialCaseRepository.findById(judicialCaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + judicialCaseId));
        if (!tenantId.equals(judicialCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + judicialCaseId);
        }
        return judicialEventRepository.findAllByJudicialCase_IdOrderByEventDateAsc(judicialCaseId);
    }

    @Transactional
    public Hearing createHearing(UUID judicialCaseId, HearingRequest request) {
        UUID tenantId = TenantContext.requireLawFirmId();
        JudicialCase judicialCase = judicialCaseRepository.findById(judicialCaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + judicialCaseId));
        if (!tenantId.equals(judicialCase.getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Judicial case not found: " + judicialCaseId);
        }
        Hearing hearing = new Hearing(judicialCase, request.getDateTime(), request.getType(), request.getStatus(),
                request.getLocation(), request.getNotes(), request.getNextAction());
        hearing.setJudicialCase(judicialCase);

        if (hearing.getStatus() == null) {
            hearing.setStatus(HearingStatus.SCHEDULED);
        }

        Hearing saved = hearingRepository.save(hearing);
        judicialCase.setStatus(JudicialCaseStatus.HEARING_SCHEDULED);

        JudicialEvent event = new JudicialEvent("HEARING_CREATED",
                "Hearing scheduled for " + saved.getDateTime() + " type=" + saved.getType(),
                LocalDateTime.now(), "system");
        judicialCase.addEvent(event);
        judicialCaseRepository.save(judicialCase);
        return saved;
    }

    @Transactional
    public Hearing updateHearingStatus(UUID hearingId, HearingStatus newStatus) {
        UUID tenantId = TenantContext.requireLawFirmId();
        Hearing hearing = hearingRepository.findById(hearingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hearing not found: " + hearingId));
        if (!tenantId.equals(hearing.getJudicialCase().getLawFirmId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hearing not found: " + hearingId);
        }

        HearingStatus current = hearing.getStatus();
        if (!isValidHearingStatusTransition(current, newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hearing status transition from " + current + " to " + newStatus + " is not allowed");
        }

        hearing.setStatus(newStatus);
        Hearing saved = hearingRepository.save(hearing);

        JudicialEvent event = new JudicialEvent("HEARING_STATUS_CHANGED",
                "Hearing status changed from " + current + " to " + newStatus,
                LocalDateTime.now(), "system");
        hearing.getJudicialCase().addEvent(event);
        judicialCaseRepository.save(hearing.getJudicialCase());

        if (newStatus == HearingStatus.COMPLETED) {
            hearing.getJudicialCase().setStatus(JudicialCaseStatus.DELIBERATION);
            judicialCaseRepository.save(hearing.getJudicialCase());
        }

        return saved;
    }

    private boolean isValidStatusTransition(JudicialCaseStatus current, JudicialCaseStatus next) {
        return current != null && next != null
                && JUDICIAL_CASE_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }

    private boolean isValidHearingStatusTransition(HearingStatus current, HearingStatus next) {
        return current != null && next != null
                && HEARING_TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }
}
