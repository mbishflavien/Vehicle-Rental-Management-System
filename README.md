# VRMS: Vehicle Rental Management System

[![CI/CD](https://github.com/mbishflavien/Vehicle-Rental-Management-System/actions/workflows/ci.yml/badge.svg)](https://github.com/mbishflavien/Vehicle-Rental-Management-System/actions/workflows/ci.yml)

A full-stack web platform for a Rwandan vehicle rental agency. Customers browse the fleet and book
online; rental agents and managers run vehicles, customers and contracts from a staff console; every
booking and status change emails and texts the customer through RabbitMQ.

**MBISHIBISHI Flavien · 27857 · Web Technology (2026–2027)**

![Staff dashboard](docs/screenshots/desktop-admin-dashboard.png)

## How this project meets the brief

| # | Instruction | Where |
|---|---|---|
| 1 | Problem statement, users, objectives, scope; functional requirements; measurable quality attributes; user stories with acceptance criteria | [docs/01-requirements.md](docs/01-requirements.md) |
| 2 | ER diagram + UML class diagram; domain concepts; technology schema with PKs, FKs, constraints, indexes | [docs/02-data-model.md](docs/02-data-model.md), [Flyway migration](src/main/resources/db/migration/V1__initial_schema.sql) |
| 3 | Responsive pages in a modern framework; mock-ups across devices | React 19 + TypeScript ([`frontend/`](frontend)), [Figma design](https://www.figma.com/make/9onglo1jX7XiV8dwmBgoU3/Vehicle-Rental-Management-System), [desktop/tablet/phone screens](docs/04-ui-design.md) |
| 4 | Backend with an architecture style, talking to the frontend | Spring Boot 3 **layered** architecture + **event-driven** messaging, REST/JSON API ([docs/03-architecture.md](docs/03-architecture.md), [Swagger](#urls)) |
| 5 | Relational **and** non-relational databases | **PostgreSQL** (users, customers, branches, vehicles, contracts) + **MongoDB** (audit trail, notifications, documents in **GridFS**) |
| 6 | Secure authentication incl. **OAuth2**; performance | **OAuth2 resource server** (signed JWT bearer tokens) + **OAuth2 login with Google/GitHub**; BCrypt, sign-in throttling, CSP. Caching, no N+1 queries, indexes, gzip, code splitting ([§3.3–3.5](docs/03-architecture.md#33-security)) |
| 7 | **RabbitMQ** for email, SMS and other events | Topic exchange `vrms.events` → email, SMS (Africa's Talking) and staff-alert queues, retries + dead-letter queue ([§3.4](docs/03-architecture.md#34-messaging-rabbitmq)) |
| 8 | **RBAC** on protected resources | Admin / Agent / Customer → 15 permissions, `@PreAuthorize` on every endpoint, staff management UI ([matrix](docs/03-architecture.md#authorization-rbac)) |
| 9 | Git with meaningful commits; PR to main | Feature-by-feature history on `feat/fullstack`, [pull request #1](https://github.com/mbishflavien/Vehicle-Rental-Management-System/pull/1) |
| Bonus 1 | DevOps for deployment | Multi-stage **Dockerfile**, **docker compose** stack, **GitHub Actions** CI/CD publishing the image to GHCR |
| Bonus 2 | Testing & QA | **32 backend** tests (unit + integration on real PostgreSQL/MongoDB/RabbitMQ/SMTP via **Testcontainers**) + **13 frontend** tests (Vitest), all in CI ([docs/05-testing.md](docs/05-testing.md)) |
| Bonus 3 | New technology | Testcontainers, Flyway, GridFS, Spring Security OAuth2, Africa's Talking SMS, Mailpit, OpenAPI/Swagger, Vitest, Playwright screenshots |

## Features

**Customers:** browse and filter the fleet by branch, type and price · reserve a vehicle (held as *Pending*) · sign up with email, Google or GitHub · upload driver license/ID scans · see bookings, cancel pending ones, read every email/SMS VRMS sent them.

**Rental agents:** dashboard · register walk-in customers · issue contracts with live cost · approve bookings, record returns, cancel · put vehicles in maintenance · verify customer documents.

**Admins:** everything agents do, plus deleting records, branches, the audit log and **staff accounts & roles**.

**Business rules:** Rwandan plate format (`RAB 123 A`, stored `RAB123A`), licenses start with `DL-`, no duplicate customers, no double bookings, rentals up to 90 days, vehicle status always follows the contract (Pending→Reserved, Active→Rented, Completed/Cancelled→Available), deletes refused while a rental is open.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite, React Router, CSS variables (Figma design tokens) |
| Backend | Java 17, Spring Boot 3.3 (Web, Security, OAuth2 Resource Server + Client, Data JPA, Data MongoDB, AMQP, Mail, Cache, Actuator), springdoc OpenAPI |
| Data | PostgreSQL 17 (Flyway migrations), MongoDB 7 (+ GridFS), Caffeine cache |
| Messaging | RabbitMQ 3.13, SMTP (Mailpit in development), Africa's Talking SMS |
| DevOps & QA | Docker, docker compose, GitHub Actions, GHCR, JUnit 5, Testcontainers, Awaitility, Vitest, Testing Library |

## Run it

### Option A: everything in Docker (recommended)

```bash
cp .env.example .env          # then set DB_PASSWORD, JWT_SECRET (openssl rand -base64 48), ADMIN_PASSWORD, AGENT_PASSWORD
docker compose up -d --build
```

### Option B: develop in IntelliJ + Vite

1. `docker compose up -d postgres mongo rabbitmq mailpit` (or use local installs).
2. Copy `src/main/resources/application-secrets.properties.example` → `application-secrets.properties` and fill it in
   (set `DB_URL=jdbc:postgresql://localhost:5433/vrms_db` if you use the compose PostgreSQL).
3. In IntelliJ set the project SDK to **Java 17** and run `VehicleRentalApplication`.
4. `cd frontend && npm install && npm run dev` → http://localhost:5173 (API calls are proxied to :8080).

### URLs

| What | URL |
|---|---|
| App | http://localhost:8080 |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Emails sent by VRMS (Mailpit) | http://localhost:8025 |
| RabbitMQ management (guest/guest) | http://localhost:15672 |

### Accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@vrms.rw` | `ADMIN_PASSWORD` from `.env` / secrets file |
| Agent | `agent@vrms.rw` | `AGENT_PASSWORD` |
| Customer | register on the site, or use Google/GitHub | |

Demo branches, vehicles, customers and contracts are loaded on first start.

### Optional integrations

- **Google / GitHub sign-in:** create OAuth credentials and set `GOOGLE_CLIENT_ID`/`SECRET` and/or `GITHUB_CLIENT_ID`/`SECRET`. Redirect URI: `http://localhost:8080/login/oauth2/code/{google|github}`.
- **Real SMS:** set `AT_USERNAME` and `AT_API_KEY` from [Africa's Talking](https://africastalking.com) (`sandbox` for testing). Without a key, SMS are simulated and logged.

## Tests

```bash
./mvnw test               # 32 backend tests; needs Docker running (Testcontainers)
cd frontend && npm test   # 13 frontend tests
```

## Documentation

1. [Requirements](docs/01-requirements.md): problem, users, objectives, scope, requirements, quality attributes, user stories
2. [Domain & data model](docs/02-data-model.md): ER and UML diagrams, domain concepts, PostgreSQL/MongoDB/RabbitMQ schema
3. [Architecture](docs/03-architecture.md): layers, flows, OAuth2, RBAC, messaging, performance, deployment
4. [UI design](docs/04-ui-design.md): responsive screens on desktop, tablet and phone
5. [Testing](docs/05-testing.md): strategy, inventory, results

Diagram images for reports are in [`docs/diagrams/`](docs/diagrams).
