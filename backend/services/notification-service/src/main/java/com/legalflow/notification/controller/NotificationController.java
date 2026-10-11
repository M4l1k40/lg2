package com.legalflow.notification.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.legalflow.notification.dto.NotificationCreateRequest;
import com.legalflow.notification.dto.NotificationResponse;
import com.legalflow.notification.dto.ReadAllResponse;
import com.legalflow.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public List<NotificationResponse> getAll() {
        return notificationService.getAll();
    }

    @GetMapping("/unread")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public List<NotificationResponse> getUnread() {
        return notificationService.getUnread();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER')")
    public NotificationResponse create(@Valid @RequestBody NotificationCreateRequest request) {
        return notificationService.create(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public NotificationResponse getById(@PathVariable UUID id) {
        return notificationService.getById(id);
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public NotificationResponse getStatus(@PathVariable UUID id) {
        return notificationService.getStatus(id);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public ReadAllResponse markAllRead() {
        return notificationService.markAllRead();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','CLIENT')")
    public void delete(@PathVariable UUID id) {
        notificationService.delete(id);
    }
}
