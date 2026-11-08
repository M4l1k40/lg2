package com.legalflow.document.controller;

import com.legalflow.document.config.DocumentSecurityConfig;
import com.legalflow.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(DocumentSecurityConfig.class)
class DocumentStatsRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String WEEK = "2026-10-11T00:00:00Z";
    private static final String TODAY = "2026-10-17T22:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    void onlyAdminAndSecretaryCanReadTheStatistics() throws Exception {
        for (String role : new String[] {"ADMIN", "SECRETARY"}) {
            mockMvc.perform(get("/documents/stats").param("weekStart", WEEK).param("todayStart", TODAY).with(as(role)))
                    .andExpect(status().isOk());
        }
        for (String role : new String[] {"LAWYER", "CLIENT"}) {
            mockMvc.perform(get("/documents/stats").param("weekStart", WEEK).param("todayStart", TODAY).with(as(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(get("/documents/stats").param("weekStart", WEEK).param("todayStart", TODAY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bothBoundsAreMandatory() throws Exception {
        mockMvc.perform(get("/documents/stats").param("weekStart", WEEK).with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }
}
