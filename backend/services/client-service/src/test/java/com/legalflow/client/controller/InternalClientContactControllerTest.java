package com.legalflow.client.controller;

import com.legalflow.client.config.ClientSecurityConfig;
import com.legalflow.client.domain.Client;
import com.legalflow.client.repository.ClientRepository;
import com.legalflow.client.service.ClientContactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalClientContactController.class)
@Import({ClientContactService.class, ClientSecurityConfig.class, ClientExceptionHandler.class})
class InternalClientContactControllerTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientRepository clientRepository;

    @Test
    void serviceAccountReadsOnlyTheContactFieldsOfAClientOfTheRequestedFirm() throws Exception {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client(FIRM_A)));

        mockMvc.perform(get("/internal/clients/{id}/contact", CLIENT_ID)
                        .param("lawFirmId", FIRM_A.toString())
                        .with(serviceAccount()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("client@example.test"))
                .andExpect(jsonPath("$.phone").value("555-0100"))
                .andExpect(jsonPath("$.firstName").value("Client"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.lawyerIds").doesNotExist());
    }

    @Test
    void aClientOfAnotherFirmLooksLikeAMissingClient() throws Exception {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client(FIRM_A)));

        mockMvc.perform(get("/internal/clients/{id}/contact", CLIENT_ID)
                        .param("lawFirmId", FIRM_B.toString())
                        .with(serviceAccount()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownClientIsNotFound() throws Exception {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/clients/{id}/contact", CLIENT_ID)
                        .param("lawFirmId", FIRM_A.toString())
                        .with(serviceAccount()))
                .andExpect(status().isNotFound());
    }

    @Test
    void noHumanRoleCanUseTheInternalEndpoint() throws Exception {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client(FIRM_A)));

        for (String role : new String[] {"ADMIN", "LAWYER", "SECRETARY", "CLIENT"}) {
            mockMvc.perform(get("/internal/clients/{id}/contact", CLIENT_ID)
                            .param("lawFirmId", FIRM_A.toString())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                                    .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(get("/internal/clients/{id}/contact", CLIENT_ID)
                        .param("lawFirmId", FIRM_A.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLawFirmParameterIsMandatory() throws Exception {
        mockMvc.perform(get("/internal/clients/{id}/contact", CLIENT_ID).with(serviceAccount()))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor serviceAccount() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE_CONTACT_LOOKUP"));
    }

    private Client client(UUID lawFirmId) {
        Client client = new Client(lawFirmId, "Client", "A", "client@example.test", "555-0100", "Cabinet A");
        ReflectionTestUtils.setField(client, "id", CLIENT_ID);
        return client;
    }
}
