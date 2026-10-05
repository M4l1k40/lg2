# LegalFlow microservice domain model

This project follows a database-per-service architecture. The business diagram is global and should not be implemented as one monolithic JPA model.

## Service ownership

### Auth Service
- LawFirm
- UserProfile

### Client Service
- Client

### Case Service
- LegalCase
- CaseEvent

### Document Service
- Document

### Judicial Service
- JudicialCase
- Court
- Judge
- Hearing
- JudicialEvent

### Deadline Service
- Deadline

### Appointment Service
- Appointment

### Notification Service
- Notification

## Cross-service relationship rules

- All references between entities owned by different services are stored as UUID values.
- Example: `LegalCase.clientId`, `LegalCase.lawyerId`, `LegalCase.lawFirmId` are UUID fields, not JPA relationships.
- Do not create `@ManyToOne`, `@OneToMany`, `@ManyToMany`, or similar JPA associations across microservices.
- Cross-service communication should use REST calls or asynchronous messaging via RabbitMQ.
- The diagram represents business relationships, not database foreign keys.

## Example conventions

```java
@Column(name = "client_id", nullable = false)
private UUID clientId;

@Column(name = "law_firm_id", nullable = false)
private UUID lawFirmId;
```

This is intentionally not a JPA relationship mapping.
