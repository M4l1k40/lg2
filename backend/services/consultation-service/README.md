# Consultation Service

## Objectif

Le service de consultation gère les demandes juridiques soumises par un client d'un cabinet, leur affectation à un avocat, puis leur réponse et fermeture.

## Port et base

- Port application : 8089
- Base PostgreSQL interne : consultation-db:5432
- Base PostgreSQL externe : 5440 (convention project : éviter le port 5439 déjà utilisé par billing-db)

## Endpoints

- GET /consultations
- POST /consultations
- GET /consultations/{id}
- PUT /consultations/{id}
- DELETE /consultations/{id}
- PUT /consultations/{id}/assign
- PUT /consultations/{id}/start
- PUT /consultations/{id}/answer
- PUT /consultations/{id}/close
- PUT /consultations/{id}/cancel

## Etats et transitions

- PENDING -> ASSIGNED -> IN_PROGRESS -> ANSWERED -> CLOSED
- PENDING/ASSIGNED/IN_PROGRESS -> CANCELLED
- Les transitions incohérentes déclenchent une erreur métier claire.

## Sécurité et multi-tenancy

Le service utilise le mécanisme JWT existant de LegalFlow et lit le claim `lawFirmId` depuis le token authentifié. La valeur fournie côté frontend est ignorée et l'accès se fait uniquement sur le tenant du JWT.

## Interaction avec client-service

Lors de la création d'une consultation, le service vérifie que `clientId` appartient au même cabinet via le service client. Si la ressource n'est pas trouvée, la création est refusée avec un statut métier adapté.

## Démarrage Docker

```powershell
docker compose up -d --build consultation-db consultation-service
```

## Préparation pour RabbitMQ

Le code est structuré pour permettre une intégration future d'événements métier (`CONSULTATION_CREATED`, etc.) sans installer RabbitMQ ni ajouter de dépendance broker pour l'instant.
