package com.legalflow.judicial_service.service;

import com.legalflow.judicial_service.domain.JudicialCase;
import com.legalflow.judicial_service.domain.JudicialCaseStatus;
import com.legalflow.judicial_service.domain.ProcedureType;
import com.legalflow.judicial_service.integration.CaseServiceOwnershipVerifier;
import com.legalflow.judicial_service.repository.CourtRepository;
import com.legalflow.judicial_service.repository.HearingRepository;
import com.legalflow.judicial_service.repository.JudgeRepository;
import com.legalflow.judicial_service.repository.JudicialCaseRepository;
import com.legalflow.judicial_service.repository.JudicialEventRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

class JudicialClientVisibilityTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private void authenticate(UUID userId, UUID clientId, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(userId.toString());
        if (clientId != null) {
            builder.claim("clientId", clientId.toString());
        }
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }

    private static final UUID MY_CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CASE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID MY_JUDICIAL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_JUDICIAL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private JudicialCaseRepository judicialCaseRepository;
    private HearingRepository hearingRepository;
    private CaseServiceOwnershipVerifier verifier;
    private JudicialService service;

    @BeforeEach
    void setUp() {
        judicialCaseRepository = mock(JudicialCaseRepository.class);
        hearingRepository = mock(HearingRepository.class);
        verifier = mock(CaseServiceOwnershipVerifier.class);
        service = new JudicialService(mock(CourtRepository.class), mock(JudgeRepository.class), judicialCaseRepository,
                hearingRepository, mock(JudicialEventRepository.class), verifier);

        JudicialCase mine = judicialCase(MY_JUDICIAL_ID, MY_CASE_ID);
        JudicialCase other = judicialCase(OTHER_JUDICIAL_ID, OTHER_CASE_ID);
        when(judicialCaseRepository.findAllByLawFirmIdOrderByOpeningDateDesc(FIRM_A)).thenReturn(List.of(mine, other));
        when(judicialCaseRepository.findById(MY_JUDICIAL_ID)).thenReturn(Optional.of(mine));
        when(judicialCaseRepository.findById(OTHER_JUDICIAL_ID)).thenReturn(Optional.of(other));
        // Le case-service répond 404 pour un dossier qui n'appartient pas au client : le vérificateur le traduit en 422.
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "case not found in your law firm"))
                .when(verifier).verifyCaseBelongsToCurrentTenant(OTHER_CASE_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientSeesOnlyJudicialCasesOfItsOwnCases() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        List<JudicialCase> visible = service.findJudicialCasesByLawFirmId(null);

        assertEquals(1, visible.size());
        assertEquals(MY_CASE_ID, visible.get(0).getCaseId());
    }

    @Test
    void clientCannotOpenAnotherClientsJudicialCase() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        ResponseStatusException byId = assertThrows(ResponseStatusException.class,
                () -> service.findJudicialCaseById(OTHER_JUDICIAL_ID));
        assertEquals(HttpStatus.NOT_FOUND, byId.getStatusCode());

        ResponseStatusException hearings = assertThrows(ResponseStatusException.class,
                () -> service.findHearingsByJudicialCaseId(OTHER_JUDICIAL_ID));
        assertEquals(HttpStatus.NOT_FOUND, hearings.getStatusCode());
        verify(hearingRepository, never()).findAllByJudicialCase_IdOrderByDateTimeAsc(OTHER_JUDICIAL_ID);

        assertEquals(MY_JUDICIAL_ID, service.findJudicialCaseById(MY_JUDICIAL_ID).getId());
    }

    @Test
    void officeStaffSeesEverythingWithoutCallingTheCaseService() {
        authenticate(UUID.randomUUID(), null, "SECRETARY");

        assertEquals(2, service.findJudicialCasesByLawFirmId(null).size());
        verify(verifier, never()).verifyCaseBelongsToCurrentTenant(any());
    }

    @Test
    void lawyerSeesOnlyTheJudicialCasesOfTheCasesHeCanSee() {
        authenticate(UUID.randomUUID(), null, "LAWYER");

        List<JudicialCase> visible = service.findJudicialCasesByLawFirmId(null);

        assertEquals(1, visible.size());
        assertEquals(MY_CASE_ID, visible.get(0).getCaseId());
    }

    @Test
    void lawyerCannotOpenTheJudicialCaseOfAnotherLawyersCase() {
        authenticate(UUID.randomUUID(), null, "LAWYER");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.findJudicialCaseById(OTHER_JUDICIAL_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private JudicialCase judicialCase(UUID id, UUID caseId) {
        JudicialCase judicialCase = new JudicialCase(FIRM_A, caseId, UUID.randomUUID(), UUID.randomUUID(), "J-1",
                ProcedureType.CIVIL, JudicialCaseStatus.REGISTERED, LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(judicialCase, "id", id);
        return judicialCase;
    }
}
