# legalflow-events

Bibliothèque partagée : enveloppe d'événements, convention des destinataires et publication RabbitMQ.
Un service l'utilise en ajoutant la dépendance ; la publication s'active toute seule (auto-configuration).

```xml
<dependency>
    <groupId>com.legalflow</groupId>
    <artifactId>legalflow-events</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Convention des destinataires (SEC-07)

| Rôle du destinataire | `recipientId` | Fabrique |
|---|---|---|
| `CLIENT` | `clientId` (fiche client, claim `clientId`) | `EventRecipient.client(...)` |
| `LAWYER` | `sub` Keycloak de l'avocat | `EventRecipient.lawyer(...)` |
| `SECRETARY` | `sub` Keycloak du secrétaire | `EventRecipient.secretary(...)` |

Clés du `payload` lues par le `notification-service` : `actor`, `lawyer`, `client`, `creator`, et `message` (optionnel).
Les coordonnées (`email`, `phone`) viennent de l'événement : le consommateur n'a pas de token utilisateur.

## Publier

```java
publisher.publish(DomainEventEnvelope.of(EventTypes.CASE_CREATED, lawFirmId, "case-service",
        "CASE", legalCase.getId(), Map.of("actor", actor, "client", client)));
```

Dans une transaction, l'envoi a lieu après le commit ; en cas de rollback, rien n'est envoyé. Une panne du broker
ne fait pas échouer l'appelant (journalisée avec l'identifiant de l'événement). Limite connue : l'événement est
perdu si le broker est indisponible juste après le commit (le pattern Outbox corrigera cela).

## Configuration RabbitMQ à ajouter dans l'`application.yml` d'un service publisher

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_DEFAULT_USER:legalflow}
    password: ${RABBITMQ_DEFAULT_PASS:replace-with-rabbitmq-password}
    connection-timeout: 2s   # ne pas bloquer la réponse HTTP si le broker est injoignable
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

## Construire en local

```powershell
mvn -f infrastructure/legalflow-events/pom.xml install
```

À refaire après toute modification de la bibliothèque, avant de lancer `mvn test` dans un service.

Dans `management`, désactiver l'indicateur de santé RabbitMQ d'un publisher (`management.health.rabbit.enabled: false`) :
la publication est best-effort et ne doit pas rendre le service « DOWN ».

## Brancher un service publisher (3 changements dans le service, le reste est dans `infrastructure/`)

1. `pom.xml` du service : la dépendance ci-dessus.
2. `application.yml` du service : le bloc `spring.rabbitmq` ci-dessus.
3. `Dockerfile` du service : construire la bibliothèque depuis un contexte nommé fourni par le compose, sans changer
   le contexte de build du service :

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY --from=events . /events
RUN mvn -q -DskipTests -f /events/pom.xml install
# ... suite inchangée (COPY pom.xml, go-offline, COPY src, package)
```

Dans `infrastructure/docker-compose.yml`, l'entrée `build` du service déclare ce contexte (Docker Compose 2.17 ou plus) :

```yaml
    build:
      context: ../backend/services/case-service
      dockerfile: Dockerfile
      additional_contexts:
        events: ./legalflow-events
```
