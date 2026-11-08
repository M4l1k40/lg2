package com.legalflow.appointment.controller;

import com.legalflow.appointment.config.AppointmentSecurityConfig;
import com.legalflow.appointment.service.AppointmentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import(AppointmentSecurityConfig.class)
class AppointmentUpdateRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String BODY = "{\"dateTime\":\"2099-01-01T10:00:00\",\"type\":\"CLIENT_MEETING\",\"location\":\"Cabinet\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentService appointmentService;

    @Test
    void staffCanModifyButAClientCannot() throws Exception {
        for (String role : new String[] {"ADMIN", "SECRETARY", "LAWYER"}) {
            mockMvc.perform(put("/appointments/{id}", ID).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put("/appointments/{id}", ID).with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(put("/appointments/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDateInThePastOrAMissingTypeIsRejected() throws Exception {
        String past = "{\"dateTime\":\"2001-01-01T10:00:00\",\"type\":\"CLIENT_MEETING\"}";
        String noType = "{\"dateTime\":\"2099-01-01T10:00:00\"}";
        mockMvc.perform(put("/appointments/{id}", ID).with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(past))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/appointments/{id}", ID).with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(noType))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }
}
