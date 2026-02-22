# Micro Supply Chain Order Processing System

A RESTful microservice built with **Spring Boot 3.4.5 (Java 21)** for supply chain order management — demonstrating enterprise patterns including Kafka event-driven processing, Resilience4j retry, Spring Actuator health monitoring, and SLF4J structured logging.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4.5 |
| Persistence | Spring Data JPA + PostgreSQL |
| Messaging | Apache Kafka (producer + consumer) |
| Resilience | Resilience4j Retry |
| Observability | Spring Actuator + SLF4J structured logging |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 5, Mockito, MockMvc, EmbeddedKafka, H2 |
| Frontend | Next.js (TypeScript) |
| Infra | Docker Compose |

---

## Architecture

```
Client
  │
  ▼
OrderController  (REST API — /api/orders)
  │
  ▼
OrderService     (business logic, @Retry Kafka publish)
  │           │
  ▼           ▼
OrderRepository  KafkaTemplate ──► orders-topic ──► OrderConsumer
(PostgreSQL)                                         (warehouse simulation)
```

The service follows a single-service architecture with full event-driven messaging via Kafka, making it straightforward to extract into multiple microservices later.

---

## Prerequisites

Make sure the following are installed before running anything:

| Tool | Version | Check |
|------|---------|-------|
| Java (JDK) | 21 or 25 | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker + Docker Compose | Any recent | `docker -v` |
| Node.js + npm | 18+ | `node -v` (frontend only) |

> **⚠️ Multiple JDKs installed (e.g. Homebrew Java 25 + Java 21)?** Maven can pick up the wrong JDK even if your terminal shows Java 21. The repo includes `.mvn/jvm.config` which sets `-Dnet.bytebuddy.experimental=true` to handle this — no manual steps needed. But if you see Mockito/ByteBuddy errors, double-check Maven is using Java 21:
>
> ```bash
> # Check which Java Maven is actually using
> mvn --version
>
> # If it shows the wrong JDK, set JAVA_HOME explicitly before running tests
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)
> mvn test
> ```

> **Note on Spring Boot version:** This project uses Spring Boot **3.4.5**. Spring Boot 4.x exists but is not used here because Resilience4j does not yet have a compatible `spring-boot4` starter — upgrading would break `@Retry` and the Kafka resilience patterns.

---

## Running Locally

### 1. Start infrastructure (PostgreSQL + Kafka)

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port **5433** (mapped from container's 5432)
- Kafka on port **9092**
- Zookeeper on port **2181**

### 2. Run the backend

```bash
cd backend
mvn spring-boot:run
```

App starts on **http://localhost:8080**

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:3000**

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/orders` | Place a new order |
| `GET` | `/api/orders` | Get all orders |
| `GET` | `/api/orders?status=PENDING` | Filter orders by status |
| `GET` | `/api/orders/{id}` | Get a single order |
| `PATCH` | `/api/orders/{id}/status` | Update order status |

**Valid statuses:** `PENDING` → `PROCESSING` → `SHIPPED` / `CANCELLED`

### Example requests

```bash
# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"itemName": "Widget A", "quantity": 10}'

# Get all orders
curl http://localhost:8080/api/orders

# Filter by status
curl http://localhost:8080/api/orders?status=PENDING

# Update status
curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "SHIPPED"}'
```

---

## Health & Observability (Spring Actuator)

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Service health — shows DB + Kafka component status |
| `GET /actuator/info` | App name, version, description |
| `GET /actuator/metrics` | JVM + HTTP metrics |

---

## Resilience4j Retry

Kafka publish operations are wrapped with `@Retry(name = "kafkaPublish")`:
- **Max attempts:** 3
- **Wait between retries:** 500 ms
- **Retries on:** `TimeoutException`, `RuntimeException`
- **Fallback:** logs the failure gracefully — the app never crashes on Kafka unavailability

---

## Running Tests

Tests use **H2 in-memory DB** + **EmbeddedKafka** — no real PostgreSQL or Kafka needed.

```bash
cd backend
mvn test
```

To run a specific test class:

```bash
mvn test -Dtest=OrderServiceTest
mvn test -Dtest=OrderControllerTest
mvn test -Dtest=OrderRepositoryTest
mvn test -Dtest=OrderIntegrationTest
```

### Test layers

| Test class | Type | What it covers |
|---|---|---|
| `OrderServiceTest` | Unit | Business logic with mocked repo + Kafka |
| `OrderControllerTest` | Web slice (MockMvc) | HTTP layer — status codes, validation, error responses |
| `OrderRepositoryTest` | JPA slice | Custom queries, timestamp persistence |
| `OrderIntegrationTest` | Full integration | End-to-end flow, status transitions, Actuator |

---

## Building a JAR

```bash
cd backend
mvn clean package -DskipTests
java -jar target/supply-service-0.0.1-SNAPSHOT.jar
```
