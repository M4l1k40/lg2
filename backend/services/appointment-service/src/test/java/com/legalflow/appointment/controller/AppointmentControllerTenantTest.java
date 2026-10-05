package com.legalflow.appointment.controller;

import com.legalflow.appointment.config.AppointmentSecurityConfig;
import com.legalflow.appointment.domain.Appointment;
import com.legalflow.appointment.domain.AppointmentStatus;
import com.legalflow.appointment.domain.AppointmentType;
import com.legalflow.appointment.integration.AppointmentOwnershipVerifier;
import com.legalflow.appointment.repository.AppointmentRepository;
import com.legalflow.appointment.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import({AppointmentService.class, AppointmentSecurityConfig.class, AppointmentExceptionHandler.class})
class AppointmentControllerTenantTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LAWYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CASE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID APPOINTMENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentRepository appointmentRepository;

    @MockBean
    private AppointmentOwnershipVerifier ownershipVerifier;

    @Test
    void createsAppointmentAndForcesTenantAndScheduledStatus() throws Exception {
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            ReflectionTestUtils.setField(appointment, "id", APPOINTMENT_ID);
            ReflectionTestUtils.setField(appointment, "createdAt", LocalDateTime.now());
            return appointment;
        });

        mockMvc.perform(post("/appointments").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(CASE_ID, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.caseId").value(CASE_ID.toString()));

        verify(appointmentRepository).saveAndFlush(any(Appointment.class));
        verify(ownershipVerifier).verifyClientBelongsToCurrentTenant(CLIENT_ID);
        verify(ownershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);
    }

    @Test
    void createsInternalMeetingWhenCaseIdIsOmitted() throws Exception {
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            ReflectionTestUtils.setField(appointment, "id", APPOINTMENT_ID);
            return appointment;
        });

        mockMvc.perform(post("/appointments").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").doesNotExist())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        verify(ownershipVerifier).verifyCaseBelongsToCurrentTenant(null);
    }

    @Test
    void readsOwnAppointment() throws Exception {
        when(appointmentRepository.findByIdAndLawFirmId(APPOINTMENT_ID, FIRM_A))
                .thenReturn(Optional.of(appointment(APPOINTMENT_ID, FIRM_A, CASE_ID, AppointmentStatus.SCHEDULED)));

        mockMvc.perform(get("/appointments/{id}", APPOINTMENT_ID).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()));
    }

            @Test
            void caseAppointmentListVerifiesCaseOwnershipAndFiltersTenant() throws Exception {
            when(appointmentRepository.findAllByLawFirmIdAndCaseIdOrderByDateTimeAsc(FIRM_A, CASE_ID))
                .thenReturn(List.of(appointment(APPOINTMENT_ID, FIRM_A, CASE_ID, AppointmentStatus.SCHEDULED)));

            mockMvc.perform(get("/appointments/case/{caseId}", CASE_ID).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lawFirmId").value(FIRM_A.toString()));

            verify(ownershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);
            verify(appointmentRepository).findAllByLawFirmIdAndCaseIdOrderByDateTimeAsc(FIRM_A, CASE_ID);
            }

    @Test
    void crossTenantAppointmentIdReturns404() throws Exception {
        when(appointmentRepository.findByIdAndLawFirmId(APPOINTMENT_ID, FIRM_B)).thenReturn(Optional.empty());

        mockMvc.perform(get("/appointments/{id}", APPOINTMENT_ID).with(jwtForB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Appointment not found: " + APPOINTMENT_ID));
    }

    @Test
    void falsifiedLawFirmQueryReturns403() throws Exception {
        mockMvc.perform(get("/appointments").queryParam("lawFirmId", FIRM_B.toString()).with(jwtForA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Requested lawFirmId does not match the authenticated law firm."));

        verify(appointmentRepository, never()).findAllByLawFirmIdOrderByDateTimeAsc(any());
    }

    @Test
    void falsifiedLawFirmBodyReturns403() throws Exception {
        mockMvc.perform(post("/appointments").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(null, FIRM_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Requested lawFirmId does not match the authenticated law firm."));

        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void listIsFilteredByAuthenticatedTenant() throws Exception {
        when(appointmentRepository.findAllByLawFirmIdOrderByDateTimeAsc(FIRM_A))
                .thenReturn(List.of(appointment(APPOINTMENT_ID, FIRM_A, CASE_ID, AppointmentStatus.SCHEDULED)));

        mockMvc.perform(get("/appointments").queryParam("lawFirmId", FIRM_A.toString()).with(jwtForA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$[1]").doesNotExist());

        verify(appointmentRepository).findAllByLawFirmIdOrderByDateTimeAsc(FIRM_A);
    }

    @Test
    void invalidStatusTransitionReturns409() throws Exception {
        when(appointmentRepository.findByIdAndLawFirmId(APPOINTMENT_ID, FIRM_A))
                .thenReturn(Optional.of(appointment(APPOINTMENT_ID, FIRM_A, CASE_ID, AppointmentStatus.COMPLETED)));

        mockMvc.perform(patch("/appointments/{id}/status", APPOINTMENT_ID)
                        .queryParam("status", "CANCELLED").with(jwtForA()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Appointment status transition from COMPLETED to CANCELLED is not allowed."));

        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void invalidAppointmentDateReturns400() throws Exception {
        mockMvc.perform(post("/appointments").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBodyWithDate(LocalDateTime.now().minusDays(1), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dateTime: must be a future date"));

        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    void foreignClientReturns422AndDoesNotCreate() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "client not found in your law firm"))
                .when(ownershipVerifier).verifyClientBelongsToCurrentTenant(CLIENT_ID);

        mockMvc.perform(post("/appointments").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(CASE_ID, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("client not found in your law firm"));

        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void foreignCaseReturns422AndDoesNotCreate() throws Exception {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "case not found in your law firm"))
                .when(ownershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);

        mockMvc.perform(post("/appointments").with(jwtForA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(CASE_ID, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("case not found in your law firm"));

        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    private Appointment appointment(UUID id, UUID lawFirmId, UUID caseId, AppointmentStatus appointmentStatus) {
        Appointment appointment = new Appointment(lawFirmId, caseId, CLIENT_ID, LAWYER_ID,
                LocalDateTime.now().plusDays(1), AppointmentType.CLIENT_MEETING, appointmentStatus,
                "Office", "Test appointment");
        ReflectionTestUtils.setField(appointment, "id", id);
        ReflectionTestUtils.setField(appointment, "createdAt", LocalDateTime.now());
        return appointment;
    }

    private String createBody(UUID caseId, UUID lawFirmId) {
        return createBodyWithDate(LocalDateTime.now().plusDays(1), caseId, lawFirmId);
    }

    private String createBodyWithDate(LocalDateTime dateTime, UUID caseId, UUID lawFirmId) {
        String lawFirmProperty = lawFirmId == null ? "" : "\"lawFirmId\":\"" + lawFirmId + "\",";
        String caseProperty = caseId == null ? "" : "\"caseId\":\"" + caseId + "\",";
        return "{" + lawFirmProperty + caseProperty
                + "\"clientId\":\"" + CLIENT_ID + "\","
                + "\"lawyerId\":\"" + LAWYER_ID + "\","
                + "\"dateTime\":\"" + dateTime + "\","
                + "\"type\":\"CLIENT_MEETING\","
                + "\"location\":\"Office\",\"notes\":\"Test appointment\"}";
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
