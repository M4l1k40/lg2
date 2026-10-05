package com.legalflow.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationEventProcessor processor;

    public NotificationEventListener(NotificationEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "legalflow.notification.queue")
    public void onMessage(DomainEventEnvelope event) {
        try {
            processor.process(event);
        } catch (Exception exception) {
            log.error("Failed to process notification event {}", event != null ? event.eventId() : null, exception);
            throw new AmqpRejectAndDontRequeueException("Invalid or unprocessable event payload", exception);
        }
    }
}
