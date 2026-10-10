# 3. Architecture

VRMS is a **layered monolith with event-driven messaging**: one Spring Boot application, organised in
strict layers, that publishes business events to RabbitMQ for asynchronous work (email, SMS, staff
alerts). The React single-page app is built into the same deployable jar.

## 3.1 System context and containers

```mermaid
flowchart LR
    subgraph Users
        C[Customer<br/>browser / phone]
        S[Agent / Admin<br/>browser]
    end
    subgraph VRMS["VRMS application (Spring Boot 3, Java 17)"]
        SPA[React 19 SPA<br/>served from /]
        API[REST API /api/**<br/>OAuth2 resource server]
        L[RabbitMQ consumers<br/>email · SMS · staff alerts]
    end
    PG[(PostgreSQL<br/>transactional core)]
    MG[(MongoDB<br/>audit · notifications · GridFS)]
    MQ{{RabbitMQ<br/>vrms.events}}
    SMTP[SMTP server<br/>Mailpit in dev]
    SMS[Africa's Talking<br/>SMS API]
    IDP[Google / GitHub<br/>OAuth2 providers]

    C --> SPA
    S --> SPA
    SPA -- "JSON + Bearer token" --> API
    API --> PG
    API --> MG
    API -- "events (after commit)" --> MQ
    MQ --> L
    L --> SMTP
    L --> SMS
    L --> MG
    API <-. "authorization code flow" .-> IDP
```

## 3.2 Backend layers

```mermaid
flowchart TB
    subgraph Presentation["Presentation (controller/)"]
        CT["@RestController + @PreAuthorize<br/>VehicleController, RentalContractController, …"]
        EH[GlobalExceptionHandler<br/>uniform error JSON]
    end
    subgraph Security["Security (security/, config/SecurityConfig)"]
        RS[OAuth2 resource server<br/>JWT validation]
        CV[UserJwtAuthenticationConverter<br/>loads user + role permissions]
        OL[OAuth2LoginConfig<br/>Google / GitHub]
    end
    subgraph Business["Business (service/)"]
        SV["ContractService · VehicleService · CustomerService<br/>BranchService · AuthService · StaffService<br/>DocumentService · DashboardService · AuditService"]
        EV[RentalEventPublisher]
    end
    subgraph Messaging["Messaging (messaging/, notification/)"]
        RELAY["RabbitEventRelay<br/>@TransactionalEventListener AFTER_COMMIT"]
        NL["NotificationListeners<br/>@RabbitListener"]
    end
    subgraph Data["Data access (repository/, model/)"]
        JPA[Spring Data JPA repositories]
        MR[Spring Data MongoDB repositories<br/>+ GridFsTemplate]
    end
    CT --> SV
    RS --> CV
    SV --> JPA
    SV --> MR
    SV --> EV --> RELAY --> NL
    NL --> MR
```

| Package | Responsibility |
|---|---|
| `controller` | HTTP endpoints, request validation (`@Valid`), permission checks (`@PreAuthorize`). No business logic. |
| `dto` | Request/response records (`BookingRequest`, `UserView`, …) so entities aren't bound directly where it matters. |
| `service` | Business rules and transactions (contract state machine, duplicate checks, vehicle status). |
| `repository` / `model` | JPA entities + repositories (PostgreSQL) and Mongo documents + repositories. |
| `security` | Token issuing/validation, user loading, login throttling, OAuth2 account mapping. |
| `messaging` / `notification` | Domain events, RabbitMQ relay, consumers, templates, SMS gateways. |
| `config` | Security chains, RabbitMQ topology, caches, OpenAPI, SPA serving, seed data. |

