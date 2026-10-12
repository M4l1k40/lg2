package com.legalflow.user.controller;

import com.legalflow.user.dto.CreateUserRequest;
import com.legalflow.user.dto.CreatedUserResponse;
import com.legalflow.user.dto.EnabledRequest;
import com.legalflow.user.dto.LawyerSummary;
import com.legalflow.user.dto.MeResponse;
import com.legalflow.user.dto.PasswordResetResponse;
import com.legalflow.user.dto.UserResponse;
import com.legalflow.user.service.UserDirectoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserDirectoryService userDirectoryService;

    public UserController(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    /** Profil de l'utilisateur connecté. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public MeResponse me() {
        return userDirectoryService.currentUser();
    }

    /** Annuaire complet du cabinet : réservé à l'administrateur. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> listUsers() {
        return userDirectoryService.listUsers();
    }

    /** Liste minimale des avocats actifs (pour assigner une consultation ou planifier un rendez-vous). */
    @GetMapping("/lawyers")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER')")
    public List<LawyerSummary> listLawyers() {
        return userDirectoryService.listLawyers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userDirectoryService.createUser(request);
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse setEnabled(@PathVariable String id, @Valid @RequestBody EnabledRequest request) {
        return userDirectoryService.setEnabled(id, request.enabled());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public PasswordResetResponse resetPassword(@PathVariable String id) {
        return userDirectoryService.resetPassword(id);
    }
}
