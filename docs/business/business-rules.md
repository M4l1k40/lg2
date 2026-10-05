# LegalFlow business rules

## 1. Identity and tenant context

- Keycloak is the identity provider.
- Application databases never store user passwords.
- The JWT subject and claim-based identity are used to derive current user context.
- The tenant context is taken from the authenticated identity and validated server-side.

## 2. Multi-tenancy

- A law firm is the tenant boundary.
- Each tenant-owned business entity includes `lawFirmId`.
- A user can only access records belonging to their own law firm.
- Cross-tenant access attempts must be rejected with `403 Forbidden` for mismatched input and `404` for different-tenant records.
- The tenant must always be read from the authenticated JWT claim `lawFirmId`; request parameters and request-body fields are treated as untrusted input and ignored when they conflict.
- `Court` and `Judge` are global reference data; they remain without a `lawFirmId` field by design.

## 3. Client management

- Clients belong to a single law firm.
- Client ownership must be verified before reads, updates or deletions.

## 4. Case management

- Cases belong to one law firm and one client.
- The case status starts in `OPEN`.
- Every new case creates a timeline event.
- Case updates may trigger `CASE_STATUS_CHANGED` or `CASE_CREATED` events.

## 5. Document management

- Document metadata is stored in PostgreSQL.
- Binary files are stored in MinIO.
- Document visibility is `PRIVATE` or `CLIENT_VISIBLE`.
- Access must respect both tenant ownership and visibility rules.

## 6. Judicial procedures

- Judicial cases, courts, judges and hearings are validated by business status rules.
- Invalid transitions are rejected.
- Hearing and judicial changes create corresponding timeline events.

## 7. Deadlines and appointments

- Deadline status transitions are validated.
- Reminder windows are configurable and may be used to trigger notifications.
- Appointments validate tenant, client and assigned lawyer ownership.

## 8. Notifications

- Notifications are persisted per user.
- Notification records are created from asynchronous events emitted by business services.

## 9. Error handling

- `400` for invalid requests
- `401` for missing or invalid authentication
- `403` for forbidden cross-tenant or role-invalid access
- `404` for missing resource
- `409` for business conflicts
- `422` for validation/business rule rejection
- `500` for unexpected failures

## 10. Quality bar

Each service must include tests for:

- business logic
- controller behavior
- validation rules
- tenant isolation
- role restrictions
