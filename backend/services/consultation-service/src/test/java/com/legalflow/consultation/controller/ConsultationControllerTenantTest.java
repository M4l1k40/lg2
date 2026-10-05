package com.legalflow.consultation.controller;

import com.legalflow.consultation.domain.Consultation;
import com.legalflow.consultation.domain.ConsultationStatus;
import com.legalflow.consultation.dto.CreateConsultationRequest;
import com.legalflow.consultation.service.ConsultationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsultationControllerTenantTest {

    private MockMvc mockMvc;
    private ConsultationService consultationService;

    @BeforeEach
    void setUp() {
        consultationService = mock(ConsultationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsultationController(consultationService)).build();
    }

    @Test
    void createConsultationReturnsCreated() throws Exception {
        UUID clientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID firmA = UUID.fromString("11111111-1111-1111-1111-111111111111");

        Consultation consultation = new Consultation(firmA, clientId, null, "Question", "Need advice", ConsultationStatus.PENDING, null);
        when(consultationService.createConsultation(any(CreateConsultationRequest.class), isNull())).thenReturn(consultation);

        mockMvc.perform(post("/consultations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"subject\":\"Question\",\"description\":\"Need advice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lawFirmId").value(firmA.toString()));
    }

    @Test
    void getByIdReturnsResponseWhenConsultationExists() throws Exception {
        UUID consultationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID firmA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID clientId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        Consultation consultation = new Consultation(firmA, clientId, null, "Question", "Need advice", ConsultationStatus.PENDING, null);
        when(consultationService.getConsultation(consultationId)).thenReturn(consultation);

        mockMvc.perform(get("/consultations/{id}", consultationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawFirmId").value(firmA.toString()));
    }
}
