# LegalFlow architecture overview

## Purpose

LegalFlow is a multi-tenant SaaS platform for law firms. Each law firm acts as an independent tenant and owns its own business data, users, clients, cases, documents, deadlines, appointments and notifications.

## Identity and security model

- Keycloak is the identity provider and source of truth for authentication.
- The frontend authenticates through Keycloak using OAuth2/OIDC.
- The frontend obtains an access token and sends it as a Bearer token to the Spring Cloud Gateway.
- The Gateway validates JWTs and enforces access policies before routing requests.
- Microservices are configured as OAuth2 resource servers where needed and enforce authorization server-side.
- Roles are managed in Keycloak and mapped to Spring authorities in the form `ROLE_ADMIN`, `ROLE_LAWYER`, `ROLE_SECRETARY`, and `ROLE_CLIENT`.
- No passwords are stored in PostgreSQL application databases.

## Multi-tenancy model

- Tenant = law firm.
- Every tenant-owned business entity stores a `lawFirmId` UUID.
- Keycloak declares the `lawFirmId` user attribute as administrator-editable only and issues it as an access-token claim through the `lawFirmId` client scope assigned to `legalflow-frontend`. Keep `clientScopes` absent from the realm import JSON so Keycloak 26.0.7 creates its built-in scopes (`basic`, `roles`, `profile`, `email`, `web-origins`, and `acr`) before importing the frontend default scope assignments. Keycloak 26.0.7 also does not accept declarative user-profile configuration inside `RealmRepresentation`; after realm import, apply `infrastructure/scripts/configure-keycloak-user-profile.ps1`, which creates/maps the `lawFirmId` client scope and PUTs `infrastructure/scripts/legalflow-user-profile.json` through the supported admin API, verifying admin-only permissions.
- Each resource server's local `TenantContext.requireLawFirmId()` reads and parses the claim from the authenticated JWT. Missing or malformed claims return `403 Forbidden`.
- List queries always use the token tenant. A supplied `lawFirmId` query parameter is ignored only when it matches the claim; a mismatch returns `403 Forbidden`.
- Create operations assign `lawFirmId` from the token. An absent body value is accepted; a different supplied value returns `403 Forbidden`.
- Every by-ID read/update/delete/PATCH and every child-resource operation checks ownership in the service layer. A different tenant receives `404 Not Found` to avoid user enumeration; child ownership is checked through its parent entity.
- Before creating a record with a cross-service owner reference, validate that reference synchronously against the owning service using the caller's original `Authorization: Bearer` token. Do not mint a service token or skip the check. Map an owning-service `404` to a domain `422`; map downstream timeout, connection errors, and `5xx` to `503` and fail the write closed. Use a short explicit timeout (currently 3 seconds).
- `Court` and `Judge` are global reference data and intentionally do not carry a `lawFirmId` in the model.
- This is mandatory for all current and future resource-server services. Duplicate the small `TenantContext` class in each service; do not create a shared Maven module because Docker build contexts are isolated.
- The gateway forwards the `Authorization` header unchanged; tenant authorization remains enforced in each resource service.
- Tenant-owned resources must be queried by the tenant UUID from the authenticated access-token claim, never by a caller-selected tenant value. Child resources inherit ownership from their parent case.
- `deadline-service` is not implemented yet. When it is added, its list/create/by-ID and child-resource paths must follow the same token-derived tenant checks and include equivalent JWT-based MockMvc isolation tests.
- Future cross-service references from deadline-service or another resource server must use the same caller-token ownership-check pattern, status mapping, timeout, and tests.

## Service architecture

### Gateway

- Single entry point for the frontend.
- Routes traffic to the correct business service.
- Uses the public-facing `/api/*` convention for all client-facing routes.
- Strips the external `/api` prefix before forwarding to each service, allowing each microservice to keep clean internal paths such as `/clients`, `/cases`, `/documents`, etc.
- Validates JWTs and enforces CORS.
- Provides centralized request logging and policy enforcement.

### Client service

- Owns client records and client metadata.
- Stores one database per service.
- Uses UUIDs and tenant checks for isolation.

### Case service

- Owns `LegalCase` and `CaseEvent` records.
- Automatically creates an initial `OPEN` case status and timeline event on creation.
- Publishes case-related domain events to RabbitMQ when appropriate.

### Document service

- Maintains document metadata in its dedicated PostgreSQL database; `lawFirmId` and `caseId` remain UUID references, not cross-service JPA relations.
- Scopes all reads, updates, deletes and case lists to the authenticated tenant. Before create/update it verifies the case with case-service using the caller's bearer token.
- Persists a `storageKey`; the current REST API does not stream or upload binary content. A configured object-storage adapter is a separate integration concern.

### Judicial service

- Handles courts, judges, judicial cases, hearings and judicial events.
- Validates status transitions and event creation rules.

### Deadline service

- Tracks legal deadlines and statuses.
- Supports configurable reminder windows such as J-7 / J-3 / J-1.
- Publishes deadline notifications when approaching or overdue.

### Appointment service

- Tracks appointments and meeting/calendar data.
- Validates tenant, client and lawyer ownership.

### Notification service

- Stores and exposes user notifications through REST endpoints.
- Derives the tenant from the JWT `lawFirmId` claim and the recipient from the UUID `sub` claim; all reads and mutations are scoped by both values.
- Uses its own PostgreSQL database (`notification_db`) and listens on port 8087.
- RabbitMQ will be integrated in a later step.

## Data architecture

- PostgreSQL is used for each service's own database.
- Database-per-service is mandatory.
- Cross-service references are implemented with UUIDs such as `lawFirmId`, `caseId`, `clientId`, `lawyerId`, `courtId`, `judgeId`, `userId`.
- No cross-service foreign keys and no JPA relationships across service boundaries.

## Event-driven integration

REST is used for synchronous interactions. RabbitMQ is not connected to the Notification Service in this phase; the following event integrations are planned for a later phase:

**Open gap:** `deadline-service` currently uses `LoggingNotificationPublisher`, which only logs `OVERDUE` and `APPROACHING` events. It does not call the Notification Service REST API. No deadline, case, judicial, or appointment event currently creates a row in the notifications table; rows are created only by direct Notification Service REST requests. This remains intentionally unwired until phase 10 (RabbitMQ integration).

- CASE_CREATED
- CASE_STATUS_CHANGED
- DOCUMENT_UPLOADED
- DEADLINE_APPROACHING
- DEADLINE_OVERDUE
- APPOINTMENT_CREATED
- APPOINTMENT_CANCELLED
- HEARING_SCHEDULED
- HEARING_COMPLETED
- JUDICIAL_STATUS_CHANGED

## Security and error handling

- Standard API errors should map to HTTP 400, 401, 403, 404, 409, 422 and 500.
- Production-facing APIs must never expose stack traces or internal implementation details.
- Business logic must protect tenant boundaries even when a client submits unexpected IDs.

## Delivery roadmap

This repository begins with the project foundation and architecture artifacts. The next phases are:

1. Keycloak realm and roles
2. Gateway and JWT validation
3. Client service
4. Case service and timeline
5. Judicial service
6. Deadline service
7. Appointment service
8. Document service + MinIO
9. Notification service REST API
10. RabbitMQ integration for notifications
11. React frontend + Keycloak login
12. Docker Compose and CI/CD
13. Kubernetes deployment

## Important design decision

The global class model is a business-level model, not a single monolithic JPA model. The entities are distributed across independent microservices according to ownership and domain boundaries.
