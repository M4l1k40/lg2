package com.legalflow.appointment.service;

import com.legalflow.appointment.domain.Appointment;
import com.legalflow.appointment.domain.AppointmentStatus;
import com.legalflow.appointment.domain.AppointmentType;
import com.legalflow.appointment.dto.AppointmentResponse;
import com.legalflow.appointment.dto.AppointmentUpdateRequest;
import com.legalflow.appointment.integration.AppointmentOwnershipVerifier;
import com.legalflow.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentServiceUpdateTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LAWYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_LAWYER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECRETARY_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID APPOINTMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AppointmentRepository repository;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppointmentRepository.class);
        service = new AppointmentService(repository, mock(AppointmentOwnershipVerifier.class));
        when(repository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lawyerModifiesHisOwnScheduledAppointment() {
        authenticate(LAWYER_ID, "LAWYER");
        stub(appointment(LAWYER_ID, AppointmentStatus.SCHEDULED));
        LocalDateTime newDate = LocalDateTime.now().plusDays(5);

        AppointmentResponse response = service.update(APPOINTMENT_ID, request(newDate, AppointmentType.COURT_HEARING, "Palais", "Apporter les pièces"));

        assertEquals(newDate, response.dateTime());
        assertEquals(AppointmentType.COURT_HEARING, response.type());
        assertEquals("Palais", response.location());
        assertEquals("Apporter les pièces", response.notes());
        assertEquals(AppointmentStatus.SCHEDULED, response.status());
        assertEquals(CLIENT_ID, response.clientId());
        assertEquals(LAWYER_ID, response.lawyerId());
    }

    @Test
    void officeStaffModifiesAnyAppointmentOfTheFirm() {
        authenticate(SECRETARY_ID, "SECRETARY");
        stub(appointment(LAWYER_ID, AppointmentStatus.SCHEDULED));

        AppointmentResponse response = service.update(APPOINTMENT_ID,
                request(LocalDateTime.now().plusDays(3), AppointmentType.CLIENT_MEETING, null, null));

        assertEquals(LAWYER_ID, response.lawyerId());
    }

    @Test
    void anotherLawyersAppointmentLooksMissing() {
        authenticate(OTHER_LAWYER_ID, "LAWYER");
        stub(appointment(LAWYER_ID, AppointmentStatus.SCHEDULED));

        assertStatus(HttpStatus.NOT_FOUND, () -> service.update(APPOINTMENT_ID,
                request(LocalDateTime.now().plusDays(3), AppointmentType.CLIENT_MEETING, null, null)));

        verify(repository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void completedAndCancelledAppointmentsCannotBeModified() {
        authenticate(LAWYER_ID, "LAWYER");
        for (AppointmentStatus status : new AppointmentStatus[] {AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED}) {
            stub(appointment(LAWYER_ID, status));

            assertStatus(HttpStatus.CONFLICT, () -> service.update(APPOINTMENT_ID,
                    request(LocalDateTime.now().plusDays(3), AppointmentType.CLIENT_MEETING, null, null)));
        }
        verify(repository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void aClientCannotModifyAnAppointment() {
        authenticate(UUID.randomUUID(), "CLIENT", CLIENT_ID);
        stub(appointment(LAWYER_ID, AppointmentStatus.SCHEDULED));

        assertStatus(HttpStatus.FORBIDDEN, () -> service.update(APPOINTMENT_ID,
                request(LocalDateTime.now().plusDays(3), AppointmentType.CLIENT_MEETING, null, null)));
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected.value(), exception.getStatusCode().value());
    }

    private Appointment appointment(UUID lawyerId, AppointmentStatus status) {
        Appointment appointment = new Appointment(FIRM_A, null, CLIENT_ID, lawyerId, LocalDateTime.now().plusDays(1),
                AppointmentType.CLIENT_MEETING, status, "Cabinet", "Notes");
        ReflectionTestUtils.setField(appointment, "id", APPOINTMENT_ID);
        ReflectionTestUtils.setField(appointment, "createdAt", LocalDateTime.now());
        return appointment;
    }

    private void stub(Appointment appointment) {
        when(repository.findByIdAndLawFirmId(APPOINTMENT_ID, FIRM_A)).thenReturn(Optional.of(appointment));
    }

    private AppointmentUpdateRequest request(LocalDateTime dateTime, AppointmentType type, String location, String notes) {
        AppointmentUpdateRequest request = new AppointmentUpdateRequest();
        request.setDateTime(dateTime);
        request.setType(type);
        request.setLocation(location);
        request.setNotes(notes);
        return request;
    }

    private void authenticate(UUID userId, String role) {
        authenticate(userId, role, null);
    }

    private void authenticate(UUID userId, String role, UUID clientId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(userId.toString());
        if (clientId != null) {
            builder.claim("clientId", clientId.toString());
        }
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }
}
