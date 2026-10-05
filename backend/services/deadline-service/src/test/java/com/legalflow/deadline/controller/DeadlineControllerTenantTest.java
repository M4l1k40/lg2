package com.legalflow.deadline.controller;

import com.legalflow.deadline.config.DeadlineSecurityConfig;
import com.legalflow.deadline.domain.Deadline;
import com.legalflow.deadline.domain.DeadlineStatus;
import com.legalflow.deadline.domain.Priority;
import com.legalflow.deadline.integration.CaseServiceOwnershipVerifier;
import com.legalflow.deadline.repository.DeadlineRepository;
import com.legalflow.deadline.service.DeadlineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeadlineController.class)
@Import({DeadlineService.class, DeadlineSecurityConfig.class, DeadlineExceptionHandler.class})
class DeadlineControllerTenantTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CASE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID DEADLINE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeadlineRepository deadlineRepository;

    @MockBean
    private CaseServiceOwnershipVerifier ownershipVerifier;

    @Test
    void createsDeadlineWithAuthenticatedTenant() throws Exception {
        when(deadlineRepository.save(any(Deadline.class))).thenAnswer(invocation -> {
            Deadline deadline = invocation.getArgument(0);
            ReflectionTestUtils.setField(deadline, "id", DEADLINE_ID);
            ReflectionTestUtils.setField(deadline, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(deadline, "updatedAt", LocalDateTime.now());
            return deadline;
        });

        mockMvc.perform(post("/deadlines").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DEADLINE_ID.toString()))
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(ownershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);
    }

    @Test
    void readsOwnDeadline() throws Exception {
        when(deadlineRepository.findByIdAndLawFirmId(DEADLINE_ID, FIRM_A))
                .thenReturn(Optional.of(deadline(FIRM_A, DeadlineStatus.PENDING)));

        mockMvc.perform(get("/deadlines/{id}", DEADLINE_ID).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$.caseId").value(CASE_ID.toString()));
    }

    @Test
    void crossTenantDeadlineIdReturns404() throws Exception {
        when(deadlineRepository.findByIdAndLawFirmId(DEADLINE_ID, FIRM_B)).thenReturn(Optional.empty());

        mockMvc.perform(get("/deadlines/{id}", DEADLINE_ID).with(jwtForB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Deadline not found: " + DEADLINE_ID));
    }

    @Test
    void falsifiedTenantQueryReturns403() throws Exception {
        mockMvc.perform(get("/deadlines").queryParam("lawFirmId", FIRM_B.toString()).with(jwtForA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Requested lawFirmId does not match the authenticated law firm."));

        verify(deadlineRepository, never()).findAllByLawFirmIdOrderByDueDateAsc(any());
    }

    @Test
    void falsifiedTenantBodyReturns403() throws Exception {
        mockMvc.perform(post("/deadlines").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(FIRM_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Requested lawFirmId does not match the authenticated law firm."));

        verify(deadlineRepository, never()).save(any());
        verify(ownershipVerifier, never()).verifyCaseBelongsToCurrentTenant(any());
    }

    @Test
    void invalidStatusTransitionReturns409() throws Exception {
        when(deadlineRepository.findByIdAndLawFirmId(DEADLINE_ID, FIRM_A))
                .thenReturn(Optional.of(deadline(FIRM_A, DeadlineStatus.COMPLETED)));

        mockMvc.perform(patch("/deadlines/{id}/status", DEADLINE_ID)
                        .queryParam("status", "CANCELLED").with(jwtForA()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Deadline status transition from COMPLETED to CANCELLED is not allowed."));

        verify(deadlineRepository, never()).save(any());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/deadlines")).andExpect(status().isUnauthorized());
    }

    private Deadline deadline(UUID lawFirmId, DeadlineStatus deadlineStatus) {
        Deadline deadline = new Deadline(lawFirmId, CASE_ID, "File response", "Description",
                LocalDateTime.now().plusDays(5), Priority.HIGH, deadlineStatus, "lawyer-a");
        ReflectionTestUtils.setField(deadline, "id", DEADLINE_ID);
        ReflectionTestUtils.setField(deadline, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(deadline, "updatedAt", LocalDateTime.now());
        return deadline;
    }

    private String createBody(UUID lawFirmId) {
        String tenantProperty = lawFirmId == null ? "" : "\"lawFirmId\":\"" + lawFirmId + "\",";
        return "{" + tenantProperty
                + "\"caseId\":\"" + CASE_ID + "\","
                + "\"title\":\"File response\","
                + "\"description\":\"Description\","
                + "\"dueDate\":\"" + LocalDateTime.now().plusDays(5) + "\","
                + "\"priority\":\"HIGH\"}";
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtForA() {
        return jwt().jwt(token -> token.subject("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .claim("lawFirmId", FIRM_A.toString()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtForB() {
        return jwt().jwt(token -> token.subject("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                .claim("lawFirmId", FIRM_B.toString()));
    }
}