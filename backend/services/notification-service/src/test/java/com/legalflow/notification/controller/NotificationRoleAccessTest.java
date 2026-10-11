package com.legalflow.notification.controller;

import com.legalflow.notification.config.NotificationSecurityConfig;
import com.legalflow.notification.service.NotificationService;
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

/** Chacun lit et gère ses propres notifications ; seul le personnel peut en créer. */
@WebMvcTest(NotificationController.class)
@Import(NotificationSecurityConfig.class)
class NotificationRoleAccessTest {

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

    private static final String CREATE_BODY = "{\"type\":\"CASE_CREATED\",\"title\":\"Nouveau dossier\",\"message\":\"Un dossier a été ouvert.\"}";

    @MockBean
    private NotificationService notificationService;

    @Test
    void everyRoleManagesItsOwnNotifications() throws Exception {
        for (String role : new String[] {"CLIENT", "LAWYER", "SECRETARY", "ADMIN"}) {
            mockMvc.perform(get("/notifications").with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get("/notifications/unread").with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get("/notifications/{id}", ID).with(as(role))).andExpect(status().isOk());
            mockMvc.perform(patch("/notifications/{id}/read", ID).with(as(role))).andExpect(status().isOk());
            mockMvc.perform(patch("/notifications/read-all").with(as(role))).andExpect(status().isOk());
            mockMvc.perform(delete("/notifications/{id}", ID).with(as(role))).andExpect(status().isNoContent());
        }
    }

    @Test
    void onlyStaffCanCreateNotifications() throws Exception {
        mockMvc.perform(post("/notifications").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());

        for (String role : new String[] {"LAWYER", "SECRETARY", "ADMIN"}) {
            mockMvc.perform(post("/notifications").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/notifications").with(noRole())).andExpect(status().isForbidden());
    }
}
