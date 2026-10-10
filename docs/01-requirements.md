# 1. Requirements

## 1.1 Problem statement

Vehicle rental agencies in Rwanda still run much of their business on paper registers, phone calls and
unlinked spreadsheets. Staff check availability by hand, so vehicles get **double-booked** or sit idle;
customer details and driver licenses are copied without validation, creating **duplicate and invalid
records**; plate numbers are entered in inconsistent formats; and there is **no audit trail** of who
changed what. Customers cannot see the fleet or book without visiting or calling, and they hear nothing
until someone remembers to phone them.

**VRMS** (Vehicle Rental Management System) is a web platform that puts the fleet, customers and rental
contracts in one place, lets customers browse and book online, keeps vehicle status consistent with
every contract automatically, notifies customers by email and SMS, and records every action.

## 1.2 Target users

| User | Needs | Role in VRMS |
|---|---|---|
| **Customer** (residents, business travellers, tourists) | See available vehicles and prices, book online, know the booking status, upload their license once | `CUSTOMER` |
| **Rental agent** (branch desk staff) | Register walk-in customers, issue contracts, approve bookings, record returns | `AGENT` |
| **Operations manager** | Everything an agent does, plus fleet and branch management, deleting records, audit log, staff accounts | `ADMIN` |

## 1.3 Objectives (measurable)

| # | Objective | Measure | Result |
|---|---|---|---|
| O1 | No double bookings | A vehicle can't have two open (pending/active) contracts | Enforced with a row lock + status check; covered by `onlineBookingReservesVehicleThenStaffApproveAndReturn` |
| O2 | No invalid or duplicate records | 100% of plates/licenses validated; duplicate email/license rejected | Validated in the UI, API (Bean Validation) **and** database (CHECK + unique indexes) |
| O3 | Customers book without calling | Online booking in ≤ 3 steps from the fleet page | Reserve → (sign in) → Confirm |
| O4 | Customers are kept informed | Every booking/status change emails (and texts) the customer within seconds | RabbitMQ consumers; verified end to end in `MessagingTest` |
| O5 | Full accountability | Every create/update/delete/sign-in is logged with actor and time | MongoDB audit trail, System Logs page |

## 1.4 Scope

**In scope:** public fleet browsing and search; customer accounts (email/password, Google, GitHub);
online booking and cancellation; customer document upload; staff console for vehicles, branches,
customers, contracts (issue, approve, return, cancel), staff accounts and roles; email and SMS
notifications; dashboard KPIs; audit log; Docker deployment and CI/CD.

**Out of scope (future work):** online payments (Mobile Money, cards), GPS tracking, damage
inspections with photos, multi-currency, a native mobile app.

## 1.5 Functional / business requirements

| ID | Requirement | Where it's implemented |
|---|---|---|
| BR-01 | Capture customer full name, email, phone and driver license; **prevent duplicates** (same email or license). A customer who self-registers with the same email and license as an existing walk-in record is linked to it, not duplicated | `CustomerService`, `AuthService.register`, unique indexes `uk_customers_email_ci`, `uk_customers_license` |
| BR-02 | Driver licenses must start with `DL-` | `@Pattern` on `Customer`/`RegisterRequest`, CHECK `ck_customers_license_format`, form validation |
| BR-03 | Register vehicles with plate, model and daily rate (RWF), plus category, transmission, fuel, seats, photo and home branch | `VehicleService`, Fleet Assets page |
| BR-04 | Plates must match the Rwandan format `RA[A-Z] 000 [A-Z]`, stored without spaces (`RAB123A`) | `Vehicle.normalizePlate`, `@Pattern`, CHECK `ck_vehicles_plate_format` |
| BR-05 | Edit records in place (form pre-filled from the table row) | Edit drawers on every staff table |
| BR-06 | Delete customers and vehicles with explicit confirmation; refused while they have an open booking or rental | Confirmation dialogs, `VehicleService.delete`, `CustomerService.delete` |
| FR-07 | Customers book online; the booking is **Pending** and the vehicle **Reserved** until staff approve it | `ContractService.book` |
| FR-08 | Contract lifecycle: Pending → Active → Completed, or Cancelled; vehicle status always follows (Reserved/Rented/Available); rentals max 90 days; cost = days × daily rate | `ContractService` (state machine) |
| FR-09 | Email and SMS for: welcome, booking received, contract issued/approved/completed/cancelled, document verified/rejected; staff alerted of new bookings | RabbitMQ consumers (`NotificationListeners`) |
| FR-10 | Customers upload license/ID scans (PDF/JPEG/PNG ≤ 5 MB); staff verify or reject them | `DocumentService` (MongoDB GridFS) |
| FR-11 | Role-based access: Admin, Agent, Customer with distinct permissions; admins manage staff accounts and can disable them | `Role`/`Permission`, `@PreAuthorize`, Staff & Access page |
| FR-12 | Sign in with email/password or Google/GitHub (OAuth2) | `OAuth2LoginConfig`, `AuthService` |
| FR-13 | Audit trail of every action, searchable and exportable | `AuditService` (MongoDB), System Logs page |
| FR-14 | Dashboard: 30-day revenue and trend, active contracts, utilization, availability, recent activity | `DashboardService` |

