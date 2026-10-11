package com.legalflow.appointment.service;

import com.legalflow.appointment.domain.Appointment;
import com.legalflow.appointment.domain.AppointmentStatus;
import com.legalflow.appointment.dto.AppointmentRequest;
import com.legalflow.appointment.dto.AppointmentResponse;
import com.legalflow.appointment.integration.AppointmentOwnershipVerifier;
import com.legalflow.appointment.repository.AppointmentRepository;
import com.legalflow.appointment.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> STATUS_TRANSITIONS = Map.of(
            AppointmentStatus.SCHEDULED, Set.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED),
            AppointmentStatus.COMPLETED, Set.of(),
            AppointmentStatus.CANCELLED, Set.of());

    private final AppointmentRepository appointmentRepository;
    private final AppointmentOwnershipVerifier ownershipVerifier;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              AppointmentOwnershipVerifier ownershipVerifier) {
        this.appointmentRepository = appointmentRepository;
        this.ownershipVerifier = ownershipVerifier;
    }

    /**
     * ADMIN/SECRETARY : tous les rendez-vous du cabinet.
     * LAWYER : uniquement les siens. CLIENT : uniquement les siens.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAll(UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);
        List<Appointment> appointments;
        if (TenantContext.isOfficeStaff()) {
            appointments = appointmentRepository.findAllByLawFirmIdOrderByDateTimeAsc(lawFirmId);
        } else if (TenantContext.isLawyer()) {
            appointments = appointmentRepository.findAllByLawFirmIdAndLawyerIdOrderByDateTimeAsc(
                    lawFirmId, TenantContext.requireUserId());
        } else {
            appointments = appointmentRepository.findAllByLawFirmIdAndClientIdOrderByDateTimeAsc(
                    lawFirmId, TenantContext.requireClientId());
        }
        return appointments.stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getByCase(UUID caseId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        ownershipVerifier.verifyCaseBelongsToCurrentTenant(caseId);
        return appointmentRepository.findAllByLawFirmIdAndCaseIdOrderByDateTimeAsc(lawFirmId, caseId).stream()
                .filter(this::isVisibleToCurrentUser)
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(request.getLawFirmId(), lawFirmId);
        if (!TenantContext.isOfficeStaff()) {
            // Un avocat ne peut planifier que ses propres rendez-vous.
            if (!TenantContext.isLawyer() || !TenantContext.requireUserId().equals(request.getLawyerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A lawyer can only schedule appointments for themselves.");
            }
        }
        ownershipVerifier.verifyClientBelongsToCurrentTenant(request.getClientId());
        ownershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());
        // TODO: validate lawyerId once a user/lawyer directory service exists
        Appointment appointment = new Appointment(
                lawFirmId,
                request.getCaseId(),
                request.getClientId(),
                request.getLawyerId(),
                request.getDateTime(),
                request.getType(),
                AppointmentStatus.SCHEDULED,
                request.getLocation(),
                request.getNotes());
        return AppointmentResponse.from(appointmentRepository.saveAndFlush(appointment));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID id) {
        return AppointmentResponse.from(findOwnedAppointment(id));
    }

    @Transactional
    public AppointmentResponse updateStatus(UUID id, String requestedStatus) {
        Appointment appointment = findOwnedAppointment(id);
        AppointmentStatus nextStatus;
        try {
            nextStatus = AppointmentStatus.valueOf(requestedStatus.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Unknown appointment status: " + requestedStatus);
        }

        if (!TenantContext.isStaff() && nextStatus != AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A client can only cancel an appointment.");
        }
        AppointmentStatus currentStatus = appointment.getStatus();
        if (!STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Appointment status transition from " + currentStatus + " to " + nextStatus + " is not allowed.");
        }

        appointment.setStatus(nextStatus);
        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional
    public void delete(UUID id) {
        Appointment appointment = findOwnedAppointment(id);
        if (!TenantContext.isOfficeStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the office staff can delete appointments.");
        }
        appointmentRepository.delete(appointment);
    }

    private Appointment findOwnedAppointment(UUID id) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        Appointment appointment = appointmentRepository.findByIdAndLawFirmId(id, lawFirmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Appointment not found: " + id));
        if (!isVisibleToCurrentUser(appointment)) {
            // 404 plutôt que 403 : on ne révèle pas l'existence des rendez-vous des autres.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found: " + id);
        }
        return appointment;
    }

    private boolean isVisibleToCurrentUser(Appointment appointment) {
        if (TenantContext.isOfficeStaff()) {
            return true;
        }
        if (TenantContext.isLawyer()) {
            return TenantContext.requireUserId().equals(appointment.getLawyerId());
        }
        return TenantContext.requireClientId().equals(appointment.getClientId());
    }

    private void rejectMismatchedTenant(UUID requestedLawFirmId, UUID authenticatedLawFirmId) {
        if (requestedLawFirmId != null && !authenticatedLawFirmId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Requested lawFirmId does not match the authenticated law firm.");
        }
    }
}
