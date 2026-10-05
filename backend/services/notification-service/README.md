# Notification Service

## Responsibility

The service keeps LegalFlow's authenticated user's notification inbox and immediately attempts external delivery when a notification is created. It does not publish or consume RabbitMQ messages yet.

The REST API creates notifications for the authenticated user only. `recipientId` always comes from JWT `sub`, and `lawFirmId` always comes from the JWT claim. Body-supplied `recipientId`, `recipientType`, `channel`, and `lawFirmId` are ignored. The service derives the actual recipient type from the Keycloak `realm_access.roles` claim.

The existing `ADMIN` role is not a delivery recipient type. Admin inbox creation remains supported for backward compatibility, with external delivery marked `FAILED` and no invented channel.

## Routing and Providers

```text
NotificationService
        |
        v
NotificationRoutingService
        |--------------------------|
        v                          v
WhatsAppNotificationProvider  EmailNotificationProvider
        |                          |
      CLIENT                LAWYER / SECRETARY
```

| Recipient role | Channel | Contact source |
|---|---|---|
| `CLIENT` | `WHATSAPP` | JWT phone claim (`phone_number`, `phoneNumber`, or `phone`); otherwise tenant-scoped `client-service` lookup by JWT subject |
| `LAWYER` | `EMAIL` | JWT `email` claim |
| `SECRETARY` | `EMAIL` | JWT `email` claim |

The server always derives the channel; the request cannot select one. The `CLIENT` contact lookup forwards the current bearer token to `GET /clients/{sub}`, so the existing client service enforces tenant ownership. A client subject used for that lookup must match its client UUID.

Every new notification is first persisted as `PENDING`, then routed. Success updates it to `SENT` and sets `sentAt`; delivery or contact-resolution failure updates it to `FAILED` and stores a safe `errorMessage`. Existing inbox fields and read/delete operations remain available. Historical rows created before delivery metadata existed can have null delivery fields.

At startup, `schema.sql` updates the existing notification type check constraint after Hibernate creates/updates the table. The script preserves existing rows and admits the event names reserved for future consumers.

## REST API

Direct service base: `http://localhost:8087/notifications`  
Gateway base: `http://localhost:9000/api/notifications`

All routes except Actuator health/info require a JWT.

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/` | Persist and send a notification for the authenticated user |
| `GET` | `/` | List that user's notifications, newest first |
| `GET` | `/unread` | List unread notifications |
| `GET` | `/{id}` | Read a notification owned by the current tenant and user |
| `GET` | `/{id}/status` | Read delivery state for an owned notification |
| `PATCH` | `/{id}/read` | Mark one owned notification read |
| `PATCH` | `/read-all` | Mark all of that user's unread notifications read |
| `DELETE` | `/{id}` | Delete one owned notification |

Create body:

```json
{
  "type": "DEADLINE_APPROACHING",
  "title": "Deadline approaching",
  "subject": "Optional email subject",
  "message": "A legal deadline is approaching.",
  "referenceType": "DEADLINE",
  "referenceId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
}
```

`subject` defaults to `title`. Delivery errors are recorded in the returned notification; the record remains available with HTTP 201 so callers can inspect `/{id}/status`.

## Email Configuration

Configure SMTP through environment variables. For Gmail, use `smtp.gmail.com`, port `587`, the account address, and a Gmail App Password; never use the account password. Credentials are not stored in source control. Local Compose defaults to `localhost:1025`; without an SMTP server, email delivery records `FAILED` while the application continues running.

## WhatsApp Configuration

`WHATSAPP_MODE=MOCK` is the local default. It makes no network call, logs the message that would have been sent, and reports simulated success. Avoid putting sensitive production content in mock logs.

For a real Meta WhatsApp Cloud API integration, set `WHATSAPP_MODE=REAL`, `WHATSAPP_PROVIDER=META`, the API base URL, access token, and phone-number ID. The provider calls `{WHATSAPP_API_URL}/{WHATSAPP_PHONE_NUMBER_ID}/messages`. Missing configuration or a rejected/failed request is stored as `FAILED`; tokens are never logged.

## Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `MAIL_HOST` | `localhost` | SMTP host |
| `MAIL_PORT` | `1025` | SMTP port |
| `MAIL_USERNAME` | empty | SMTP account |
| `MAIL_PASSWORD` | empty | SMTP password or Gmail App Password |
| `MAIL_FROM` | `no-reply@legalflow.local` | Sender address |
| `WHATSAPP_MODE` | `MOCK` | `MOCK` or `REAL` |
| `WHATSAPP_PROVIDER` | `META` | Provider implementation in real mode |
| `WHATSAPP_API_URL` | `https://graph.facebook.com/v22.0` | Meta Graph API base URL |
| `WHATSAPP_ACCESS_TOKEN` | empty | WhatsApp API bearer token |
| `WHATSAPP_PHONE_NUMBER_ID` | empty | WhatsApp Business phone-number ID |
| `CLIENT_SERVICE_URL` | `http://client-service:8081` | Tenant-scoped client contact lookup |

`.env.example` contains fictitious values only. Do not commit a real `.env` file or credentials.

## Security and Multi-Tenancy

The service uses existing Keycloak realm roles, JWT `sub`, and `lawFirmId`; it creates no user directory or authentication system. Reads and mutations query by both `lawFirmId` and `recipientId`, and a record outside that scope returns 404. Recipient type and delivery channel are derived server-side. The client contact lookup forwards the same JWT to the existing service, which independently checks the tenant.

## RabbitMQ Preparation

RabbitMQ is deliberately not added or configured in this change. `NotificationRoutingService` and the channel providers are independent of the REST controller, so a future consumer can translate events such as `CASE_CREATED`, `DEADLINE_APPROACHING`, `INVOICE_OVERDUE`, or `CONSULTATION_ANSWERED` into notification records and reuse the same routing/provider layer. That future work must define trusted event identity, idempotency, retries, and dead-letter handling; no broker listener exists yet.

## Tests

Run from this directory:

```powershell
mvn test
```