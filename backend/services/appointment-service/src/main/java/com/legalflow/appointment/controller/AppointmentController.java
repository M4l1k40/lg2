package com.legalflow.appointment.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.legalflow.appointment.dto.AppointmentRequest;
import com.legalflow.appointment.dto.AppointmentResponse;
import com.legalflow.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public List<AppointmentResponse> getAll(@RequestParam(required = false) UUID lawFirmId) {
        return appointmentService.getAll(lawFirmId);
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public List<AppointmentResponse> getByCase(@PathVariable UUID caseId) {
        return appointmentService.getByCase(caseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER')")
    public AppointmentResponse create(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public AppointmentResponse getById(@PathVariable UUID id) {
        return appointmentService.getById(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public AppointmentResponse updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return appointmentService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public void delete(@PathVariable UUID id) {
        appointmentService.delete(id);
    }
}