## 1.6 Quality attributes

| Attribute | Target | How it's achieved | Evidence |
|---|---|---|---|
| **Usability** | Works from 390 px phones to 1440 px desktops; every form shows errors next to the field; accessible labels | Responsive CSS with 3 breakpoints (1100/820/560 px); server field errors mapped to inputs; `aria-describedby`, keyboard-closable dialogs | [UI screenshots](04-ui-design.md), `components.test.tsx` |
| **Performance** | p95 API latency < 200 ms on a laptop; public JS < 150 KB gzipped | Caffeine caches (fleet, branches, dashboard), entity graphs (no N+1), partial indexes, gzip, immutable asset caching, code-split staff console | Measured p95: fleet 18 ms, contracts 80 ms, dashboard 95 ms, logs 75 ms; main bundle 100 KB gzipped |
| **Reliability** | No lost or contradictory state; notifications survive broker hiccups | Transactions with row locks for bookings; events published **after commit**; RabbitMQ retries ×3 then dead-letter queue; idempotent consumers; health checks for every dependency | `MessagingTest.failedActionsPublishNothing`, `/actuator/health` |
| **Security** | Only authorised users reach each endpoint; credentials can't be brute-forced | OAuth2 resource server (signed JWT: signature, expiry, issuer, audience), BCrypt, RBAC permissions per endpoint, lockout after 5 failed sign-ins for 15 min, CSP and referrer headers, upload type sniffing, secrets outside Git | 9 security tests in `VrmsApiTest` |
| **Maintainability** | Clear layers; changes are tested automatically | Controller → Service → Repository layering, DTOs, versioned schema (Flyway), 32 backend + 13 frontend tests in CI | [Architecture](03-architecture.md), [Testing](05-testing.md) |

## 1.7 User stories and acceptance criteria

### US-1 Book a vehicle online (Customer)

> *As a customer, I want to reserve an available vehicle for my dates online, so that I don't have to call the agency.*

- **Given** I am signed in with a completed profile **and** the Toyota RAV4 is Available,
  **when** I choose dates 12–17 June and confirm,
  **then** a contract is created as **Pending**, the total is 5 × daily rate, the vehicle becomes **Reserved**,
  and I receive a "We've received your booking" email and SMS.
- **Given** the vehicle has just been reserved by someone else, **when** I confirm, **then** I see
  "*… is not available right now*" and no booking is created.
- **Given** my return date is not after my start date (or the start is in the past), **then** the form shows the error under that date.

*Tests:* `onlineBookingReservesVehicleThenStaffApproveAndReturn`, `bookingAndApprovalSendEmailsAndTextsThroughRabbitMq`.

### US-2 Approve and close a rental (Rental agent)

> *As a rental agent, I want to approve pending bookings and record returns, so that the fleet status is always correct.*

- **Given** a Pending booking, **when** I choose *Approve & hand over*, **then** it becomes **Active**, the
  vehicle **Rented**, I am recorded as the issuing staff member, and the customer is emailed "Confirmed".
- **Given** an Active contract, **when** I choose *Mark returned*, **then** it becomes **Completed** and the vehicle **Available**.
- **Given** I am an agent, **then** I do not see *Delete* actions, System Logs or Staff, and the API answers **403** if I try them.

*Tests:* `agentCanRunRentalsButNotDeleteOrReadAuditLog`, `onlineBookingReservesVehicleThenStaffApproveAndReturn`.

### US-3 Keep customer records clean (Operations manager)

> *As an operations manager, I want invalid or duplicate customers and vehicles rejected, so that our records are trustworthy.*

- **Given** I register a customer with license `12345`, **then** I see "*Driver License must start with 'DL-'*".
- **Given** a customer with email `eric@email.com` exists, **when** I register another with the same email, **then** the email field shows "*A customer with this email is already registered*".
- **Given** I add a vehicle with plate `rab 123 a`, **then** it is saved as `RAB123A`; `XYZ 123` is rejected.
- **Given** a vehicle has an open rental, **when** I try to delete it, **then** the deletion is refused with an explanation.

*Tests:* `driverLicenseMustStartWithDlAndDuplicatesAreRejected`, `plateMustBeRwandanFormatAndIsNormalized`, `staffIssuedContractStartsActiveAndBlocksDeletes`.
