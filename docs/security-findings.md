# Registre des failles et points faibles — LegalFlow

Registre tenu pendant le développement : chaque point est décrit, classé, puis corrigé à l'étape de la feuille de route où il devient naturel
(ou accepté explicitement). Une faille « Corrigée » renvoie au script qui le prouve.

**Gravité** : Élevée = fuite de données ou prise de contrôle possible ; Moyenne = exploitable sous conditions ; Faible / Info = durcissement ou confort.

## 1. Failles corrigées

| ID | Faille | Gravité | Correctif | Preuve |
|---|---|---|---|---|
| SEC-01 | N'importe quel utilisateur connecté (même un client) pouvait lister et créer des clients et des dossiers (`client-service`, `case-service`). | Élevée | Contrôle de rôle par endpoint (`@PreAuthorize`) | `test-role-access.ps1` |
| SEC-02 | Un client voyait **tous** les dossiers du cabinet (filtrage par cabinet seulement, pas par client). | Élevée | Claim `clientId` posé par un administrateur + filtre côté service ; `404` pour les dossiers des autres | `test-role-access.ps1` |
| SEC-03 | `consultation-service` sans aucun contrôle de rôle ni de propriétaire. | Élevée | Règles par rôle + avocat assigné uniquement pour démarrer/répondre | `test-consultation-flow.ps1` |
| SEC-04 | Rendez-vous, factures, échéances, documents, judiciaire et notifications sans contrôle de rôle. | Élevée | Rôles par endpoint + visibilité des données (client, avocat, `visibility` des documents) | `test-role-access-services.ps1` |
| SEC-05 | Gateway : seule la route `/api/clients/**` exigeait un token ; CORS absent. | Moyenne | `/api/**` authentifié, CORS limité à l'origine du frontend | `curl` sans token → `401` |
| SEC-06 | Aucun moyen de créer les comptes d'un cabinet (comptes créés à la main dans Keycloak) ; l'assignation d'une consultation demandait un UUID d'avocat saisi à la main. | Moyenne | `user-service` (annuaire des avocats, création de comptes, `lawFirmId` forcé depuis le token) | `test-user-service.ps1` |
| SEC-20 | **Cloisonnement des avocats absent** : un avocat voyait tous les clients, dossiers, factures, échéances, documents et affaires judiciaires du cabinet (le RBAC ne contrôlait que le rôle, pas la propriété des données). Découvert en testant un compte avocat créé par l'administrateur. | Élevée | Clients : liste d'avocats en charge (`client_lawyers`). Dossiers : visibles par l'avocat assigné. Factures, échéances, documents, judiciaire : visibles si le `case-service` accepte le dossier. Un avocat n'ouvre un dossier que pour lui-même, et l'administration ne peut assigner qu'un avocat en charge du client. | `test-lawyer-scope.ps1` |
| SEC-07 | Notifications : le destinataire était le `sub` Keycloak alors que les événements visent un `clientId` ; les clients ne recevaient jamais leurs notifications. | Moyenne | Convention : `recipientId` = `clientId` (claim du JWT) pour le rôle CLIENT, `sub` pour le personnel ; JWT sans `clientId` ou mêlant CLIENT et rôle du personnel → `403` | tests `NotificationControllerTenantIsolationTest` |

## 2. Points ouverts (non corrigés volontairement, à traiter plus tard)

| ID | Point | Gravité | À traiter avec |
|---|---|---|---|
| SEC-08 | Keycloak en mode `dev-file` sans volume : utilisateurs et réglages perdus à chaque recréation du conteneur. | Moyenne | Étape 8 — industrialisation |
| SEC-09 | Email des clients unique **globalement** (`unique` + `existsByEmail`) : un cabinet peut deviner qu'un email existe chez un autre (erreur `409`). | Moyenne | Migration Flyway (unicité par cabinet) |
| SEC-10 | Tribunaux et juges sans `lawFirmId` : partagés et modifiables entre cabinets par tout avocat ou administrateur. | Élevée | Migration Flyway (données propres à chaque cabinet) |
| SEC-11 | Documents : `storageKey` fournie par l'appelant, aucun upload réel. Risque latent : une clé forgée pourrait viser le fichier d'un autre cabinet. | Moyenne | Étape 7 — documents réels (clé générée par le serveur) |
| SEC-12 | Les ports de tous les services sont publiés sur la machine hôte : la gateway peut être contournée. | Moyenne | Étape 8 — durcissement |
| SEC-13 | Secrets de développement par défaut : `admin/admin` (Keycloak), `replace-with-rabbitmq-password`, `dev-only-user-service-secret`, mots de passe de test égaux à l'identifiant. | Élevée (en production) | Étape 8 — secrets |
| SEC-14 | `ddl-auto: update` : le schéma de base évolue automatiquement, sans migration versionnée. | Moyenne | Migration Flyway |
| SEC-15 | Rendez-vous : le `lawyerId` n'est pas vérifié (`TODO` dans le code). | Moyenne | Brancher l'annuaire du `user-service` dans `appointment-service` |
| SEC-16 | Le client Keycloak `legalflow-frontend` autorise le grant `password` (utilisé par les scripts de test) : les identifiants transitent par une application. | Moyenne | Étape 8 — désactiver hors développement |
| SEC-17 | Aucune limitation de débit à la gateway, aucune détection de force brute sur les connexions. | Moyenne | Étape 8 — limitation de débit, `bruteForceProtected` |
| SEC-18 | Les mots de passe temporaires sont renvoyés **une fois** dans la réponse de l'API : ils ne doivent jamais apparaître dans les journaux, et la connexion doit être chiffrée (HTTPS). | Faible | Étape 8 — HTTPS, journaux |
| SEC-19 | `emailVerified` est forcé à `true` à la création d'un compte (aucun email de vérification). | Faible | Avec l'envoi d'emails (étape 4) |

| SEC-21 | File d'attente des consultations : tous les avocats du cabinet voient les demandes `PENDING` (non assignées) pour pouvoir les prendre en charge ; le nom du client reste masqué tant qu'il ne leur est pas confié. | Faible | Risque accepté (conception de la file d'accueil) |
| SEC-22 | Les `lawyerIds` d'un client ne sont pas validés contre l'annuaire des avocats (l'administrateur peut saisir un UUID quelconque). | Faible | Valider via le `user-service` |
| SEC-23 | Données existantes : après la migration, les anciens clients n'ont aucun avocat en charge et ne sont visibles que de l'administration et du secrétariat. | Info | Les confier depuis la page Clients (administrateur ou secrétaire) |

## 3. Constats fonctionnels (sans risque de sécurité)

| ID | Constat | Étape |
|---|---|---|
| FUN-01 | Le statut choisi à la création d'un dossier est ignoré : le service force `OPEN`. | Écrans des modules |
| FUN-02 | Judiciaire : pour un client, la liste appelle le `case-service` une fois par affaire. | À la fin, si besoin |
| FUN-03 | Aucun service « cabinets » : le nom d'un cabinet n'est stocké nulle part (seul son `lawFirmId` existe). `create-law-firm.ps1` est un outil du propriétaire de la plateforme. | À discuter |
