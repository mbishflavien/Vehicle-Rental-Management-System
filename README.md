# Vehicle Rental Management System (VRMS)

Full-stack web app for a Rwandan vehicle rental agency: customers browse the fleet and book online,
and staff manage vehicles, customers, rental contracts and an audit log from a staff console.

- **Backend:** Spring Boot 3.3 (Java 17), Spring Data JPA, PostgreSQL, Spring Security with JWT
- **Frontend:** React 19 + TypeScript + Vite (`frontend/`), implementing the Figma Make design

## Features

| Area | What it does |
|---|---|
| Public site | Home, fleet browsing with search/type/sort filters, sign in and registration |
| Customers | "Reserve & book" a vehicle (held as **Pending**), view and cancel their own bookings |
| Staff dashboard | 30-day revenue and trend, active contracts, utilization, available fleet, recent activity |
| Fleet assets | Add, edit, delete vehicles; send to maintenance; Rwandan plate validation (`RAB 123 A`) |
| Customer directory | Register, edit, delete customers; `DL-` license validation; duplicate checks; CSV export |
| Rental contracts | Issue contracts with a live cost calculation; approve, mark returned, cancel, delete |
| System logs | Every action with who did it and when; search and CSV export |

Business rules (from the requirements document) are enforced on the server and mirrored in the forms:
BR-01 no duplicate email or driver license, BR-02 licenses start with `DL-`, BR-03/04 vehicle plate
format `RA[A-Z] 000 [A-Z]`, BR-05 edit in place, BR-06 delete with confirmation (blocked while a
booking or rental is open). Vehicle status always follows the contract:

```
customer books online  -> PENDING   (vehicle RESERVED)
staff issues contract  -> ACTIVE    (vehicle RENTED)
PENDING -> ACTIVE      approve           (vehicle RENTED)
ACTIVE  -> COMPLETED   vehicle returned  (vehicle AVAILABLE)
PENDING/ACTIVE -> CANCELLED              (vehicle AVAILABLE)
```

## Setup

1. **PostgreSQL:** create a database named `vrms_db` (tables are created automatically).
2. **Secrets:** copy `src/main/resources/application-secrets.properties.example` to
   `application-secrets.properties` (git-ignored) and fill in the database password, a JWT secret
   (`openssl rand -base64 48`) and the staff password.
3. **JDK 17:** in IntelliJ, File → Project Structure → SDK → `ms-17` (the system `java` is 1.8, which is too old).

The staff account (`admin@vrms.rw`, password from the secrets file) is created on first start. Demo
vehicles, customers and contracts are added while the database has no contracts yet
(turn off with `vrms.seed.demo-data=false`).

## Run

**Single app (what you'd deploy):**

```bash
cd frontend && npm install && npm run build && cd ..
./mvnw package          # bundles frontend/dist into the jar
java -jar target/VehicleRentalManagementSystem-1.0-SNAPSHOT.jar
```

Open http://localhost:8080.

**Development (hot reload):** run `VehicleRentalApplication` from IntelliJ (port 8080), then
`cd frontend && npm run dev` and open http://localhost:5173. Vite forwards `/api` to Spring Boot.

**Tests:** `./mvnw test` runs the API integration tests on an in-memory H2 database.

## REST API

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | anyone | Create account / sign in (returns JWT) |
| `GET /api/auth/me` | signed in | Current user |
| `GET /api/vehicles`, `GET /api/vehicles/{id}` | anyone | Browse the fleet |
| `POST/PUT/DELETE /api/vehicles…` | staff | Manage vehicles |
| `GET/POST/PUT/DELETE /api/customers…` | staff | Manage customers |
| `GET/POST /api/contracts`, `PATCH /api/contracts/{id}/status`, `DELETE /api/contracts/{id}` | staff | Contracts |
| `GET/POST /api/me/bookings`, `POST /api/me/bookings/{id}/cancel` | customer | Own bookings |
| `GET /api/dashboard`, `GET /api/logs` | staff | KPIs and audit log |

Errors always have the shape `{ status, message, fieldErrors: { field: message }, timestamp }`.
