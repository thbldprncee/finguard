<div align="center">

# FinGuard

### Real-Time Anti-Fraud Transaction Monitoring System

A production-grade, event-driven microservice platform for real-time fraud detection in financial systems.  
Built with Java 21, Spring Boot 3, Apache Kafka, and React.

<br/>

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-7.6-231F20?style=flat-square&logo=apachekafka&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)

</div>

---

## Overview

Financial fraud costs the global economy hundreds of billions of dollars every year. Every payment made online, every card swipe at a terminal, and every wire transfer carries the risk of being fraudulent. Fintech companies need systems that can analyze transactions **in real time** — within milliseconds of a transaction being created — and flag or block suspicious activity before money leaves an account.

**FinGuard** simulates exactly this problem. When a user submits a transaction, it is immediately published to Apache Kafka. The Fraud Detection Service consumes the event, runs the transaction through a configurable rule engine, calculates a risk score, and emits a decision. Downstream services (Notification, Audit) react to the result. The API Gateway streams fraud decisions to the React dashboard in real time via Server-Sent Events (SSE).

The entire system is designed to reflect real patterns used in production fintech backends: idempotent APIs, JWT-based auth, schema-isolated databases per service, Flyway migrations, and full audit trails.

---

## Key Features

- **Real-time fraud analysis** — transactions are evaluated as they arrive, not in batch
- **Rule-based scoring engine** — 8 rules; whether a rule is enabled and its weight live in the database and can be changed at runtime
- **Fail-safe by design** — a rule that cannot be evaluated (e.g. Redis down) escalates the decision instead of silently lowering the risk score
- **Three-tier risk decisions** — `APPROVED`, `REVIEW_REQUIRED`, `BLOCKED`
- **Transactional Outbox** — state changes and their Kafka events are committed atomically; a relay publishes them afterwards
- **Idempotency end to end** — concurrency-safe `Idempotency-Key` per user on the API, idempotent Kafka consumers, idempotent Redis rule state
- **Keycloak (OpenID Connect)** — login, self-registration, brute-force protection and refresh-token rotation; services validate RS256 tokens via JWKS
- **Role-based access control** — `USER`, `ANALYST`, `ADMIN`; users only ever see their own accounts, transactions and live events
- **Hardened infrastructure** — Kafka with SASL/SCRAM + per-service ACLs, one database role per service, Redis password, no default secrets, nothing internal published to the host
- **Full audit trail** — domain events stored once per event id; Keycloak keeps login and admin events
- **Live dashboard** — authenticated SSE feed from the API Gateway, filtered per user

---

## Architecture

```
                     Browser (React SPA, OIDC + PKCE)
                                 │
                 ┌───────────────▼────────────────┐
                 │  nginx :3000                   │
                 │  /        static SPA           │
                 │  /auth/*  ──► Keycloak         │  (admin console only on 127.0.0.1:8180)
                 │  /api/*   ──► API Gateway      │
                 └───────────────┬────────────────┘
                                 │ Bearer access token (RS256)
                 ┌───────────────▼────────────────┐
                 │  API Gateway :8080             │  validates JWT (JWKS, iss, aud), strips
                 │  routes · SSE feed per user    │  client X-User-*/X-Client-Ip, sets X-Client-Ip
                 └──┬──────────────┬──────────┬───┘
                    │              │          │
           Transaction Svc   Fraud Svc   Audit Svc      every service validates the token again
            (accounts, tx)  (rule engine)
                    │   outbox      │ outbox
                    ▼               ▼
        ┌──────────────── Kafka (SASL/SCRAM + ACLs) ────────────────┐
        │ transaction.created ─► Fraud, Audit, Gateway              │
        │ fraud.analysis.completed ─► Transaction, Notification,    │
        │                             Audit, Gateway                │
        │ audit.event.created ─► Audit                              │
        └───────────────────────────────────────────────────────────┘
   PostgreSQL: one database + role per service (and Keycloak) · Redis: rule state
```

### Request flow

