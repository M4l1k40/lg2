package com.legalflow.client.controller;

import com.legalflow.client.domain.Client;
import com.legalflow.client.repository.ClientRepository;
import com.legalflow.client.service.ClientService;
import com.legalflow.client.config.ClientSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import({ClientService.class, ClientSecurityConfig.class, ClientExceptionHandler.class})
class ClientControllerTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientRepository clientRepository;

    @Test
    void ownTenantCanReadClient() throws Exception {
    when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client(FIRM_A)));

    mockMvc.perform(get("/clients/{id}", CLIENT_ID)
            .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("client@example.test"));
    }

    @Test
    void otherTenantCannotReadClientById() throws Exception {
    when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client(FIRM_A)));

    mockMvc.perform(get("/clients/{id}", CLIENT_ID)
            .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
        .andExpect(status().isNotFound());
    }

    @Test
    void mismatchedQueryAndBodyTenantsAreForbidden() throws Exception {
    mockMvc.perform(get("/clients")
            .param("lawFirmId", FIRM_A.toString())
            .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/clients")
            .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lawFirmId":"44444444-4444-4444-4444-444444444444","firstName":"Test","lastName":"Client","email":"body@example.test"}
                """))
        .andExpect(status().isForbidden());
    }

    @Test
    void missingTenantClaimIsForbidden() throws Exception {
    mockMvc.perform(get("/clients").with(jwt()))
        .andExpect(status().isForbidden());
    }

    @Test
    void listUsesOnlyAuthenticatedTenantAndCreateCanOmitTenant() throws Exception {
    when(clientRepository.findAllByLawFirmId(FIRM_A)).thenReturn(List.of(client(FIRM_A)));
    when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(get("/clients")
            .param("lawFirmId", FIRM_A.toString())
            .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("client@example.test"))
        .andExpect(jsonPath("$[1]").doesNotExist());

    mockMvc.perform(post("/clients")
            .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"firstName":"New","lastName":"Client","email":"new@example.test"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()));

    verify(clientRepository).findAllByLawFirmId(FIRM_A);
    }

    @Test
    void duplicateEmailReturnsStandardConflictBodyBeforeInsert() throws Exception {
        when(clientRepository.existsByEmail("duplicate@example.test")).thenReturn(true);

        mockMvc.perform(post("/clients")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Dup","lastName":"Client","email":"duplicate@example.test"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Un client avec cet email existe déjà."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/clients"));

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void uniqueConstraintRaceReturnsTheSameConflictBodyAndLogsOriginalCause() throws Exception {
        when(clientRepository.existsByEmail("race@example.test")).thenReturn(false);
        when(clientRepository.save(any(Client.class)))
                .thenThrow(new DataIntegrityViolationException("unique email constraint"));

        mockMvc.perform(post("/clients")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Race","lastName":"Client","email":"race@example.test"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Un client avec cet email existe déjà."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/clients"));
    }

    @Test
    void malformedJsonUsesStandardBadRequestBody() throws Exception {
        mockMvc.perform(post("/clients")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body is invalid."))
                .andExpect(jsonPath("$.path").value("/clients"));
    }

    private Client client(UUID lawFirmId) {
    return new Client(lawFirmId, "Test", "Client", "client@example.test", "555", "Test address");
    }
}
