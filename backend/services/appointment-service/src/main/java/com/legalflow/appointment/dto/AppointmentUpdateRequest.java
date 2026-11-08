package com.legalflow.appointment.dto;

import com.legalflow.appointment.domain.AppointmentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Champs modifiables d'un rendez-vous planifié. Le dossier, le client et l'avocat ne changent pas : pour les
 * changer, on annule le rendez-vous et on en crée un nouveau.
 */
public class AppointmentUpdateRequest {

    @NotNull
    @Future
    private LocalDateTime dateTime;

    @NotNull
    private AppointmentType type;

    @Size(max = 500)
    private String location;

    @Size(max = 2000)
    private String notes;

    public LocalDateTime getDateTime() { return dateTime; }
    public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    public AppointmentType getType() { return type; }
    public void setType(AppointmentType type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
