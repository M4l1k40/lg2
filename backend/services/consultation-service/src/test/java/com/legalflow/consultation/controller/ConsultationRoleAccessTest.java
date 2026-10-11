package com.legalflow.consultation.controller;

import com.legalflow.consultation.config.ConsultationSecurityConfig;
import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;
import com.legalflow.consultation.service.ConsultationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Vérifie les règles par rôle posées par @PreAuthorize sur le contrôleur (le service est simulé). */
@WebMvcTest(ConsultationController.class)
@Import(ConsultationSecurityConfig.class)
class ConsultationRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final String CREATE_BODY =
            "{\"clientId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"subject\":\"Question\",\"description\":\"Besoin d'un avis juridique\"}";
    private static final String ASSIGN_BODY = "{\"lawyerId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"}";
    private static final String ANSWER_BODY = "{\"answer\":\"Voici ma réponse détaillée.\"}";
    private static final String UPDATE_BODY = "{\"subject\":\"Nouveau sujet\",\"description\":\"Nouvelle description détaillée\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultationService consultationService;

    @BeforeEach
    void stubService() {
        Consultation sample = new Consultation(FIRM_A, CLIENT_ID, null, "Question", "Need legal advice",
                ConsultationStatus.PENDING, null);
        when(consultationService.getConsultations(any())).thenReturn(List.of());
        when(consultationService.createConsultation(any(), any())).thenReturn(sample);
        when(consultationService.getConsultation(any())).thenReturn(sample);
        when(consultationService.updateConsultation(any(), any())).thenReturn(sample);
        when(consultationService.assignConsultation(any(), any())).thenReturn(sample);
        when(consultationService.startConsultation(any())).thenReturn(sample);
        when(consultationService.answerConsultation(any(), any())).thenReturn(sample);
        when(consultationService.closeConsultation(any())).thenReturn(sample);
        when(consultationService.cancelConsultation(any())).thenReturn(sample);
    }

    @Test
    void clientCanListCreateReadAndCancelButNotManageTheWorkflow() throws Exception {
        mockMvc.perform(get("/consultations").with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/consultations/{id}", ID).with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(post("/consultations").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/consultations/{id}/cancel", ID).with(as("CLIENT"))).andExpect(status().isOk());

        mockMvc.perform(put("/consultations/{id}/assign", ID).with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/consultations/{id}/start", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/consultations/{id}/answer", ID).with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(ANSWER_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/consultations/{id}/close", ID).with(as("CLIENT"))).andExpect(status().isForbidden());

        verify(consultationService, never()).assignConsultation(any(), any());
        verify(consultationService, never()).startConsultation(any());
        verify(consultationService, never()).answerConsultation(any(), any());
        verify(consultationService, never()).closeConsultation(any());
    }

    @Test
    void secretaryManagesRequestsButCannotStartOrAnswer() throws Exception {
        mockMvc.perform(put("/consultations/{id}/assign", ID).with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}", ID).with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/cancel", ID).with(as("SECRETARY"))).andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/close", ID).with(as("SECRETARY"))).andExpect(status().isOk());
        mockMvc.perform(delete("/consultations/{id}", ID).with(as("SECRETARY"))).andExpect(status().isNoContent());

        mockMvc.perform(put("/consultations/{id}/start", ID).with(as("SECRETARY"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/consultations/{id}/answer", ID).with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(ANSWER_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void lawyerWorksTheWorkflowButCannotEditOrCancel() throws Exception {
        mockMvc.perform(get("/consultations").with(as("LAWYER"))).andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/assign", ID).with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/start", ID).with(as("LAWYER"))).andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/answer", ID).with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(ANSWER_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/close", ID).with(as("LAWYER"))).andExpect(status().isOk());

        mockMvc.perform(put("/consultations/{id}", ID).with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/consultations/{id}/cancel", ID).with(as("LAWYER"))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/consultations/{id}", ID).with(as("LAWYER"))).andExpect(status().isForbidden());
    }

    @Test
    void adminSupervisesButDoesNotStartOrAnswer() throws Exception {
        mockMvc.perform(get("/consultations").with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/assign", ID).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(ASSIGN_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(put("/consultations/{id}/start", ID).with(as("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void userWithoutAnyRoleIsForbiddenEverywhere() throws Exception {
        mockMvc.perform(get("/consultations").with(noRole())).andExpect(status().isForbidden());
        mockMvc.perform(post("/consultations").with(noRole()).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static JwtRequestPostProcessor noRole() {
        return jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }
}
