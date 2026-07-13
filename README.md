# OrderFlow 🛒⚡

**OrderFlow** is a multi-tenant, event-driven order fulfillment platform delivered as SaaS. Multiple stores (tenants) use OrderFlow to process orders, manage inventory, and handle payments through a choreographed Saga running on Apache Kafka. Each service owns its data, communicates asynchronously via domain events, and compensates automatically on failure — no central coordinator required.

Conceptually, OrderFlow is "fulfillment infrastructure as a service" — a simplified version of what a platform like Shopify's order pipeline does internally, exposed as a product other businesses subscribe to.

---

## 🚀 Key Features

- **Choreographed Saga:** Order lifecycle spans three independent services (Order, Inventory, Payment) coordinated entirely through Kafka events, with automatic compensation on failure — no central orchestrator as a single point of failure.
- **Transactional Outbox:** Order creation and event publishing happen in the same database transaction, eliminating the dual-write problem between PostgreSQL and Kafka.
- **Polyglot Persistence:** PostgreSQL for transactional data (orders, payments), MongoDB for the product catalog (flexible, category-dependent attributes), Redis for caching and rate limiting.
- **Hybrid Sync/Async Communication:** gRPC for the one call requiring an immediate answer (stock availability check before order creation); Kafka for everything else.
- **Multi-Tenancy:** JWT-based tenant isolation with row-level filtering (Hibernate `@Filter` for PostgreSQL, manual scoping with test enforcement for MongoDB). Each tenant's data stays isolated without provisioning separate infrastructure.
- **Per-Tenant Rate Limiting:** Redis token-bucket rate limiting at the API Gateway, enforced per tenant plan (FREE / PRO).
- **Serverless Webhook Ingestion:** AWS Lambda (Node.js) receives external payment provider webhooks — bursty, stateless, cold-start-tolerant traffic handled outside the JVM services.
- **Idempotent Consumers:** Every Kafka listener deduplicates via a `processed_events` table/collection, ensuring exactly-once-ish processing even on redelivery.
- **Dead Letter Topics:** Failed messages are routed to `<topic>.DLT` after retry exhaustion, mirroring the DLQ pattern used in the sibling MCNE project (adapted from RabbitMQ to Kafka).
- **Full Observability:** Micrometer metrics exported to Prometheus, visualized in Grafana dashboards tracking saga completion rate, per-service p99 latency, Kafka consumer lag, and per-tenant order volume.
- **Tenant Dashboard:** React + Vite frontend with live order list, saga timeline visualization per order, and plan usage indicators.

---

## 🛠️ Technology Stack

- **Language:** Java 21 (Records, Pattern Matching, modern switch expressions) + Node.js 20 (Lambda)
- **Framework:** Spring Boot 4.1.0 (Spring Framework 7.0.8)
- **Cloud:** Spring Cloud 2025.1.2 "Oakwood", Spring Cloud Gateway 5.0.1
- **Messaging:** Apache Kafka (KRaft mode, no Zookeeper)
- **Sync RPC:** gRPC (Spring Boot 4.1 native support)
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
| Prometheus | 9090 | Metrics aggregation |
| Grafana | 3000 | Dashboards and alerting |

To start all infrastructure services:

```bash
docker compose -f infra/docker-compose.yml up -d
```

Verify that all containers are running:

```bash
docker ps
```

### 3. Environment Variables

The application uses sensible defaults for local development. To override credentials or connection endpoints in other environments, set the following before running:

| Variable | Description | Default (dev) |
| :--- | :--- | :--- |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials | `orderflow_user` / `orderflow_password` |
| `MONGO_URI` | MongoDB connection string | `mongodb://localhost:27018/orderflow` |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection | `localhost` / `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker(s) | `localhost:9092` |
| `JWT_ISSUER_URI` | OAuth2 issuer URI | `http://localhost:8090/issuer` |
| `INTERNAL_API_KEY` | Shared secret for Lambda → Payment Service | _(set in `.env`)_ |
| `PAYMENT_WEBHOOK_SECRET` | Webhook signature validation secret | _(set in `.env`)_ |

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

The tenant dashboard will be available at `http://localhost:5175`.

---

## 📡 REST API Documentation

> All public endpoints are routed through the API Gateway and require a valid JWT bearer token.

### Order Endpoints

| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/orders` | Creates an order (triggers the Saga) | `201 Created` |
| **GET** | `/api/v1/orders/{id}` | Fetches an order with current Saga status | `200 OK` |
| **GET** | `/api/v1/orders` | Lists orders for the authenticated tenant | `200 OK` |
| **POST** | `/api/v1/orders/{id}/cancel` | Explicit cancellation (business-intent endpoint) | `200 OK` |

### Inventory Endpoints

| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/v1/products` | Lists the product catalog (Redis-cached) | `200 OK` |
| **GET** | `/api/v1/products/{id}` | Fetches product detail | `200 OK` |

### Payment Endpoints

| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/v1/payments/{orderId}` | Fetches payment status for an order | `200 OK` |
| **POST** | `/internal/v1/payment-webhook` | Internal: called by Lambda, shared-secret header | `200 OK` |

---

## 🏗️ Repository Structure

```
order-flow/
├── pom.xml                          # Parent POM (dependency & plugin management)
├── common/                          # Shared DTOs, event envelopes, proto contracts
├── api-gateway/                     # Spring Cloud Gateway, JWT validation, rate limiting
├── order-service/                   # Order lifecycle, Outbox, Saga reactions
├── inventory-service/               # Catalog (Mongo), stock reservation, gRPC server
├── payment-service/                 # Payment authorization, webhook ingestion
├── serverless/
│   └── payment-webhook-lambda/      # Node.js Lambda (deployed independently)
├── frontend/                        # React + Vite tenant dashboard
├── infra/
│   ├── docker-compose.yml           # Kafka, Postgres, Mongo, Redis, Prometheus, Grafana
│   └── init-db.sh                   # Creates per-service databases on first startup
├── docs/
│   └── ARCHITECTURE.md              # Full design rationale, diagrams, and trade-offs
└── README.md
```

---

## 📂 Architecture & Coding Standards

Architecture decisions, component diagrams, Saga sequence flows, data schemas, and design trade-offs are documented in the living architecture reference:

- [ARCHITECTURE.md](docs/ARCHITECTURE.md): System overview, service responsibilities, Kafka topic map, multi-tenancy model, event envelope schema, and future improvement roadmap.
