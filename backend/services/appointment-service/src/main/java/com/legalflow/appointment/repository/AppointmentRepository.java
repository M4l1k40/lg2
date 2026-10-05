package com.legalflow.appointment.repository;

import com.legalflow.appointment.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findAllByLawFirmIdOrderByDateTimeAsc(UUID lawFirmId);

    List<Appointment> findAllByLawFirmIdAndCaseIdOrderByDateTimeAsc(UUID lawFirmId, UUID caseId);

    Optional<Appointment> findByIdAndLawFirmId(UUID id, UUID lawFirmId);
}
