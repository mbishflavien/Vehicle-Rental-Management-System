# 5. Testing & quality assurance

## Strategy

| Level | Tooling | What it covers |
|---|---|---|
| **Unit** (backend) | JUnit 5, AssertJ | RBAC role → permission mapping, plate normalisation, rental day calculation, email/SMS templates (incl. HTML escaping and SMS length), phone number normalisation |
| **Integration** (backend) | Spring Boot Test, MockMvc, **Testcontainers** | The real API against **real PostgreSQL 17, MongoDB 7, RabbitMQ 3.13 and an SMTP inbox (Mailpit)** in Docker. The schema is built by the production Flyway migration in every test context |
| **End-to-end messaging** | Testcontainers + Awaitility + Mailpit HTTP API | An API call publishes an event → RabbitMQ → consumers → a real email arrives in the inbox and notifications are stored in MongoDB |
| **Unit / component** (frontend) | Vitest, Testing Library, jsdom | Formatting, search matching, UI components, registration validation, server field-error mapping, OAuth buttons |
| **Manual / exploratory** | Chrome at 3 device sizes (Playwright screenshots), Swagger UI, Mailpit, RabbitMQ UI | Responsive layout, real flows on the Docker stack |
| **Continuous integration** | GitHub Actions | All of the above on every push and pull request, plus the Docker image build |

## Test inventory

**Backend: 32 tests** (`src/test/java/com/vrms`)

| Area | Tests (`VrmsApiTest`, `MessagingTest`, `UnitTests`) |
|---|---|
| Authentication | staff account seeded; wrong password rejected; **sign-in throttling** (lockout after repeated failures); tampered/foreign tokens rejected; password change |
| RBAC | public vs staff endpoints (401/403); **agent can run rentals but not delete or read logs**; admin manages staff; **disabling revokes an existing token immediately**; last admin can't be demoted; role → permission unit tests |
| Business rules | BR-01 duplicates (incl. linking self-registration to a walk-in), BR-02 `DL-` licenses, BR-04 plate format and normalisation, BR-06 delete blocked while open; 404s for unknown ids |
| Contract lifecycle | online booking → Pending/Reserved; double booking refused; approve → Active/Rented with issuing staff; customer can't cancel an active rental; return → Available; completed is final; staff-issued contract starts Active; dashboard numbers |
| OAuth2 | Google/GitHub identity creates a customer, who must complete the profile before booking; linking to an existing walk-in; repeat sign-in reuses the account |
| MongoDB | license upload with **content sniffing** (script disguised as PNG rejected); staff download and verification; another customer gets 404; audit trail entries |
| RabbitMQ | booking + approval send the right **emails (checked in the SMTP inbox)**, staff alert and **SMS** to the normalised number; customers see only their own messages; **failed actions publish nothing** |
| Templates | HTML escaping of customer input, SMS ≤ 160 chars, wording with/without a branch |

**Frontend: 13 tests** (`frontend/src/test`): money/plate/period/ID formatting, date arithmetic,
space-insensitive search, field error accessibility, status pills, row menu actions, registration
validation (DL- and password rules before any API call), server field errors shown under the right
input, OAuth buttons shown only when enabled.

## Running the tests

```bash
./mvnw test                 # backend (needs Docker running for Testcontainers)
cd frontend && npm test     # frontend
```

## Results

| Suite | Result |
|---|---|
| Backend (`mvn verify`, local and GitHub Actions) | 32 passed, 0 failed |
| Frontend (`vitest run`) | 13 passed, 0 failed |
| GitHub Actions pipeline | frontend ✓ · backend ✓ · Docker image ✓ |

## Defects found by testing and fixed

| Found by | Defect | Fix |
|---|---|---|
| Integration test (messaging) | Staff alert emails were linked to the customer, so they appeared in the customer's own message list | Staff alerts are no longer tagged with the customer id |
| Frontend test | Error/hint text was part of the input's accessible name ("Password At least 8 characters…") | Explicit `<label for>` + `aria-describedby` |
| Live smoke test | Approval SMS read "Pick up … at to be confirmed" when no branch was set | Separate wording when the branch is unknown |
| Device screenshots | Mobile hero merged "personal" and "vehicle"; totals wrapped on phones | Explicit space; `white-space: nowrap` and smaller size on phones |
| First Testcontainers run | Docker Engine 29 rejects the old Docker API version used by Testcontainers 1.19 | Upgraded to 1.21 and pinned the API version |
| Earlier review | Static assets were served as `index.html` by the SPA fallback | Resource resolver that only falls back for extension-less routes |
