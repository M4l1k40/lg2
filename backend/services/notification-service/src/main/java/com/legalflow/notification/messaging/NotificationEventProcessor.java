package com.legalflow.notification.messaging;

import com.legalflow.notification.domain.Notification;
import com.legalflow.notification.domain.NotificationChannel;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.domain.ProcessedEvent;
import com.legalflow.notification.repository.NotificationRepository;
import com.legalflow.notification.repository.ProcessedEventRepository;
import com.legalflow.notification.service.NotificationDeliveryException;
import com.legalflow.notification.service.NotificationRecipientContact;
import com.legalflow.notification.service.NotificationRoutingService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationEventProcessor {

    private final NotificationEventMapper eventMapper;
    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRoutingService notificationRoutingService;

    public NotificationEventProcessor(NotificationEventMapper eventMapper,
                                     NotificationRepository notificationRepository,
                                     ProcessedEventRepository processedEventRepository,
                                     NotificationRoutingService notificationRoutingService) {
        this.eventMapper = eventMapper;
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.notificationRoutingService = notificationRoutingService;
    }

    @Transactional
    public void process(DomainEventEnvelope event) {
        if (event == null || event.eventId() == null) {
            return;
        }
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        MappedEventNotification mappedEvent = eventMapper.map(event);
        for (EventRecipient recipient : mappedEvent.recipients()) {
            NotificationRecipientType recipientType = NotificationRecipientType.valueOf(recipient.recipientRole());
            NotificationChannel channel = notificationRoutingService.channelFor(recipientType);
            if (notificationRepository.findByEventIdAndLawFirmIdAndRecipientIdAndChannel(
                    event.eventId(), event.lawFirmId(), recipient.recipientId(), channel).isPresent()) {
                continue;
            }

            Notification notification = new Notification(
                    event.eventId(),
                    event.lawFirmId(),
                    recipient.recipientId(),
                    recipientType,
                    channel,
                    mappedEvent.title(),
                    mappedEvent.title(),
                    mappedEvent.message(),
                    mappedEvent.type(),
                    "event",
                    event.aggregateId());

            notification = notificationRepository.saveAndFlush(notification);
            try {
                notificationRoutingService.send(notification,
                        new NotificationRecipientContact(recipient.name(), recipient.email(), recipient.phone()));
                notification.markSent(LocalDateTime.now());
            } catch (NotificationDeliveryException exception) {
                notification.markFailed(exception.getMessage());
            } catch (RuntimeException exception) {
                notification.markFailed("Notification provider failed to send notification.");
            }
            notificationRepository.saveAndFlush(notification);
        }

        processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId(), event.lawFirmId(), event.eventType()));
    }
}
