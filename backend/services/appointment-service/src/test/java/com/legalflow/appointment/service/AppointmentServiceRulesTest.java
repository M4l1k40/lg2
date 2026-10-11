package com.legalflow.appointment.service;

import com.legalflow.appointment.domain.Appointment;
import com.legalflow.appointment.domain.AppointmentStatus;
import com.legalflow.appointment.domain.AppointmentType;
import com.legalflow.appointment.dto.AppointmentRequest;
import com.legalflow.appointment.integration.AppointmentOwnershipVerifier;
import com.legalflow.appointment.repository.AppointmentRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

class AppointmentServiceRulesTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private void authenticate(UUID userId, UUID clientId, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(userId.toString());
        if (clientId != null) {
            builder.claim("clientId", clientId.toString());
        }
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }

    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID LAWYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_LAWYER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECRETARY_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID APPOINTMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AppointmentRepository repository;
    private AppointmentOwnershipVerifier verifier;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        repository = mock(AppointmentRepository.class);
        verifier = mock(AppointmentOwnershipVerifier.class);
        service = new AppointmentService(repository, verifier);
        when(repository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(repository.save(any(Appointment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listingDependsOnTheRole() {
        authenticate(SECRETARY_ID, null, "SECRETARY");
        service.getAll(null);
        verify(repository).findAllByLawFirmIdOrderByDateTimeAsc(FIRM_A);

        authenticate(LAWYER_ID, null, "LAWYER");
        service.getAll(null);
        verify(repository).findAllByLawFirmIdAndLawyerIdOrderByDateTimeAsc(FIRM_A, LAWYER_ID);

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        service.getAll(null);
        verify(repository).findAllByLawFirmIdAndClientIdOrderByDateTimeAsc(FIRM_A, CLIENT_ID);
    }

    @Test
    void clientAndLawyerCannotReadSomeoneElsesAppointment() {
        stub(appointment(CLIENT_ID, LAWYER_ID, AppointmentStatus.SCHEDULED));

        authenticate(UUID.randomUUID(), OTHER_CLIENT_ID, "CLIENT");
        ResponseStatusException clientAttempt = assertThrows(ResponseStatusException.class, () -> service.getById(APPOINTMENT_ID));
        assertEquals(HttpStatus.NOT_FOUND, clientAttempt.getStatusCode());

        authenticate(OTHER_LAWYER_ID, null, "LAWYER");
        ResponseStatusException lawyerAttempt = assertThrows(ResponseStatusException.class, () -> service.getById(APPOINTMENT_ID));
        assertEquals(HttpStatus.NOT_FOUND, lawyerAttempt.getStatusCode());

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        assertEquals(APPOINTMENT_ID, service.getById(APPOINTMENT_ID).id());
    }

    @Test
    void lawyerCanOnlyScheduleForThemselves() {
        authenticate(LAWYER_ID, null, "LAWYER");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(request(OTHER_LAWYER_ID)));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(repository, never()).saveAndFlush(any(Appointment.class));

        assertEquals(AppointmentStatus.SCHEDULED, service.create(request(LAWYER_ID)).status());
    }

    @Test
    void secretaryCanScheduleForAnyLawyer() {
        authenticate(SECRETARY_ID, null, "SECRETARY");

        assertEquals(AppointmentStatus.SCHEDULED, service.create(request(OTHER_LAWYER_ID)).status());
        verify(verifier).verifyClientBelongsToCurrentTenant(CLIENT_ID);
    }

    @Test
    void clientCanCancelItsOwnAppointmentButNotCompleteIt() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        stub(appointment(CLIENT_ID, LAWYER_ID, AppointmentStatus.SCHEDULED));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateStatus(APPOINTMENT_ID, "COMPLETED"));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        assertEquals(AppointmentStatus.CANCELLED, service.updateStatus(APPOINTMENT_ID, "CANCELLED").status());
    }

    @Test
    void onlyOfficeStaffCanDelete() {
        stub(appointment(CLIENT_ID, LAWYER_ID, AppointmentStatus.SCHEDULED));

        authenticate(LAWYER_ID, null, "LAWYER");
        ResponseStatusException lawyerAttempt = assertThrows(ResponseStatusException.class, () -> service.delete(APPOINTMENT_ID));
        assertEquals(HttpStatus.FORBIDDEN, lawyerAttempt.getStatusCode());
        verify(repository, never()).delete(any(Appointment.class));

        authenticate(SECRETARY_ID, null, "SECRETARY");
        service.delete(APPOINTMENT_ID);
        verify(repository).delete(any(Appointment.class));
    }

    private Appointment withId(Appointment appointment) {
        ReflectionTestUtils.setField(appointment, "id", APPOINTMENT_ID);
        ReflectionTestUtils.setField(appointment, "createdAt", LocalDateTime.now());
        return appointment;
    }

    private Appointment appointment(UUID clientId, UUID lawyerId, AppointmentStatus status) {
        return withId(new Appointment(FIRM_A, null, clientId, lawyerId, LocalDateTime.now().plusDays(1),
                AppointmentType.CLIENT_MEETING, status, "Cabinet", "Notes"));
    }

    private void stub(Appointment appointment) {
        when(repository.findByIdAndLawFirmId(APPOINTMENT_ID, FIRM_A)).thenReturn(Optional.of(appointment));
    }

    private AppointmentRequest request(UUID lawyerId) {
        AppointmentRequest request = new AppointmentRequest();
        request.setClientId(CLIENT_ID);
        request.setLawyerId(lawyerId);
        request.setDateTime(LocalDateTime.now().plusDays(2));
        request.setType(AppointmentType.CLIENT_MEETING);
        return request;
    }
}
