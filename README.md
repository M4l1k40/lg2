# LegalFlow

Plateforme SaaS de gestion de cabinets d'avocats : dossiers, consultations, clients et notifications, avec une architecture **microservices** sécurisée par **Keycloak (OIDC / JWT)**.

> Statut : en développement actif. Voir [Feuille de route](#feuille-de-route).

---

## Sommaire

1. [Fonctionnalités](#fonctionnalités)
2. [Architecture](#architecture)
3. [Stack technique](#stack-technique)
4. [Structure du dépôt](#structure-du-dépôt)
5. [Démarrage rapide](#démarrage-rapide)
6. [Authentification et rôles](#authentification-et-rôles)
7. [Événements asynchrones (RabbitMQ)](#événements-asynchrones-rabbitmq)
8. [Tests](#tests)
9. [Sécurité](#sécurité)
10. [Feuille de route](#feuille-de-route)
11. [Dépannage](#dépannage)

---

## Fonctionnalités

| Domaine | Description |
|---|---|
| Authentification | Connexion unique via Keycloak, JWT, contrôle d'accès par rôle |
| Dossiers | Création et suivi des dossiers par les avocats |
| Consultations | Gestion des consultations et notifications associées |
| Clients | Fiches clients, invitation par email (lien Keycloak pour choisir son mot de passe) |
| Notifications | Email (Mailpit en dev) et WhatsApp, déclenchées par des événements métier |
| Espaces dédiés | Espace avocat, client, secrétaire et administrateur |

---

## Architecture

```
                    ┌──────────────────────┐
                    │   Frontend React     │
                    │   (Vite)             │
                    └──────────┬───────────┘
                               │  Bearer JWT
                               ▼
                    ┌──────────────────────┐        ┌───────────────┐
                    │ Gateway (Spring Cloud│◄──────►│   Keycloak    │
                    │ Gateway, WebFlux)    │  OIDC  │ realm:        │
                    │ :9000                │        │ legalflow     │
                    └──────────┬───────────┘        └───────────────┘
         ┌──────────┬──────────┼───────────┬──────────────┐
         ▼          ▼          ▼           ▼              ▼
    user-service  client-   case-     consultation-  notification-
                  service   service   service        service
                               │           │              ▲
                               └─────┬─────┘              │
                                     ▼                    │
                              ┌─────────────┐   events    │
                              │  RabbitMQ   │─────────────┘
                              └─────────────┘
```

Principes clés :

- Le **frontend ne consomme que le JWT** ; il ne décide jamais de l'identité du cabinet.
- Le `lawFirmId` provient **uniquement du JWT**, jamais d'un paramètre envoyé par le frontend.
- Aucune donnée personnelle ne transite dans RabbitMQ : les coordonnées des destinataires sont résolues par `notification-service` via un compte de service Keycloak (`client_credentials`) et un endpoint interne de `client-service`.

---

## Stack technique

- **Frontend** : React, Vite
- **Backend** : Java, Spring Boot (microservices), Spring Cloud Gateway (WebFlux)
- **Identité** : Keycloak (realm `legalflow`, client `legalflow-frontend`)
- **Messagerie** : RabbitMQ
- **Email de dev** : Mailpit
- **Conteneurs** : Docker, Docker Compose
- **Build / tests** : Maven, npm

---

## Structure du dépôt

```
.
├── backend/
│   ├── libs/
│   │   └── legalflow-events/     # module Maven partagé (enveloppe des événements)
│   └── ...                        # services : user, client, case, consultation, notification, gateway
├── frontend/                      # application React/Vite
├── infrastructure/                # docker-compose, Keycloak, RabbitMQ, Mailpit
└── docs/
    └── security-findings.md       # registre des failles et correctifs (SEC-xx)
```


## Démarrage rapide

### Prérequis

- Docker Desktop
- JDK et Maven (versions alignées sur les `pom.xml`)
- Node.js et npm

### 1. Lancer l'infrastructure et les services backend

```powershell
cd infrastructure
docker compose up -d --build
```

| Composant | URL |
|---|---|
| Gateway | http://localhost:9000 |
| Keycloak | http://localhost:8080 |
| Mailpit (emails de dev) | à renseigner selon votre `docker-compose.yml` |

### 2. Lancer le frontend

```powershell
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Pour tester le build de production :

```powershell
npm run build
npm run preview    # http://localhost:4173
```

### 3. Configuration Keycloak

Le realm `legalflow` et le client `legalflow-frontend` doivent autoriser **les ports réellement utilisés par le frontend** :

| Champ | Valeurs |
|---|---|
| Valid Redirect URIs | `http://localhost:5173/*`, `http://localhost:4173/*` (+ équivalents `127.0.0.1`) |
| Web Origins | `http://localhost:5173`, `http://localhost:4173` (+ équivalents `127.0.0.1`) |

Compte de test : `lawyer-a` (rôle `LAWYER`).

---

## Authentification et rôles

Flux de connexion :

```
/login → Keycloak → authentification → retour React → JWT
       → lecture de realm_access.roles → redirection selon le rôle
       (LAWYER → /lawyer/dashboard)
```

| Rôle | Espace |
|---|---|
| `LAWYER` | Dossiers, consultations, clients |
| `CLIENT` | Espace client |
| Secrétaire | Gestion des clients, ajout d'avocats |
| Administrateur | Administration du cabinet |

Les clients créés par le personnel reçoivent une **invitation par email** : un lien Keycloak leur permet de définir leur propre mot de passe (aucun mot de passe n'est transmis en clair).

---

## Événements asynchrones (RabbitMQ)

Les services publient des événements métier ; `notification-service` les consomme pour notifier les destinataires.

| Publisher | Événements |
|---|---|
| `case-service` | `CASE_CREATED` (validé de bout en bout) |
| `consultation-service` | 5 événements de consultation (validés) |
| Autres services | À venir |

Le contrat des messages (enveloppe) est défini dans le module partagé `backend/libs/legalflow-events`.

Règle de rattachement des notifications :

- **Client** : rattachée à sa fiche (`clientId`)
- **Personnel** : rattachée à son compte (`sub` Keycloak)

---

## Tests

```powershell
# Tests unitaires backend
mvn test

# Tests de bout en bout (PowerShell, stack lancée)
./test-consultation-notifications.ps1
./test-client-invitation.ps1
```

---

## Sécurité

- Authentification déléguée à Keycloak (OIDC), validation JWT côté backend.
- Isolation par cabinet : `lawFirmId` lu uniquement dans le JWT.
- Compte de service dédié (`SERVICE_CONTACT_LOOKUP`) pour la résolution des coordonnées, sans donnée personnelle dans les messages.
- Les failles identifiées et leur traitement sont suivies dans [`docs/security-findings.md`](docs/security-findings.md).

Pour signaler une vulnérabilité, contactez le mainteneur en privé plutôt que d'ouvrir une issue publique.

---

## Feuille de route

- [x] Authentification Keycloak / JWT
- [x] Pipeline RabbitMQ (`CASE_CREATED`, événements de consultation)
- [x] Notifications email (Mailpit) et invitation des clients
- [ ] Persistance de Keycloak (volume) pour conserver les comptes du seed
- [ ] Publishers pour les événements restants
- [ ] Tableaux de bord client / secrétaire / admin branchés sur des données réelles
- [ ] Coordonnées du personnel pour les notifications
- [ ] Durcissement de la gateway

---

## Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `invalid redirect_uri` à la connexion | Le port du frontend n'est pas déclaré dans Keycloak | Ajouter `http://localhost:<port>/*` dans *Valid Redirect URIs* et *Web Origins* |
| Comptes de test disparus après recréation du conteneur Keycloak | Keycloak en `dev-file` sans volume | Monter un volume pour les données Keycloak |
| Notification WhatsApp en `FAILED` | Numéro de téléphone absent sur la fiche client | Renseigner le téléphone du client |
| Email en `FAILED` | Serveur SMTP non configuré | Vérifier Mailpit et la config SMTP du realm |
