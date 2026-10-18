package com.legalflow.user.controller;

import com.legalflow.user.config.UserSecurityConfig;
import com.legalflow.user.service.ClientInvitationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientInvitationController.class)
@Import(UserSecurityConfig.class)
class ClientInvitationControllerRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientInvitationService clientInvitationService;

    @Test
    void officeStaffAndLawyersCanInvite() throws Exception {
        for (String role : new String[] {"ADMIN", "SECRETARY", "LAWYER"}) {
            mockMvc.perform(post("/users/clients/{id}/invitation", CLIENT_ID).with(as(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aClientCannotInviteAnyone() throws Exception {
        mockMvc.perform(post("/users/clients/{id}/invitation", CLIENT_ID).with(as("CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(post("/users/clients/{id}/invitation", CLIENT_ID)).andExpect(status().isUnauthorized());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }
}
