package com.legalflow.deadline.controller;

import com.legalflow.deadline.config.DeadlineSecurityConfig;
import com.legalflow.deadline.service.DeadlineService;
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

/** Les échéances sont internes au cabinet : le client n'y a pas accès, la secrétaire est en lecture seule. */
@WebMvcTest(DeadlineController.class)
@Import(DeadlineSecurityConfig.class)
class DeadlineRoleAccessTest {

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

    private static final String DEADLINE_BODY = "{\"caseId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\","
            + "\"title\":\"Dépôt des conclusions\",\"dueDate\":\"2099-01-01T10:00:00\",\"priority\":\"HIGH\"}";

    @MockBean
    private DeadlineService deadlineService;

    @Test
    void clientHasNoAccessToDeadlines() throws Exception {
        mockMvc.perform(get("/deadlines").with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/deadlines/{id}", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/deadlines/case/{id}", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/deadlines").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(DEADLINE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretaryIsReadOnly() throws Exception {
        mockMvc.perform(get("/deadlines").with(as("SECRETARY"))).andExpect(status().isOk());
        mockMvc.perform(get("/deadlines/approaching").with(as("SECRETARY"))).andExpect(status().isOk());

        mockMvc.perform(post("/deadlines").with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(DEADLINE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/deadlines/{id}/status", ID).param("status", "COMPLETED").with(as("SECRETARY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/deadlines/{id}", ID).with(as("SECRETARY"))).andExpect(status().isForbidden());
    }

    @Test
    void lawyerAndAdminManageDeadlines() throws Exception {
        for (String role : new String[] {"LAWYER", "ADMIN"}) {
            mockMvc.perform(post("/deadlines").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(DEADLINE_BODY))
                    .andExpect(status().isCreated());
            mockMvc.perform(patch("/deadlines/{id}/status", ID).param("status", "COMPLETED").with(as(role)))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/deadlines/{id}", ID).with(as(role))).andExpect(status().isNoContent());
        }
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/deadlines").with(noRole())).andExpect(status().isForbidden());
    }
}