### Request flow: customer books a vehicle

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant SPA as React SPA
    participant API as MyBookingsController
    participant Sec as Resource server + RBAC
    participant CS as ContractService
    participant PG as PostgreSQL
    participant Relay as RabbitEventRelay
    participant MQ as RabbitMQ
    participant NL as NotificationListeners

    Customer->>SPA: Reserve & book (dates, branch)
    SPA->>API: POST /api/me/bookings (Bearer token)
    API->>Sec: validate JWT, load user, check BOOKING_OWN
    API->>CS: book(customer, request)
    CS->>PG: SELECT … FOR UPDATE (lock vehicle row)
    CS->>PG: INSERT contract (PENDING), vehicle → RESERVED
    CS-->>Relay: BOOKING_REQUESTED (in-transaction event)
    API-->>SPA: 201 Created
    Note over Relay: only after the DB commit succeeds
    Relay->>MQ: publish booking.requested
    MQ->>NL: email queue, SMS queue, staff-alert queue
    NL->>NL: send email (SMTP), SMS (Africa's Talking), staff alert
    NL->>NL: record each in MongoDB notifications
```

## 3.3 Security

### Authentication: OAuth2

The REST API is an **OAuth2 resource server** (`spring-boot-starter-oauth2-resource-server`). Every
request carries `Authorization: Bearer <access token>`; tokens are JWTs signed with HS256 and checked
for **signature, expiry, issuer (`vrms`) and audience (`vrms-api`)**. The token's subject is re-loaded
from the database on each request, so disabling an account or changing a role takes effect immediately.

Tokens are obtained in two ways:

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant SPA as React SPA
    participant VRMS as VRMS
    participant G as Google / GitHub

    rect rgba(193,98,45,0.08)
    note over U,VRMS: Email + password
    U->>SPA: email, password
    SPA->>VRMS: POST /api/auth/login
    VRMS->>VRMS: throttle check · BCrypt verify · enabled?
    VRMS-->>SPA: { token, tokenType: Bearer, expiresInSeconds, user }
    end

    rect rgba(110,139,90,0.10)
    note over U,G: OAuth2 authorization-code flow
    U->>SPA: Continue with Google
    SPA->>VRMS: GET /oauth2/authorization/google
    VRMS-->>U: 302 to Google (client_id, scope, state)
    U->>G: consent
    G-->>VRMS: GET /login/oauth2/code/google?code&state
    VRMS->>G: exchange code for tokens, read verified email
    VRMS->>VRMS: find or create CUSTOMER account
    VRMS-->>SPA: 302 /oauth/callback#token=… (fragment never sent to servers)
    SPA->>VRMS: GET /api/auth/me (Bearer)
    end
```

Other protections: BCrypt password hashing; password policy (8+ chars, letters and digits);
**lockout after 5 failed sign-ins** per email and client for 15 minutes; Content-Security-Policy,
referrer policy and `X-Content-Type-Options` headers; uploads checked by magic bytes; secrets kept out
of Git (`application-secrets.properties`, `.env`); CORS restricted to known origins.

### Authorization: RBAC

Each role is a fixed bundle of permissions (`Role.java`). URL rules give a first coarse filter
(`/api/me/**` customers only, staff areas staff only), then **every protected endpoint declares the
permission it needs** with `@PreAuthorize("hasAuthority('…')")`. The UI hides what a user can't do,
but the API enforces it regardless.

| Permission | Admin | Agent | Customer | Used by |
|---|:-:|:-:|:-:|---|
| VEHICLE_WRITE | ✓ | ✓ | | add/edit vehicles, maintenance |
| VEHICLE_DELETE | ✓ | | | delete vehicles |
| CUSTOMER_READ | ✓ | ✓ | | customer directory |
| CUSTOMER_WRITE | ✓ | ✓ | | register/edit customers, review documents |
| CUSTOMER_DELETE | ✓ | | | delete customers |
| CONTRACT_READ | ✓ | ✓ | | contracts list |
| CONTRACT_WRITE | ✓ | ✓ | | issue, approve, return, cancel |
| CONTRACT_DELETE | ✓ | | | delete contract records |
| BRANCH_MANAGE | ✓ | | | create/edit/delete branches |
| DASHBOARD_READ | ✓ | ✓ | | dashboard KPIs |
| AUDIT_READ | ✓ | | | system logs |
| NOTIFICATION_READ | ✓ | ✓ | | sent emails/SMS |
| DOCUMENT_READ | ✓ | ✓ | | view customer documents |
| STAFF_MANAGE | ✓ | | | staff accounts and roles |
| BOOKING_OWN | | | ✓ | own bookings, profile, documents, messages |

Safeguards: admins can't disable or demote themselves, and the last active admin can't be removed.
Customers only ever see their own bookings, documents and messages; another customer's document id
returns 404.

## 3.4 Messaging (RabbitMQ)

- Services raise a `RentalEvent` inside their transaction; `RabbitEventRelay` publishes it to the
  **`vrms.events` topic exchange only after commit**, so rolled-back actions never notify anyone.
- Three durable queues consume it: **email** (customer emails over SMTP), **SMS** (Africa's Talking,
  simulated without an API key) and **staff alerts** (new bookings).
- Failures are retried 3 times with back-off (1 s, 2 s), then **dead-lettered** to `vrms.dead-letter`.
- Consumers are **idempotent**: a redelivered event doesn't send the same email/SMS twice.
- Every delivery is recorded in MongoDB and shown on the staff *Notifications* page and the customer's
  *Messages* list. In development, every email can be read at http://localhost:8025 (Mailpit).

## 3.5 Performance

| Technique | Where |
|---|---|
| Caffeine caches: public fleet (evicted on vehicle/contract writes), branches, dashboard (15 s) | `CacheConfig`, `@Cacheable`/`@CacheEvict` |
| Entity graphs: contracts and vehicles load their associations in one query (no N+1) | `RentalContractRepository`, `VehicleRepository` |
| Indexes incl. partial indexes for open bookings; MongoDB indexes for logs and notifications | `V1__initial_schema.sql`, `@Indexed` |
| Gzip responses (≈70% smaller JSON) | `server.compression.*` |
| Fingerprinted assets cached 1 year (`immutable`), `index.html` revalidated | `SpaConfig` |
| Code-split staff console (lazy routes), public bundle 100 KB gzipped | `App.tsx` |
| Slow I/O (SMTP, SMS API) off the request path via RabbitMQ | §3.4 |
| JDBC batching, tuned Hikari pool, `open-in-view=false` | `application.properties` |

Measured on a laptop with the Docker stack (100 requests each, gzip): `GET /api/vehicles` p95 **18 ms**,
`/api/contracts` p95 **80 ms**, `/api/dashboard` p95 **95 ms**, `/api/logs` (MongoDB) p95 **75 ms**.

## 3.6 Deployment & DevOps

```mermaid
flowchart LR
    Dev[git push] --> GA
    subgraph GA["GitHub Actions (ci.yml)"]
        F[Frontend<br/>tsc · Vitest · vite build] --> B[Backend<br/>mvn verify<br/>Testcontainers: PG, Mongo, RabbitMQ, Mailpit]
        B --> D[Docker image<br/>multi-stage build]
    end
    D -- "main branch" --> R[(GHCR<br/>ghcr.io/…/vehicle-rental-management-system)]
    R --> H["Server: docker compose up -d<br/>app + postgres + mongo + rabbitmq + mailpit"]
```

- **Dockerfile**: Node builds the SPA → Maven builds the jar with it → `eclipse-temurin:17-jre-alpine`, non-root user, health check on `/actuator/health`.
- **docker-compose.yml**: the full stack with health-checked dependencies; settings from `.env`.
- **Schema**: Flyway migrations run on start-up; Hibernate validates the schema.
- **Observability**: `/actuator/health` reports PostgreSQL, MongoDB, RabbitMQ and SMTP; RabbitMQ management UI on :15672; API docs at `/swagger-ui.html`.
