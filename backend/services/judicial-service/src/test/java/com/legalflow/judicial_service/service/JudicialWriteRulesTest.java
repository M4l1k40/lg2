package com.legalflow.judicial_service.service;

import com.legalflow.judicial_service.domain.Hearing;
import com.legalflow.judicial_service.domain.HearingRequest;
import com.legalflow.judicial_service.domain.HearingStatus;
import com.legalflow.judicial_service.domain.HearingType;
import com.legalflow.judicial_service.domain.JudicialCase;
import com.legalflow.judicial_service.domain.JudicialCaseStatus;
import com.legalflow.judicial_service.domain.ProcedureType;
import com.legalflow.judicial_service.integration.CaseServiceOwnershipVerifier;
import com.legalflow.judicial_service.repository.CourtRepository;
import com.legalflow.judicial_service.repository.HearingRepository;
import com.legalflow.judicial_service.repository.JudgeRepository;
import com.legalflow.judicial_service.repository.JudicialEventRepository;
import com.legalflow.judicial_service.repository.JudicialCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Règles d'écriture : visibilité par avocat, statuts cohérents, procédure close. */
class JudicialWriteRulesTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LAWYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MY_CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CASE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID MY_JUDICIAL_CASE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_JUDICIAL_CASE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID HEARING_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private JudicialCaseRepository judicialCaseRepository;
    private HearingRepository hearingRepository;
    private CaseServiceOwnershipVerifier verifier;
    private JudicialService service;

    @BeforeEach
    void setUp() {
        judicialCaseRepository = mock(JudicialCaseRepository.class);
        hearingRepository = mock(HearingRepository.class);
        verifier = mock(CaseServiceOwnershipVerifier.class);
        service = new JudicialService(mock(CourtRepository.class), mock(JudgeRepository.class),
                judicialCaseRepository, hearingRepository, mock(JudicialEventRepository.class), verifier);
        when(judicialCaseRepository.save(any(JudicialCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hearingRepository.save(any(Hearing.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Le dossier d'un autre avocat n'est pas visible pour l'avocat connecté (le vérificateur le refuse).
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "case not visible"))
                .when(verifier).verifyCaseBelongsToCurrentTenant(OTHER_CASE_ID);
        authenticate("LAWYER");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aLawyerCannotChangeTheStatusOfAnotherLawyersJudicialCase() {
        JudicialCase other = judicialCase(OTHER_JUDICIAL_CASE_ID, OTHER_CASE_ID, JudicialCaseStatus.REGISTERED);
        when(judicialCaseRepository.findById(OTHER_JUDICIAL_CASE_ID)).thenReturn(Optional.of(other));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateJudicialCaseStatus(OTHER_JUDICIAL_CASE_ID, JudicialCaseStatus.IN_PROGRESS));

        assertEquals(JudicialCaseStatus.REGISTERED, other.getStatus());
        verify(judicialCaseRepository, never()).save(any(JudicialCase.class));
    }

    @Test
    void aLawyerCannotScheduleAHearingOnAnotherLawyersJudicialCase() {
        when(judicialCaseRepository.findById(OTHER_JUDICIAL_CASE_ID))
                .thenReturn(Optional.of(judicialCase(OTHER_JUDICIAL_CASE_ID, OTHER_CASE_ID, JudicialCaseStatus.IN_PROGRESS)));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.createHearing(OTHER_JUDICIAL_CASE_ID, hearingRequest()));

        verify(hearingRepository, never()).save(any(Hearing.class));
    }

    @Test
    void aLawyerCannotChangeTheStatusOfAnotherLawyersHearing() {
        JudicialCase other = judicialCase(OTHER_JUDICIAL_CASE_ID, OTHER_CASE_ID, JudicialCaseStatus.HEARING_SCHEDULED);
        Hearing hearing = hearing(other, HearingStatus.SCHEDULED);
        when(hearingRepository.findById(HEARING_ID)).thenReturn(Optional.of(hearing));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.updateHearingStatus(HEARING_ID, HearingStatus.COMPLETED));

        assertEquals(HearingStatus.SCHEDULED, hearing.getStatus());
        verify(hearingRepository, never()).save(any(Hearing.class));
    }

    @Test
    void schedulingAHearingMovesARegisteredCaseToHearingScheduled() {
        JudicialCase mine = judicialCase(MY_JUDICIAL_CASE_ID, MY_CASE_ID, JudicialCaseStatus.IN_PROGRESS);
        when(judicialCaseRepository.findById(MY_JUDICIAL_CASE_ID)).thenReturn(Optional.of(mine));

        service.createHearing(MY_JUDICIAL_CASE_ID, hearingRequest());

        assertEquals(JudicialCaseStatus.HEARING_SCHEDULED, mine.getStatus());
    }

    @Test
    void schedulingAHearingNeverMovesACaseBackwards() {
        for (JudicialCaseStatus status : new JudicialCaseStatus[] {JudicialCaseStatus.DELIBERATION, JudicialCaseStatus.JUDGMENT_ISSUED}) {
            JudicialCase mine = judicialCase(MY_JUDICIAL_CASE_ID, MY_CASE_ID, status);
            when(judicialCaseRepository.findById(MY_JUDICIAL_CASE_ID)).thenReturn(Optional.of(mine));

            service.createHearing(MY_JUDICIAL_CASE_ID, hearingRequest());

            assertEquals(status, mine.getStatus());
        }
    }

    @Test
    void aClosedJudicialCaseCannotReceiveAHearing() {
        when(judicialCaseRepository.findById(MY_JUDICIAL_CASE_ID))
                .thenReturn(Optional.of(judicialCase(MY_JUDICIAL_CASE_ID, MY_CASE_ID, JudicialCaseStatus.CLOSED)));

        assertStatus(HttpStatus.CONFLICT, () -> service.createHearing(MY_JUDICIAL_CASE_ID, hearingRequest()));

        verify(hearingRepository, never()).save(any(Hearing.class));
    }

    @Test
    void completingAHearingPutsAScheduledCaseIntoDeliberation() {
        JudicialCase mine = judicialCase(MY_JUDICIAL_CASE_ID, MY_CASE_ID, JudicialCaseStatus.HEARING_SCHEDULED);
        when(hearingRepository.findById(HEARING_ID)).thenReturn(Optional.of(hearing(mine, HearingStatus.SCHEDULED)));

        service.updateHearingStatus(HEARING_ID, HearingStatus.COMPLETED);

        assertEquals(JudicialCaseStatus.DELIBERATION, mine.getStatus());
    }

    @Test
    void completingAHearingNeverReopensAJudgedOrClosedCase() {
        for (JudicialCaseStatus status : new JudicialCaseStatus[] {JudicialCaseStatus.JUDGMENT_ISSUED, JudicialCaseStatus.CLOSED}) {
            JudicialCase mine = judicialCase(MY_JUDICIAL_CASE_ID, MY_CASE_ID, status);
            when(hearingRepository.findById(HEARING_ID)).thenReturn(Optional.of(hearing(mine, HearingStatus.SCHEDULED)));

            service.updateHearingStatus(HEARING_ID, HearingStatus.COMPLETED);

            assertEquals(status, mine.getStatus());
        }
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected.value(), exception.getStatusCode().value());
    }

    private JudicialCase judicialCase(UUID id, UUID caseId, JudicialCaseStatus status) {
        JudicialCase judicialCase = new JudicialCase(FIRM_A, caseId, UUID.randomUUID(), null, "N-" + id.toString().substring(0, 4),
                ProcedureType.CIVIL, status, LocalDate.now());
        ReflectionTestUtils.setField(judicialCase, "id", id);
        return judicialCase;
    }

    private Hearing hearing(JudicialCase judicialCase, HearingStatus status) {
        Hearing hearing = new Hearing(judicialCase, LocalDateTime.now().plusDays(3), HearingType.FIRST_HEARING, status,
                "Salle 1", null, null);
        ReflectionTestUtils.setField(hearing, "id", HEARING_ID);
        return hearing;
    }

    private HearingRequest hearingRequest() {
        HearingRequest request = new HearingRequest();
        request.setDateTime(LocalDateTime.now().plusDays(7));
        request.setType(HearingType.FIRST_HEARING);
        request.setLocation("Salle 2");
        return request;
    }

    private void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(LAWYER_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
