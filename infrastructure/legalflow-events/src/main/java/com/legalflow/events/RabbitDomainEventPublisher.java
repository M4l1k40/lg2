package com.legalflow.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Publie sur l'exchange {@link EventsTopology#EVENTS_EXCHANGE} avec le type d'événement comme clé de routage.
 *
 * <p>Limite connue : si le broker est indisponible juste après le commit, l'événement est perdu (journalisé
 * avec son identifiant). Le pattern Outbox supprimera cette limite.
 */
public class RabbitDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitDomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("Event {} was not confirmed by the broker: {}",
                        correlation != null ? correlation.getId() : null, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "Event with routing key {} could not be routed (code {}, {})",
                returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
    }

    @Override
    public void publish(DomainEventEnvelope event) {
        Objects.requireNonNull(event, "event");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(DomainEventEnvelope event) {
        try {
            rabbitTemplate.convertAndSend(EventsTopology.EVENTS_EXCHANGE, event.eventType(), event,
                    new CorrelationData(String.valueOf(event.eventId())));
        } catch (RuntimeException exception) {
            // Le payload n'est volontairement pas journalisé : il contient des données personnelles.
            log.error("Failed to publish event {} ({}) for law firm {}",
                    event.eventId(), event.eventType(), event.lawFirmId(), exception);
        }
    }
}
