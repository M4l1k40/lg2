package com.legalflow.judicial_service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalflow.judicial_service.config.JudicialSecurityConfig;
import com.legalflow.judicial_service.domain.Court;
import com.legalflow.judicial_service.domain.Hearing;
import com.legalflow.judicial_service.domain.HearingStatus;
import com.legalflow.judicial_service.domain.HearingType;
import com.legalflow.judicial_service.domain.Judge;
import com.legalflow.judicial_service.domain.JudicialCase;
import com.legalflow.judicial_service.domain.JudicialCaseStatus;
import com.legalflow.judicial_service.domain.JudicialEvent;
import com.legalflow.judicial_service.domain.ProcedureType;
import com.legalflow.judicial_service.integration.CaseServiceOwnershipVerifier;
import com.legalflow.judicial_service.repository.CourtRepository;
import com.legalflow.judicial_service.repository.HearingRepository;
import com.legalflow.judicial_service.repository.JudgeRepository;
import com.legalflow.judicial_service.repository.JudicialCaseRepository;
import com.legalflow.judicial_service.repository.JudicialEventRepository;
import com.legalflow.judicial_service.service.JudicialService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JudicialController.class)
@Import({JudicialService.class, JudicialSecurityConfig.class, JudicialExceptionHandler.class})
class JudicialControllerTenantIsolationTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID JUDICIAL_CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID COURT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

    @MockBean
    private CourtRepository courtRepository;

    @MockBean
    private JudgeRepository judgeRepository;

    @MockBean
    private JudicialCaseRepository judicialCaseRepository;

    @MockBean
    private HearingRepository hearingRepository;

    @MockBean
    private JudicialEventRepository judicialEventRepository;

        @MockBean
        private CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    @Test
    void ownTenantCanReadJudicialCase() throws Exception {
        when(judicialCaseRepository.findById(JUDICIAL_CASE_ID)).thenReturn(Optional.of(judicialCase(FIRM_A)));

        mockMvc.perform(get("/judicial-cases/{id}", JUDICIAL_CASE_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtCaseNumber").value("J-1"));
    }

    @Test
    void childEntitySerializationRetainsParentIdsWithoutCycles() {
        Court court = new Court("Test court", "CIVIL", "Test city", "Test address");
        org.springframework.test.util.ReflectionTestUtils.setField(court, "id", COURT_ID);
        Judge judge = new Judge(court, "Ada", "Law", "Judge");

        JudicialCase judicialCase = judicialCase(FIRM_A);
        org.springframework.test.util.ReflectionTestUtils.setField(judicialCase, "id", JUDICIAL_CASE_ID);
        Hearing hearing = new Hearing(judicialCase, LocalDateTime.now().plusDays(1),
                HearingType.FIRST_HEARING, HearingStatus.SCHEDULED, "Courtroom", null, null);
        JudicialEvent event = new JudicialEvent("REGISTERED", "Test event", LocalDateTime.now(), "system");
        event.setJudicialCase(judicialCase);

        JsonNode judgeJson = objectMapper.valueToTree(judge);
        JsonNode hearingJson = objectMapper.valueToTree(hearing);
        JsonNode eventJson = objectMapper.valueToTree(event);

        org.junit.jupiter.api.Assertions.assertEquals(COURT_ID.toString(), judgeJson.path("courtId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(JUDICIAL_CASE_ID.toString(),
                hearingJson.path("judicialCaseId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(JUDICIAL_CASE_ID.toString(),
                eventJson.path("judicialCaseId").asText());
        org.junit.jupiter.api.Assertions.assertFalse(judgeJson.has("court"));
        org.junit.jupiter.api.Assertions.assertFalse(hearingJson.has("judicialCase"));
        org.junit.jupiter.api.Assertions.assertFalse(eventJson.has("judicialCase"));
    }

    @Test
    void otherTenantCannotReadCaseEventsHearingsOrPatchStatus() throws Exception {
        when(judicialCaseRepository.findById(JUDICIAL_CASE_ID)).thenReturn(Optional.of(judicialCase(FIRM_A)));

        mockMvc.perform(get("/judicial-cases/{id}", JUDICIAL_CASE_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/judicial-cases/{id}/events", JUDICIAL_CASE_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/judicial-cases/{id}/hearings", JUDICIAL_CASE_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/judicial-cases/{id}/status", JUDICIAL_CASE_ID)
                        .param("status", "CLOSED")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void mismatchedQueryAndBodyTenantsAreForbidden() throws Exception {
        mockMvc.perform(get("/judicial-cases")
                        .param("lawFirmId", FIRM_A.toString())
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/judicial-cases")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lawFirmId":"44444444-4444-4444-4444-444444444444","caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","courtId":"cccccccc-cccc-cccc-cccc-cccccccccccc","judgeId":"dddddddd-dddd-dddd-dddd-dddddddddddd","courtCaseNumber":"J-2","procedureType":"CIVIL","openingDate":"2026-09-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTenantClaimIsForbidden() throws Exception {
        mockMvc.perform(get("/judicial-cases").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsesOnlyAuthenticatedTenantAndCreateCanOmitTenant() throws Exception {
        UUID courtId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID judgeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Court court = new Court("Test court", "CIVIL", "Test city", "Test address");
        org.springframework.test.util.ReflectionTestUtils.setField(court, "id", courtId);
        Judge judge = new Judge(court, "Ada", "Law", "Judge");
        org.springframework.test.util.ReflectionTestUtils.setField(judge, "id", judgeId);

        when(judicialCaseRepository.findAllByLawFirmIdOrderByOpeningDateDesc(FIRM_A))
                .thenReturn(List.of(judicialCase(FIRM_A)));
        when(courtRepository.findById(courtId)).thenReturn(Optional.of(court));
        when(judgeRepository.findById(judgeId)).thenReturn(Optional.of(judge));
        when(judicialCaseRepository.save(any(JudicialCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/judicial-cases")
                        .param("lawFirmId", FIRM_A.toString())
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courtCaseNumber").value("J-1"))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(post("/judicial-cases")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","courtId":"cccccccc-cccc-cccc-cccc-cccccccccccc","judgeId":"dddddddd-dddd-dddd-dddd-dddddddddddd","courtCaseNumber":"J-3","procedureType":"CIVIL","openingDate":"2026-09-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()));

        verify(judicialCaseRepository).findAllByLawFirmIdOrderByOpeningDateDesc(FIRM_A);
    }

    @Test
    void unknownCourtOrJudgeReferenceIsRejectedBeforePersist() throws Exception {
        UUID missingCourtId = UUID.fromString("11111111-1111-1111-1111-aaaaaaaaaaaa");
        UUID missingJudgeId = UUID.fromString("22222222-2222-2222-2222-bbbbbbbbbbbb");

        mockMvc.perform(post("/judicial-cases")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","courtId":"11111111-1111-1111-1111-aaaaaaaaaaaa","judgeId":"22222222-2222-2222-2222-bbbbbbbbbbbb","courtCaseNumber":"J-MISSING-REF","procedureType":"CIVIL","openingDate":"2026-09-01"}
                                """))
                .andExpect(status().isNotFound());

        verify(judicialCaseRepository, never()).save(any(JudicialCase.class));
    }

    @Test
    void foreignCaseReturns422AndDoesNotPersistJudicialCase() throws Exception {
        UUID courtId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID judgeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Court court = new Court("Test court", "CIVIL", "Test city", "Test address");
        org.springframework.test.util.ReflectionTestUtils.setField(court, "id", courtId);
        Judge judge = new Judge(court, "Ada", "Law", "Judge");
        org.springframework.test.util.ReflectionTestUtils.setField(judge, "id", judgeId);

        when(courtRepository.findById(courtId)).thenReturn(Optional.of(court));
        when(judgeRepository.findById(judgeId)).thenReturn(Optional.of(judge));
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "case not found in your law firm"))
                .when(caseServiceOwnershipVerifier).verifyCaseBelongsToCurrentTenant(any());

        mockMvc.perform(post("/judicial-cases")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","courtId":"cccccccc-cccc-cccc-cccc-cccccccccccc","judgeId":"dddddddd-dddd-dddd-dddd-dddddddddddd","courtCaseNumber":"J-FOREIGN-CASE","procedureType":"CIVIL","openingDate":"2026-09-01"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("case not found in your law firm"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/judicial-cases"));

        verify(judicialCaseRepository, never()).save(any(JudicialCase.class));
    }

    @Test
    void caseServiceFailureReturns503AndDoesNotPersistJudicialCase() throws Exception {
        UUID courtId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID judgeId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Court court = new Court("Test court", "CIVIL", "Test city", "Test address");
        org.springframework.test.util.ReflectionTestUtils.setField(court, "id", courtId);
        Judge judge = new Judge(court, "Ada", "Law", "Judge");
        org.springframework.test.util.ReflectionTestUtils.setField(judge, "id", judgeId);

        when(courtRepository.findById(courtId)).thenReturn(Optional.of(court));
        when(judgeRepository.findById(judgeId)).thenReturn(Optional.of(judge));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Case service could not verify ownership; judicial case was not created."))
                .when(caseServiceOwnershipVerifier).verifyCaseBelongsToCurrentTenant(any());

        mockMvc.perform(post("/judicial-cases")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","courtId":"cccccccc-cccc-cccc-cccc-cccccccccccc","judgeId":"dddddddd-dddd-dddd-dddd-dddddddddddd","courtCaseNumber":"J-CASE-UNAVAILABLE","procedureType":"CIVIL","openingDate":"2026-09-01"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Case service could not verify ownership; judicial case was not created."));

        verify(judicialCaseRepository, never()).save(any(JudicialCase.class));
    }

    private JudicialCase judicialCase(UUID lawFirmId) {
        return new JudicialCase(lawFirmId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "J-1", ProcedureType.CIVIL, JudicialCaseStatus.REGISTERED, LocalDate.of(2026, 9, 1));
    }
}
