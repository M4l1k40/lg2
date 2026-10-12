package com.legalflow.client.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID lawFirmId;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    private String lastName;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String phone;

    @Column(length = 500)
    private String address;

    /** Avocats en charge du client : seuls eux (et le personnel administratif) peuvent le voir. */
    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "client_lawyers", joinColumns = @jakarta.persistence.JoinColumn(name = "client_id"))
    @Column(name = "lawyer_id", nullable = false)
    private java.util.Set<UUID> lawyerIds = new java.util.HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Client() {
    }

    public Client(UUID lawFirmId, String firstName, String lastName, String email, String phone, String address) {
        this.lawFirmId = lawFirmId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLawFirmId() {
        return lawFirmId;
    }

    public void setLawFirmId(UUID lawFirmId) {
        this.lawFirmId = lawFirmId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public java.util.Set<UUID> getLawyerIds() {
        return lawyerIds;
    }

    public void setLawyerIds(java.util.Set<UUID> lawyerIds) {
        this.lawyerIds = lawyerIds == null ? new java.util.HashSet<>() : new java.util.HashSet<>(lawyerIds);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
