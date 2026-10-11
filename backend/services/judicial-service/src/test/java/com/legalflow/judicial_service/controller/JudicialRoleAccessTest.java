package com.legalflow.judicial_service.controller;

import com.legalflow.judicial_service.config.JudicialSecurityConfig;
import com.legalflow.judicial_service.service.JudicialService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles posées par @PreAuthorize sur le contrôleur judiciaire (le service est simulé). */
@WebMvcTest(JudicialController.class)
@Import(JudicialSecurityConfig.class)
class JudicialRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static JwtRequestPostProcessor noRole() {
        return jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    @MockBean
    private JudicialService judicialService;

    @Test
    void clientCanReadItsJudicialCasesButNothingElse() throws Exception {
        mockMvc.perform(get("/judicial-cases").with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/judicial-cases/{id}", ID).with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/judicial-cases/{id}/hearings", ID).with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/judicial-cases/{id}/events", ID).with(as("CLIENT"))).andExpect(status().isOk());

        mockMvc.perform(get("/courts").with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/courts/{id}/judges", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/judicial-cases/{id}/status", ID).param("status", "CLOSED").with(as("CLIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/hearings/{id}/status", ID).param("status", "COMPLETED").with(as("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretaryIsReadOnly() throws Exception {
        mockMvc.perform(get("/courts").with(as("SECRETARY"))).andExpect(status().isOk());
        mockMvc.perform(get("/judicial-cases").with(as("SECRETARY"))).andExpect(status().isOk());

        mockMvc.perform(patch("/judicial-cases/{id}/status", ID).param("status", "CLOSED").with(as("SECRETARY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/hearings/{id}/status", ID).param("status", "COMPLETED").with(as("SECRETARY")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lawyerAndAdminManageJudicialCases() throws Exception {
        for (String role : new String[] {"LAWYER", "ADMIN"}) {
            mockMvc.perform(get("/courts").with(as(role))).andExpect(status().isOk());
            mockMvc.perform(patch("/judicial-cases/{id}/status", ID).param("status", "CLOSED").with(as(role)))
                    .andExpect(status().isOk());
            mockMvc.perform(patch("/hearings/{id}/status", ID).param("status", "COMPLETED").with(as(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/judicial-cases").with(noRole())).andExpect(status().isForbidden());
    }
}
