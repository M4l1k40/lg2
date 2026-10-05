package com.legalflow.notification.repository;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndLawFirmIdAndRecipientId(UUID id, UUID lawFirmId, UUID recipientId);

        Optional<Notification> findByEventIdAndLawFirmIdAndRecipientIdAndChannel(
            UUID eventId, UUID lawFirmId, UUID recipientId, NotificationChannel channel);

    List<Notification> findAllByLawFirmIdAndRecipientIdOrderByCreatedAtDesc(UUID lawFirmId, UUID recipientId);

    List<Notification> findAllByLawFirmIdAndRecipientIdAndReadFalseOrderByCreatedAtDesc(
            UUID lawFirmId, UUID recipientId);

    List<Notification> findAllByLawFirmIdAndRecipientIdAndReadFalse(UUID lawFirmId, UUID recipientId);

    long countByLawFirmIdAndRecipientIdAndReadFalse(UUID lawFirmId, UUID recipientId);
}
