# Relay | Multi-Tenant Helpdesk

A Java 17 / Spring Boot support desk for multiple organizations. Organizations share one application and database, while users, ticket queues, assignments, comments, and history remain scoped to the authenticated user's organization.

## Requirements

- JDK 17 or later
- Maven 3.8+
- Optional: PostgreSQL 14+ for a persistent shared database. Without database environment variables, the app uses a local H2 file database.

## Run locally

```powershell
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). The default development profile seeds two sample organizations and six demo accounts. Each account uses `Welcome1234!`:

| Organization   | Role               | Email                     |
| -------------- | ------------------ | ------------------------- |
| Acme Support   | Organization Admin | `admin@acme.test`         |
| Acme Support   | Support Agent      | `agent@acme.test`         |
| Acme Support   | Customer           | `customer@acme.test`      |
| Northstar Labs | Organization Admin | `admin@northstar.test`    |
| Northstar Labs | Support Agent      | `agent@northstar.test`    |
| Northstar Labs | Customer           | `customer@northstar.test` |

Demo data is disabled when the `prod` Spring profile is active. Change the sample passwords before using the application beyond local evaluation.

## PostgreSQL

Create an empty database, then configure the connection before starting the app:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/helpdesk"
$env:DATABASE_USERNAME = "helpdesk"
$env:DATABASE_PASSWORD = "change-this-password"
$env:SPRING_PROFILES_ACTIVE = "prod"
mvn spring-boot:run
```

The schema is created/updated by Hibernate for assessment convenience. Production deployments should replace `ddl-auto: update` with versioned migrations before release.

## Features

- Organization Admin, Support Agent, and Customer roles with BCrypt password storage.
- Organization-scoped user provisioning and ticket access. Customers can only see tickets they requested; staff can work their organization's queue.
- Ticket creation, status transitions, assignment, priority updates, and close/reopen via the status field.
- Search by subject or description; filter by status, priority, and assigned agent; paginated results (API page size capped at 100).
- Public replies and staff-only internal notes. Internal notes are rejected from customers and omitted from customer ticket responses.
- Status and assignment history, requester/assignee references, and created/updated timestamps.
- Responsive server-rendered web interface, REST API, validation/error responses, and Swagger UI.

## API

The REST endpoints accept HTTP Basic authentication using the same demo credentials. OpenAPI UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

| Method  | Path                         | Access             | Purpose                                          |
| ------- | ---------------------------- | ------------------ | ------------------------------------------------ |
| `GET`   | `/api/organizations/me`      | Any signed-in user | Current organization                             |
| `GET`   | `/api/users`                 | Admin, Agent       | List users in the current organization           |
| `POST`  | `/api/users`                 | Admin              | Create a user in the current organization        |
| `GET`   | `/api/tickets`               | Any signed-in user | Search, filter, and page through visible tickets |
| `POST`  | `/api/tickets`               | Any signed-in user | Create a ticket for the current organization     |
| `GET`   | `/api/tickets/{id}`          | Any signed-in user | Read a visible ticket and its activity           |
| `PATCH` | `/api/tickets/{id}`          | Admin, Agent       | Change status/priority or assign a staff member  |
| `POST`  | `/api/tickets/{id}/comments` | Any signed-in user | Add a public reply or staff-only internal note   |

Ticket list query parameters: `q`, `status`, `priority`, `assignedAgentId`, `page`, and `size`. Status values: `OPEN`, `IN_PROGRESS`, `WAITING_ON_CUSTOMER`, `RESOLVED`, `CLOSED`. Priority values: `LOW`, `NORMAL`, `HIGH`, `URGENT`.

Example:

```powershell
curl.exe -u customer@acme.test:Welcome1234! "http://localhost:8080/api/tickets?q=billing&status=OPEN&page=0&size=20"
```

`POST /api/tickets` body:

```json
{
  "subject": "Unable to access billing",
  "description": "The billing page returns an error after sign-in.",
  "priority": "HIGH"
}
```

`PATCH /api/tickets/{id}` accepts any combination of `status`, `priority`, and `assignedAgentId`. The assignee must be an administrator or support agent in the same organization. Invalid requests use standard HTTP statuses and a `ProblemDetail` response.

## Verify

```powershell
mvn clean verify
```

JaCoCo generates `target/site/jacoco/index.html` and fails the build unless instruction, line, and branch coverage each reach 100%. Tests cover ticket and user services, role authorization, tenant isolation, API validation, ticket filtering and updates, comment privacy, and browser form binding. The API integration suite uses an isolated H2 database; verify PostgreSQL-specific behavior before production deployment.

## Assessment delivery checklist

- Source is organized as a Maven project and can be started using the command above.
- This README documents environment setup, sample accounts, API routes, and local execution.
- Swagger UI supplies the OpenAPI documentation and supports authenticated API exploration.
- Unit tests cover important tenant and authorization rules.
- Submit this repository URL or a ZIP of the project, excluding generated `target/` and local `data/` files, by the assessment's stated 24-hour deadline.
