package com.legalflow.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RabbitDomainEventPublisherTest {

    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher(rabbitTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsImmediatelyWhenNoTransactionIsActive() {
        DomainEventEnvelope event = event();

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(eq(EventsTopology.EVENTS_EXCHANGE), eq(EventTypes.CASE_CREATED),
                eq(event), any(CorrelationData.class));
    }

    @Test
    void waitsForCommitWhenATransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        DomainEventEnvelope event = event();

        publisher.publish(event);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(CorrelationData.class));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(rabbitTemplate, times(1)).convertAndSend(eq(EventsTopology.EVENTS_EXCHANGE),
                eq(EventTypes.CASE_CREATED), eq(event), any(CorrelationData.class));
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(event());
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(CorrelationData.class));
    }

    @Test
    void brokerFailureNeverReachesTheCaller() {
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertDoesNotThrow(() -> publisher.publish(event()));
    }

    private DomainEventEnvelope event() {
        return DomainEventEnvelope.of(EventTypes.CASE_CREATED, FIRM, "case-service", "CASE",
                UUID.randomUUID(), Map.of("message", "Nouveau dossier"));
    }
}
