package com.legalflow.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Active la publication d'événements dès que la bibliothèque est dans le classpath d'un service. */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
public class EventsAutoConfiguration {

    /** Format JSON commun aux publishers et aux consommateurs (ignoré si le service définit déjà le sien). */
    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter legalflowEventsMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** Déclaration idempotente : le publisher ne dépend pas de l'ordre de démarrage du notification-service. */
    @Bean
    @ConditionalOnMissingBean(name = "legalflowEventsExchange")
    public TopicExchange legalflowEventsExchange() {
        return new TopicExchange(EventsTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    public DomainEventPublisher domainEventPublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitDomainEventPublisher(rabbitTemplate);
    }
}
