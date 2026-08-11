# OrderFlow 🛒⚡

**OrderFlow** is a multi-tenant, event-driven order fulfillment platform delivered as SaaS. Multiple stores (tenants) use OrderFlow to process orders, manage inventory, and handle payments through a choreographed Saga running on Apache Kafka. Each service owns its data, communicates asynchronously via domain events, and compensates automatically on failure — no central coordinator required.

Conceptually, OrderFlow is "fulfillment infrastructure as a service" — a simplified version of what a platform like Shopify's order pipeline does internally, exposed as a product other businesses subscribe to.

---

## 🚀 Key Features

> **This project is under active development.** The list below is the full design;
> each item is marked with what is built today. See
> [ARCHITECTURE.md §17](docs/ARCHITECTURE.md#17-implementation-status) for the phase
> breakdown.

**Built**

- ✅ **Transactional Outbox:** Order creation and event recording happen in the same database transaction, eliminating the dual-write problem between PostgreSQL and Kafka. A polling publisher waits for the broker acknowledgement before marking a row published, so a failed send is retried rather than lost.
- ✅ **Hybrid Sync/Async Communication:** gRPC for the one call requiring an immediate answer (stock availability for the requested quantity, before the order is accepted); Kafka for everything else.
- ✅ **Polyglot Persistence:** PostgreSQL for transactional data (orders, outbox), MongoDB for the product catalog with flexible, category-dependent attributes. Redis is provisioned for Phase 7.
- ✅ **Authoritative Pricing:** Item prices and names are never taken from the client. They are read from the catalog over gRPC, so a tampered request cannot change what an order costs.
- ✅ **Versioned Schema:** Flyway owns the PostgreSQL schema, with Hibernate set to `validate` and an integration test that runs the real migrations to catch entity/schema drift.
- ✅ **RFC 7807 Error Contract:** `ProblemDetail` responses that distinguish business outcomes from server faults — `409` for insufficient stock, `404` for an unknown product, `503` for an unreachable dependency.

**Planned**

- ✅ **Choreographed Saga:** Order lifecycle spanning three independent services (Order, Inventory, Payment) coordinated entirely through Kafka events, with automatic compensation on failure — no central orchestrator as a single point of failure. *(Phases 4–6; `orders.created`, `inventory.reserved`, `payment.authorized`, and failure events are fully consumed today. Final reactions are implemented in Phase 6.)*
- ✅ **Idempotent Consumers:** Every Kafka listener deduplicates via a `processed_events` table/collection, ensuring exactly-once-ish processing even on redelivery. The `eventId` that makes this possible is already on the wire. *(Implemented in Phases 4–5)*
- ✅ **Dead Letter Topics:** Failed messages routed to `<topic>.DLT` after retry exhaustion, mirroring the DLQ pattern used in the sibling MCNE project (adapted from RabbitMQ to Kafka). *(Implemented in Phases 4–5)*
- ✅ **Multi-Tenancy:** JWT-based tenant isolation with row-level filtering (Hibernate `@Filter` for PostgreSQL, manual scoping with test enforcement for MongoDB). Tenant scoping is enforced by extracting the `tenant_id` claim from the JWT at the API Gateway and forwarding it securely via the `X-Tenant-Id` header. *(Implemented in Phase 7)*
- ✅ **Per-Tenant Rate Limiting:** Redis token-bucket rate limiting at the API Gateway, enforced per tenant based on their plan limits. *(Implemented in Phase 7)*
- ✅ **Full Observability:** Micrometer metrics exported to Prometheus, enabling Grafana dashboards to track saga completion rates, per-service p99 latency, Kafka consumer lag, and per-tenant order volume. Additionally, Distributed Tracing (Brave) propagates `traceId` across HTTP and Kafka boundaries for comprehensive logging correlation. *(Implemented in Phase 8)*
- ⬜ **Serverless Webhook Ingestion:** AWS Lambda (Node.js) receiving external payment provider webhooks — bursty, stateless, cold-start-tolerant traffic handled outside the JVM services. *(Phase 9)*
- ⬜ **Tenant Dashboard:** React + Vite frontend with live order list, saga timeline visualization per order, and plan usage indicators. *(Phase 10)*

---

## 🛠️ Technology Stack

- **Language:** Java 21 (Records, Pattern Matching, modern switch expressions) + Node.js 20 (Lambda)
- **Framework:** Spring Boot 4.1.0 (Spring Framework 7.0.8)
- **Cloud:** Spring Cloud 2025.1.2 "Oakwood", Spring Cloud Gateway 5.0.1
- **Messaging:** Apache Kafka (KRaft mode, no Zookeeper)
- **Sync RPC:** gRPC via `net.devh:grpc-{client,server}-spring-boot-starter` (community starter; migrating to Spring's own gRPC support is an open item — see [ARCHITECTURE.md §16](docs/ARCHITECTURE.md#16-design-trade-offs--future-improvements))
- **Relational DB:** PostgreSQL 16 (separate databases per service, schema managed by Flyway)
- **NoSQL DB:** MongoDB 7.x (product catalog with flexible category attributes)
- **Cache:** Redis 7.x (catalog caching, per-tenant rate limiting)
- **Auth:** OAuth2 / JWT (Spring Security Resource Server)
- **Serverless:** AWS Lambda + API Gateway (payment webhook receiver)
- **Observability:** Micrometer, Prometheus, Grafana
- **Frontend:** React 19 + Vite
- **Testing:** JUnit 5, Mockito, Testcontainers (PostgreSQL, MongoDB, Kafka)
- **Build:** Maven (multi-module reactor)
- **Containerization:** Docker / Docker Compose

---

## 📋 Prerequisites

To run this application locally, you will need:

1. **Java JDK 21** or higher.
2. **Node.js 20+** (for the frontend and the Lambda function).
3. **Docker & Docker Compose** installed and running.
4. **Maven** (or use the included `./mvnw` wrapper).

---

## ⚙️ How to Get Started

### 1. Clone the repository

```bash
git clone https://github.com/pmfml/order-flow.git
cd order-flow
```

### 2. Infrastructure Setup (Docker)

The project includes a `docker-compose.yml` defining all required backing services:

| Service | Host Port | Notes |
| :--- | :--- | :--- |
| PostgreSQL | 5436 | Shared instance, separate `orders` and `payments` databases |
| MongoDB | 27018 | Product catalog and stock reservations |
| Redis | 6379 | Catalog cache and per-tenant rate limiting |
| Kafka (KRaft) | 9092 | Single broker, no Zookeeper |
| Prometheus | 9090 | Starts, but scrapes nothing until Phase 8 wires Actuator |
| Grafana | 3000 | Starts with no dashboards provisioned yet _(Phase 8)_ |

To start all infrastructure services:

```bash
docker compose -f infra/docker-compose.yml up -d
```

Verify that all containers are running:

```bash
docker ps
```

### 3. Environment Variables

Every variable has a working default for local development, so **this step is optional**.
Copy the template only if you need to diverge from it:

```bash
cp .env.example .env
```

| Variable | Description | Default (dev) |
| :--- | :--- | :--- |
| `DB_URL` | Full JDBC URL, per service | `jdbc:postgresql://localhost:5436/{orders,payments}` |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials | `orderflow_user` / `orderflow_password` |
| `MONGO_URI` | MongoDB connection string | `mongodb://localhost:27018/orderflow` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker(s) | `localhost:9092` |
| `SERVER_PORT` | Overrides a service's HTTP port | per service, see below |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin password | `admin` |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection _(Phase 7)_ | `localhost` / `6379` |
| `JWT_ISSUER_URI` | OAuth2 issuer URI (points to mock server in dev) _(Phase 7)_ | `http://localhost:8099/orderflow` |
| `JWT_JWK_SET_URI` | OAuth2 JWK Set URI _(Phase 7)_ | `http://localhost:8099/orderflow/jwks` |
| `INTERNAL_API_KEY` | Shared secret for Lambda → Payment Service _(Phase 9)_ | _(unset)_ |
| `PAYMENT_WEBHOOK_SECRET` | Webhook signature validation secret _(Phase 9)_ | _(unset)_ |

### 4. Build & Run Tests

```bash
./mvnw clean verify
```

### 5. Running a Service

Each service can be started individually from the project root:

```bash
./mvnw -pl order-service spring-boot:run
```

Service ports:

| Service | Port |
| :--- | :--- |
| API Gateway | 8090 |
| Order Service | 8091 |
| Inventory Service | 8092 (REST) / 9095 (gRPC) |
| Payment Service | 8093 |

### 6. Running the Frontend

```bash
cd frontend && npm install && npm run dev
```

Serves on `http://localhost:5175`. This is currently the default Vite scaffolding — the
tenant dashboard is Phase 10.

---

## 📡 REST API Documentation

The `/api` prefix below is the public path served by the API Gateway, which strips it
before forwarding. **The Gateway is Phase 7**, so today services are called directly on
their own ports and without the prefix — the one implemented endpoint is
`POST http://localhost:8091/v1/orders`.

Authentication is also Phase 7. There is currently **no JWT validation**: the tenant is
taken from an `X-Tenant-Id` header, which any caller can set.

### Order Endpoints

| Method | Endpoint | Description | Status |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/orders` | Creates an order (triggers the Saga) → `201 Created` | ✅ Built |
| **GET** | `/api/v1/orders/{id}` | Fetches an order with current Saga status | ✅ Phase 6 |
| **GET** | `/api/v1/orders` | Lists orders for the authenticated tenant | ✅ Phase 6 |
| **POST** | `/api/v1/orders/{id}/cancel` | Explicit cancellation (business-intent endpoint) | ✅ Phase 6 |

### Inventory Endpoints

| Method | Endpoint | Description | Status |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/v1/products` | Lists the product catalog (Redis-cached) | ⬜ Phase 4 |
| **GET** | `/api/v1/products/{id}` | Fetches product detail | ⬜ Phase 4 |

### Payment Endpoints

| Method | Endpoint | Description | Status |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/v1/payments/{orderId}` | Fetches payment status for an order | ⬜ Phase 9 |
| **POST** | `/internal/v1/payment-webhook` | Internal: called by Lambda, shared-secret header | ⬜ Phase 9 |

### Error Responses

Errors follow RFC 7807 (`application/problem+json`):

| Status | Condition |
| :--- | :--- |
| `400` | Validation failure (per-field details under `fieldErrors`), missing `X-Tenant-Id`, malformed body |
| `404` | Product unknown to the tenant. A product belonging to another tenant is reported as not found rather than forbidden, so the catalog of other tenants is not disclosed |
| `409` | Insufficient stock, with `requestedQuantity` and `availableQuantity` |
| `503` | Inventory Service unreachable — the request is worth retrying |
| `500` | Unexpected server fault. The response detail is always generic; specifics are logged, never returned |

```json
{
  "type": "https://orderflow.invalid/insufficient-stock",
  "title": "Insufficient Stock",
  "status": 409,
  "detail": "Insufficient stock for product prod-scarce: requested 500, available 1",
  "instance": "/v1/orders",
  "productId": "prod-scarce",
  "requestedQuantity": 500,
  "availableQuantity": 1
}
```

---

## 🏗️ Repository Structure

```
order-flow/
├── pom.xml                          # Parent POM (dependency & plugin management)
├── .env.example                     # Local overrides; every value has a working default
├── common/                          # Event envelope, event/topic names, proto contracts
├── api-gateway/                     # Spring Cloud Gateway — routing & JWT are Phase 7
├── order-service/                   # Order lifecycle, Outbox publisher, REST API
├── inventory-service/               # Catalog (Mongo), gRPC CheckStock server
├── payment-service/                 # Scaffolded; implemented in Phase 5
├── serverless/
│   └── payment-webhook-lambda/      # Empty; Node.js Lambda arrives in Phase 9
├── frontend/                        # Vite scaffolding; dashboard is Phase 10
├── infra/
│   ├── docker-compose.yml           # Kafka, Postgres, Mongo, Redis, Prometheus, Grafana
│   └── init-db.sh                   # Creates per-service databases on first startup
├── docs/
│   ├── ARCHITECTURE.md              # Design rationale, diagrams, trade-offs, status
│   ├── EVENTS.md                    # Kafka event contract and evolution rules
│   └── TROUBLESHOOTING.md           # Solutions to Spring Boot 4.x migration issues
└── README.md
```

---

## 📂 Architecture & Coding Standards

Architecture decisions, component diagrams, Saga sequence flows, data schemas, and design trade-offs are documented in the living architecture reference:

- [ARCHITECTURE.md](docs/ARCHITECTURE.md): System overview, service responsibilities, Kafka topic map, multi-tenancy model, design trade-offs, and — in §17 — the authoritative record of which phases are actually implemented.
- [EVENTS.md](docs/EVENTS.md): The Kafka event contract. Envelope fields, message key, per-event payload schemas, the idempotency requirement for consumers, and the rules for evolving a payload without breaking one.
- [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md): Documented solutions for Spring Boot 4.x breaking changes — Jackson 3 migration, test annotation package moves, and the silent failures caused by auto-configuration modularization (Flyway never running, `spring.data.mongodb.*` no longer being bound).
