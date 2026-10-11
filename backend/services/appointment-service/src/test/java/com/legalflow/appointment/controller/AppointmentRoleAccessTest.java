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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles posées par @PreAuthorize sur le contrôleur (le service est simulé). */
@WebMvcTest(AppointmentController.class)
@Import(AppointmentSecurityConfig.class)
class AppointmentRoleAccessTest {

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

    private static final String CREATE_BODY = "{\"clientId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\","
            + "\"lawyerId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"dateTime\":\"2099-01-01T10:00:00\","
            + "\"type\":\"CLIENT_MEETING\"}";

    @MockBean
    private AppointmentService appointmentService;

    @Test
    void clientReadsAndCancelsButCannotScheduleOrDelete() throws Exception {
        mockMvc.perform(get("/appointments").with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/appointments/{id}", ID).with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(patch("/appointments/{id}/status", ID).param("status", "CANCELLED").with(as("CLIENT")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/appointments").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/appointments/{id}", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void lawyerSchedulesButCannotDelete() throws Exception {
        mockMvc.perform(post("/appointments").with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/appointments/{id}", ID).with(as("LAWYER"))).andExpect(status().isForbidden());
    }

    @Test
    void secretaryAndAdminManageAppointments() throws Exception {
        for (String role : new String[] {"SECRETARY", "ADMIN"}) {
            mockMvc.perform(post("/appointments").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isCreated());
            mockMvc.perform(delete("/appointments/{id}", ID).with(as(role))).andExpect(status().isNoContent());
        }
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/appointments").with(noRole())).andExpect(status().isForbidden());
    }
}
