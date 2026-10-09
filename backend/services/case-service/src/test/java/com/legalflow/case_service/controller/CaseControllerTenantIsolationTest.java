package com.legalflow.case_service.controller;

import com.legalflow.case_service.config.CaseSecurityConfig;
import com.legalflow.case_service.domain.CaseStatus;
import com.legalflow.case_service.domain.LegalCase;
import com.legalflow.case_service.integration.ClientServiceOwnershipVerifier;
import com.legalflow.case_service.repository.CaseEventRepository;
import com.legalflow.case_service.repository.LegalCaseRepository;
import com.legalflow.case_service.service.CaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

@WebMvcTest(CaseController.class)
@Import({CaseService.class, CaseSecurityConfig.class, CaseExceptionHandler.class})
class CaseControllerTenantIsolationTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CLIENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LegalCaseRepository legalCaseRepository;

    @MockBean
    private CaseEventRepository caseEventRepository;

        @MockBean
        private ClientServiceOwnershipVerifier clientServiceOwnershipVerifier;

    @Test
    void ownTenantCanReadCase() throws Exception {
        when(legalCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(legalCase(FIRM_A)));

        mockMvc.perform(get("/cases/{id}", CASE_ID)
                        .with(staff(FIRM_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("CASE-1"));
    }

    @Test
    void otherTenantCannotReadCaseOrEvents() throws Exception {
        when(legalCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(legalCase(FIRM_A)));

        mockMvc.perform(get("/cases/{id}", CASE_ID)
                        .with(staff(FIRM_B)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/cases/{id}/events", CASE_ID)
                        .with(staff(FIRM_B)))
                .andExpect(status().isNotFound());
    }

    @Test
    void mismatchedQueryAndBodyTenantsAreForbidden() throws Exception {
        mockMvc.perform(get("/cases")
                        .param("lawFirmId", FIRM_A.toString())
                        .with(staff(FIRM_B)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/cases")
                        .with(staff(FIRM_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lawFirmId":"44444444-4444-4444-4444-444444444444","clientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","lawyerId":"cccccccc-cccc-cccc-cccc-cccccccccccc","reference":"CASE-2","title":"Body mismatch"}
                                """))
                .andExpect(status().isForbidden());

        verify(clientServiceOwnershipVerifier, never()).verifyClientBelongsToCurrentTenant(any());
    }

    @Test
    void missingTenantClaimIsForbidden() throws Exception {
        mockMvc.perform(get("/cases").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LAWYER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsesAuthenticatedTenantAndCreateCanOmitTenant() throws Exception {
        when(legalCaseRepository.findAllByLawFirmId(FIRM_A)).thenReturn(List.of(legalCase(FIRM_A)));
        when(legalCaseRepository.save(any(LegalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/cases")
                        .param("lawFirmId", FIRM_A.toString())
                        .with(staff(FIRM_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("CASE-1"))
                .andExpect(jsonPath("$[1]").doesNotExist());

        mockMvc.perform(post("/cases")
                        .with(staff(FIRM_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","lawyerId":"cccccccc-cccc-cccc-cccc-cccccccccccc","reference":"CASE-3","title":"Tenant assigned by service"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()));

        verify(legalCaseRepository).findAllByLawFirmId(FIRM_A);
    }

    @Test
    void foreignClientReturns422AndDoesNotPersistCase() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "client not found in your law firm"))
                .when(clientServiceOwnershipVerifier).verifyClientBelongsToCurrentTenant(any());

        mockMvc.perform(post("/cases")
                        .with(staff(FIRM_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","lawyerId":"cccccccc-cccc-cccc-cccc-cccccccccccc","reference":"CASE-FOREIGN-CLIENT","title":"Should be rejected"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("client not found in your law firm"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/cases"));

        verify(legalCaseRepository, never()).save(any(LegalCase.class));
        verify(caseEventRepository, never()).save(any());
    }

    @Test
    void clientServiceFailureReturns503AndDoesNotPersistCase() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Client service could not verify ownership; case was not created."))
                .when(clientServiceOwnershipVerifier).verifyClientBelongsToCurrentTenant(any());

        mockMvc.perform(post("/cases")
                        .with(staff(FIRM_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","lawyerId":"cccccccc-cccc-cccc-cccc-cccccccccccc","reference":"CASE-CLIENT-UNAVAILABLE","title":"Should be rejected"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Client service could not verify ownership; case was not created."));

        verify(legalCaseRepository, never()).save(any(LegalCase.class));
        verify(caseEventRepository, never()).save(any());
    }

    @Test
    void clientRoleSeesOnlyItsOwnCases() throws Exception {
        when(legalCaseRepository.findAllByLawFirmIdAndClientId(FIRM_A, CLIENT_ID))
                .thenReturn(List.of(legalCase(FIRM_A, CLIENT_ID)));

        mockMvc.perform(get("/cases").with(clientUser(FIRM_A, CLIENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value("CASE-1"))
                .andExpect(jsonPath("$[1]").doesNotExist());

        verify(legalCaseRepository).findAllByLawFirmIdAndClientId(FIRM_A, CLIENT_ID);
        verify(legalCaseRepository, never()).findAllByLawFirmId(any());
    }

    @Test
    void clientRoleCanReadItsOwnCaseButNotAnotherClientsCase() throws Exception {
        when(legalCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(legalCase(FIRM_A, CLIENT_ID)));

        mockMvc.perform(get("/cases/{id}", CASE_ID).with(clientUser(FIRM_A, CLIENT_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cases/{id}", CASE_ID).with(clientUser(FIRM_A, OTHER_CLIENT_ID)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/cases/{id}/events", CASE_ID).with(clientUser(FIRM_A, OTHER_CLIENT_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientRoleCannotCreateCases() throws Exception {
        mockMvc.perform(post("/cases")
                        .with(clientUser(FIRM_A, CLIENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee","lawyerId":"cccccccc-cccc-cccc-cccc-cccccccccccc","reference":"CASE-X","title":"Not allowed"}
                                """))
                .andExpect(status().isForbidden());

        verify(clientServiceOwnershipVerifier, never()).verifyClientBelongsToCurrentTenant(any());
        verify(legalCaseRepository, never()).save(any(LegalCase.class));
    }

    @Test
    void clientRoleWithoutClientIdClaimIsForbidden() throws Exception {
        mockMvc.perform(get("/cases")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))
                                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/cases").with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
                .andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor staff(UUID lawFirmId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_LAWYER"))
                .jwt(token -> token.claim("lawFirmId", lawFirmId.toString()));
    }

    private static JwtRequestPostProcessor clientUser(UUID lawFirmId, UUID clientId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))
                .jwt(token -> token.claim("lawFirmId", lawFirmId.toString())
                        .claim("clientId", clientId.toString()));
    }

    private LegalCase legalCase(UUID lawFirmId, UUID clientId) {
        return new LegalCase(lawFirmId, clientId, UUID.randomUUID(), "CASE-1", "Test case", "Description", CaseStatus.OPEN);
    }

    private LegalCase legalCase(UUID lawFirmId) {
        return new LegalCase(lawFirmId, UUID.randomUUID(), UUID.randomUUID(), "CASE-1", "Test case", "Description", CaseStatus.OPEN);
    }
}