1. The SPA signs the user in through Keycloak (Authorization Code + PKCE) and keeps tokens in memory only
2. `POST /api/transactions` with the access token and an `Idempotency-Key`
3. The gateway validates the token, removes any client-supplied identity/IP headers and adds `X-Client-Ip` from the connection
4. Transaction Service checks that the source account belongs to the caller, claims the idempotency key and writes **the transaction and its outbox event in one database transaction**
5. The outbox relay publishes `transaction.created` (keyed by user id, so one user's events are processed in order)
6. Fraud Detection skips already-analysed transactions, evaluates the enabled rules, and writes the analysis plus its `fraud.analysis.completed` outbox event in one transaction
7. Transaction Service moves the transaction from `PENDING` to the decision (once); Notification and Audit consume in parallel
8. The gateway forwards events to SSE subscribers: staff see all, a user only their own (without rule details)

---

## Tech Stack

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language |
| Spring Boot | 3.3.5 | Application framework |
| Spring Cloud Gateway | 4.1.5 | API gateway, token validation, SSE |
| Spring Security | 6.x | OAuth2 Resource Server (JWT, JWKS) |
| Keycloak | 26 | Identity provider (OIDC), users, roles, brute-force protection |
| Spring Data JPA | 3.x | ORM / database access |
| Spring Kafka | 3.x | Kafka producer & consumer |
| PostgreSQL | 16 | One database per service + Keycloak |
| Redis | 7 | Rule state: sliding windows, devices, countries |
| Apache Kafka | 7.6 | Async event bus |
| Flyway | 10.x | Database schema migrations |
| Lombok | latest | Boilerplate reduction |
| SpringDoc OpenAPI | 2.6.0 | Swagger UI per service |

### Frontend
| Technology | Purpose |
|------------|---------|
| React 18 | UI framework |
| TypeScript | Type safety |
| Vite | Build tool |
| Tailwind CSS | Styling |
| React Query | Server state management |
| Axios | HTTP client |
| keycloak-js | OIDC login, token refresh |
| Recharts | Charts and data visualization |

### Infrastructure & Testing
| Technology | Purpose |
|------------|---------|
| Docker | Container runtime |
| Docker Compose | Multi-service orchestration |
| JUnit 5 | Unit and integration testing |
| Mockito | Mocking |
| Testcontainers | Real Postgres / Kafka in tests |

---

## Service Reference

| Service | Responsibilities |
|---------|-----------------|
| `keycloak` | Users, login/registration, tokens, roles, brute-force protection, login and admin events |
| `api-gateway` | Token validation, routing, client IP, correlation id, per-user SSE feed |
| `transaction-service` | Accounts, transactions, concurrency-safe idempotency, status lifecycle, outbox |
| `fraud-detection-service` | Rule engine, risk scoring, fraud decisions, rule administration, outbox |
| `notification-service` | Consumes fraud results, one notification per transaction and type (no HTTP API) |
| `audit-service` | Stores every domain event once, audit trail queries |

None of the services publishes a port in `docker-compose.yml`; only nginx (:3000), the gateway (127.0.0.1:8080) and the Keycloak admin console (127.0.0.1:8180) are reachable from the host.

---

## Fraud Detection Engine

Each rule reports whether it fired and why. The score it adds is its **weight from the `fraud_rules` table**; disabling or re-weighting a rule via `PATCH /api/fraud/rules/{id}` takes effect immediately (the engine reloads the settings after the update and every 30 s). Start-up fails if a rule implementation has no database row.

| Rule | Trigger | Default weight |
|------|---------|---------------|
| `HIGH_AMOUNT` | Amount above the per-currency threshold (USD 10 000, EUR 9 000, …) | +30 |
| `VELOCITY` | More than 5 transactions in a sliding 10-minute window | +25 |
| `STRUCTURING` | ≥ 3 transactions in 24 h, each below the threshold, together above it | +25 |
| `REPEATED_BLOCKED` | User had a blocked transaction in the last 24 hours | +25 |
| `COUNTRY_CHANGE` | Country differs from the user's previous transaction | +20 |
| `SUSPICIOUS_MERCHANT` | Merchant category `GAMBLING`, `CRYPTO` or `ADULT` | +20 |
| `NIGHT_ACTIVITY` | Created between 00:00–05:00 (zone configurable) | +15 |
| `NEW_DEVICE` | First transaction from a device id — or no device id at all | +15 |

Time-based rules use the transaction's creation time, not the time of analysis.

### Decisions and failures

| Score | Decision |
|-------|----------|
| 0 – 39 | `APPROVED` |
| 40 – 69 | `REVIEW_REQUIRED` |
| 70 – 100 | `BLOCKED` |

If any rule cannot be evaluated, the decision is raised to at least `REVIEW_REQUIRED` (`FRAUD_ON_RULE_FAILURE=BLOCK` blocks instead). An event that still fails after its retries reaches the dead-letter handler, which records the same failure-policy decision — a transaction never stays `PENDING` silently.

### Idempotency under at-least-once delivery

Kafka may deliver an event more than once. Fraud Detection skips transactions that already have an analysis, and the Redis-backed rules are idempotent per transaction id (sorted sets keyed by transaction id for velocity, structuring and blocked history; "first transaction seen" for devices; "previous country" remembered per transaction). A re-delivered event therefore yields the same score and is counted once.

---

## Event-Driven Flow

| Topic | Producer (via outbox) | Consumers |
|-------|----------|-----------|
| `transaction.created` | Transaction Service (key: user id) | Fraud Detection, Audit, Gateway |
| `fraud.analysis.completed` | Fraud Detection (key: transaction id) | Transaction, Notification, Audit, Gateway |
| `audit.event.created` | Fraud Detection (rule changes) | Audit |

Every record carries `X-Event-Id`, `X-Event-Type` and `X-Correlation-Id` headers. Each consumer uses its own retry/DLT topics (e.g. `fraud.analysis.completed-txn-retry-0`), so a failure in one service is never re-delivered to another. Kafka requires SCRAM authentication and each service may only read/write its own topics — only Fraud Detection can publish fraud decisions.

---

## API Reference

All requests go through nginx (`http://localhost:3000/api/...`) with `Authorization: Bearer <access token>` issued by Keycloak.

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/api/accounts` | USER | My accounts |
| `POST` | `/api/accounts` | USER | Open an account `{"currency":"USD"}` |
| `POST` | `/api/transactions` | USER | Create a transaction (from one of my accounts); optional `Idempotency-Key`, `X-Device-Id` |
| `GET` | `/api/transactions` | USER | My transactions (all for ANALYST/ADMIN) |
| `GET` | `/api/transactions/{id}` | USER | One transaction (404 if not mine) |
| `GET` | `/api/fraud/analysis/{transactionId}` | USER | Decision for my transaction; full analysis for staff |
| `GET` | `/api/fraud/alerts` | ANALYST | `REVIEW_REQUIRED` and `BLOCKED` analyses |
| `GET` | `/api/fraud/rules` | ANALYST | Rules with weights |
| `PATCH` | `/api/fraud/rules/{id}` | ADMIN | Enable/disable, change weight (audited) |
| `GET` | `/api/audit/events` | ANALYST | Audit trail (`eventType`, `aggregateId` filters) |
| `GET` | `/api/sse/events` | USER | Live events (own only for USER) |

Reusing an `Idempotency-Key` with the same body returns the original transaction (`200`, `Idempotent-Replayed: true`); with a different body it is rejected with `422`.

---

## Screenshots

### Dashboard
![Dashboard](./docs/dashboard.png)

### Transactions
![Transactions](./docs/transactions.png)

---

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and Docker Compose

### Run

```bash
git clone https://github.com/<your-username>/finguard.git
cd finguard
scripts/generate-env.sh        # random secrets into .env (git-ignored)
docker compose up --build
```

There are no default passwords: compose refuses to start until `.env` exists. Database roles and Kafka users are created on first start, so after upgrading from an older version recreate the volumes once with `docker compose down -v`.

### Access

| Interface | URL | Credentials |
|-----------|-----|-------------|
| **Frontend** | http://localhost:3000 | register on the login page (role USER), or `FINGUARD_ADMIN_EMAIL` / `FINGUARD_ADMIN_PASSWORD` from `.env` (ADMIN) |
| **Keycloak admin console** | http://127.0.0.1:8180/auth/admin | `admin` / `KEYCLOAK_ADMIN_PASSWORD` |

Granting `ANALYST` or `ADMIN`: Keycloak admin console → realm `finguard` → Users → *user* → Role mapping. The change applies with the user's next access token (at most 5 minutes).

For local debugging, `docker compose -f docker-compose.yml -f docker-compose.dev.yml up` additionally publishes the databases, Kafka (SCRAM credentials from `.env`) and the services on 127.0.0.1 and enables Swagger UI. `npm run dev` in `frontend/` proxies `/api` and `/auth` the same way nginx does.

---

## Configuration

Secrets come from `.env` (see `.env.example`). Service settings are typed `@ConfigurationProperties` records validated at start-up:

| Prefix | Service | Examples |
|--------|---------|----------|
| `finguard.security.oidc` | all APIs | `issuer`, `jwk-set-uri`, `audience` |
| `finguard.fraud` | fraud | `decision.on-rule-failure`, `rules.high-amount.thresholds.EUR`, `rules.velocity.window` |
| `finguard.transactions` | transaction | `supported-currencies`, `max-amount`, `idempotency-key-ttl` |
| `finguard.outbox` | transaction, fraud | `poll-interval`, `batch-size`, `retention` |
| `finguard.gateway` | gateway | `trust-proxy-headers`, `sse-heartbeat` |

---

## Running Tests

Tests use Testcontainers (PostgreSQL, Kafka, Redis), so Docker must be running. Without a local JDK 21:

```bash
scripts/gradle-docker.sh transaction-service test
scripts/gradle-docker.sh fraud-detection-service test
```

Highlights: 8 concurrent requests with one `Idempotency-Key` create exactly one transaction and one outbox event; outbox events reach Kafka with the right key and headers; a twice-delivered event yields one analysis; disabling a rule in the database changes the score; a malformed event ends in `REVIEW_REQUIRED` via the DLT; tokens with a wrong issuer, audience, algorithm or without a role are rejected; the SSE feed requires a token and only delivers a user's own events.

---

## Project Structure

```
finguard/
├── api-gateway/                # Spring Cloud Gateway, token validation, SSE feed
├── transaction-service/        # Accounts, transactions, idempotency, outbox
├── fraud-detection-service/    # Rule engine, Redis rule state, outbox
├── notification-service/       # Fraud result consumer
├── audit-service/              # Audit trail
├── frontend/                   # React 18 + TypeScript dashboard, nginx config
├── infra/
│   ├── keycloak/               # Realm import: roles, client, brute-force and token settings
│   ├── kafka/                  # SCRAM users, topics, ACLs
│   └── postgres/               # One database and role per service
├── scripts/                    # generate-env.sh, gradle-docker.sh
└── docker-compose.yml
```

---

## Design Decisions

**Why a Transactional Outbox?**
`@Transactional` does not span PostgreSQL and Kafka. Saving a transaction and then calling `kafkaTemplate.send()` can commit the row while the event is lost (or publish an event for a row that is rolled back). The outbox row is written in the same database transaction; a relay polls with `FOR UPDATE SKIP LOCKED` and publishes. Delivery becomes at-least-once, which is why every consumer is idempotent.

**Why claim the idempotency key with `INSERT … ON CONFLICT DO NOTHING`?**
"Check if the key exists, then insert" lets two concurrent requests both pass the check. The unique index makes the second insert wait for the first transaction and then report the conflict, so exactly one request creates the transaction and the other returns it. Keys are scoped per user and bound to a hash of the request.

**Why Keycloak instead of a home-made auth service?**
Password storage, brute-force protection, refresh-token rotation, revocation and role administration are solved problems. With RS256 the services hold only public keys, so no service can mint tokens — unlike a shared HMAC secret.

**Why fail-safe rules?**
For an anti-fraud system, "the check could not run" must never look like "the check passed".
