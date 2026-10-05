# Notification Service

The service preserves the authenticated user's tenant-scoped inbox and adds server-side role routing with independent SMTP and WhatsApp providers. `CLIENT` maps to WhatsApp; `LAWYER` and `SECRETARY` map to email. The recipient is still the JWT subject, and `lawFirmId` is taken only from the JWT.

The local WhatsApp provider defaults to `MOCK`; SMTP and real WhatsApp credentials are supplied only through environment variables. RabbitMQ is not integrated. Provider setup, API contract, contact resolution, environment variables, and the future event-consumer boundary are documented in [the notification-service README](../../backend/services/notification-service/README.md).

Runtime remains port `8087` with the existing notification PostgreSQL database. Gateway base URL: `http://localhost:9000/api/notifications`.
