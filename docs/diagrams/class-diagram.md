# LegalFlow global business class diagram

This document models the global business domain of LegalFlow, distributed across multiple microservices. It is intentionally not a monolithic JPA model.

```mermaid
classDiagram
    class LawFirm {
        UUID id
        String name
        String address
        String phone
        String email
        LocalDateTime createdAt
    }

    class UserProfile {
        UUID id
        UUID lawFirmId
        String firstName
        String lastName
        String email
        String phone
        Role role
        Boolean active
        LocalDateTime createdAt
    }

    class Client {
        UUID id
        UUID lawFirmId
        String firstName
        String lastName
        String email
        String phone
        String address
        LocalDateTime createdAt
        LocalDateTime updatedAt
    }

    class LegalCase {
        UUID id
        UUID lawFirmId
        UUID clientId
        UUID lawyerId
        String reference
        String title
        String description
        CaseStatus status
        LocalDateTime createdAt
        LocalDateTime updatedAt
    }

    class CaseEvent {
        UUID id
        UUID caseId
        String type
        String description
        LocalDateTime createdAt
        UUID createdBy
    }

    class Document {
        UUID id
        UUID lawFirmId
        UUID caseId
        String fileName
        String fileType
        String storagePath
        DocumentVisibility visibility
        UUID uploadedBy
        LocalDateTime uploadedAt
    }

    class JudicialCase {
        UUID id
        UUID lawFirmId
        UUID caseId
        UUID courtId
        UUID judgeId
        String courtCaseNumber
        ProcedureType procedureType
        JudicialStatus status
        LocalDate openingDate
    }

    class Court {
        UUID id
        String name
        String type
        String city
        String address
    }

    class Judge {
        UUID id
        UUID courtId
        String firstName
        String lastName
        String role
    }

    class Hearing {
        UUID id
        UUID judicialCaseId
        LocalDateTime dateTime
        HearingType type
        HearingStatus status
        String location
        String notes
        String nextAction
    }

    class JudicialEvent {
        UUID id
        UUID judicialCaseId
        String type
        String description
        LocalDateTime eventDate
        UUID createdBy
    }

    class Deadline {
        UUID id
        UUID lawFirmId
        UUID caseId
        String title
        String description
        LocalDate dueDate
        Priority priority
        DeadlineStatus status
        LocalDateTime createdAt
    }

    class Appointment {
        UUID id
        UUID lawFirmId
        UUID caseId
        UUID clientId
        UUID lawyerId
        LocalDateTime dateTime
        AppointmentType type
        AppointmentStatus status
        String location
        String notes
    }

    class Notification {
        UUID id
        UUID userId
        String title
        String message
        NotificationType type
        Boolean read
        LocalDateTime createdAt
    }

    class Role {
        <<enumeration>>
        ADMIN
        LAWYER
        SECRETARY
        CLIENT
    }

    class CaseStatus {
        <<enumeration>>
        OPEN
        IN_PROGRESS
        WAITING_CLIENT
        COURT_PROCEEDING
        CLOSED
        ARCHIVED
    }

    class DocumentVisibility {
        <<enumeration>>
        PRIVATE
        CLIENT_VISIBLE
    }

    class ProcedureType {
        <<enumeration>>
        CIVIL
        COMMERCIAL
        CRIMINAL
        ADMINISTRATIVE
    }

    class JudicialStatus {
        <<enumeration>>
        REGISTERED
        IN_PROGRESS
        HEARING_SCHEDULED
        DELIBERATION
        JUDGMENT_ISSUED
        CLOSED
    }

    class HearingType {
        <<enumeration>>
        FIRST_HEARING
        FOLLOW_UP
        PLEADING
        DELIBERATION
    }

    class HearingStatus {
        <<enumeration>>
        SCHEDULED
        COMPLETED
        POSTPONED
        CANCELLED
    }

    class Priority {
        <<enumeration>>
        LOW
        MEDIUM
        HIGH
        CRITICAL
    }

    class DeadlineStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        OVERDUE
    }

    class AppointmentType {
        <<enumeration>>
        CLIENT_MEETING
        COURT_HEARING
        CONSULTATION
        INTERNAL_MEETING
    }

    class AppointmentStatus {
        <<enumeration>>
        SCHEDULED
        COMPLETED
        CANCELLED
    }

    class NotificationType {
        <<enumeration>>
        DEADLINE
        HEARING
        DOCUMENT
        CASE
        SYSTEM
    }

    LawFirm "1" --> "*" UserProfile : employs
    LawFirm "1" --> "*" Client : manages
    LawFirm "1" --> "*" LegalCase : owns
    Client "1" --> "*" LegalCase : has
    UserProfile "1" --> "*" LegalCase : manages
    LegalCase "1" --> "*" CaseEvent : timeline
    LegalCase "1" --> "*" Document : contains
    LegalCase "1" --> "*" Deadline : has
    LegalCase "1" --> "*" Appointment : related to
    LegalCase "1" --> "0..*" JudicialCase : may have
    JudicialCase "*" --> "1" Court : assigned to
    JudicialCase "*" --> "1" Judge : handled by
    JudicialCase "1" --> "*" Hearing : contains
    JudicialCase "1" --> "*" JudicialEvent : timeline
    Court "1" --> "*" Judge : has
    UserProfile "1" --> "*" Notification : receives
    Appointment "*" --> "1" Client : concerns
    Appointment "*" --> "1" UserProfile : assigned lawyer
```

## Ownership by service

- Keycloak / Identity: user authentication, identity, roles, tokens
- Client Service: Client
- Case Service: LegalCase, CaseEvent
- Document Service: Document
- Judicial Service: JudicialCase, Court, Judge, Hearing, JudicialEvent
- Deadline Service: Deadline
- Appointment Service: Appointment
- Notification Service: Notification
- LawFirm / UserProfile: business profile and tenant context, stored in the service that owns the tenant business data and linked to the Keycloak principal by subject ID

## Important note

This model describes the legal domain, not a single JPA entity graph. The classes are purposely distributed across microservices using UUID references instead of cross-service JPA relationships.
