# Micro Supply Chain Order Processing System

A RESTful microservice built with **Spring Boot 3 (Java 21)** for supply chain order management — demonstrating enterprise patterns including Kafka event streaming, Resilience4j retry, Spring Actuator health monitoring, and SLF4J structured logging.

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

The service follows a single-service architecture with full event-driven messaging via Kafka, making it easy to extract into multiple microservices later.

---

## Running Locally

### Prerequisites
- Java 21
- Docker + Docker Compose

### Start infrastructure
```bash
docker-compose up -d
```
This starts PostgreSQL (port 5433) and Kafka (port 9092).

### Run the backend
```bash
cd backend
./mvnw spring-boot:run
```
App starts on **http://localhost:8080**

### Run the frontend
```bash
cd frontend
npm install && npm run dev
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

### Example: Create an order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"itemName": "Widget A", "quantity": 10}'
```

### Example: Update status
```bash
curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "SHIPPED"}'
```

---

## Health & Observability (Spring Actuator)

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Service health (DB + Kafka status) |
| `GET /actuator/info` | App name, version, description |
| `GET /actuator/metrics` | JVM + HTTP metrics |

---

## Resilience4j Retry

Kafka publish operations are wrapped with `@Retry(name = "kafkaPublish")`:
- **Max attempts:** 3
- **Wait between retries:** 500 ms
- **Retry on:** `TimeoutException`, `RuntimeException`
- **Fallback:** logs the failure gracefully — the app never crashes

---

## Running Tests

```bash
cd backend
./mvnw test
```

Tests use **H2 in-memory DB** + **EmbeddedKafka** — no real infrastructure needed.

### Test coverage includes:
- **Unit tests** (`OrderServiceTest`) — mocked repo + Kafka, 12 cases
- **Web layer tests** (`OrderControllerTest`) — MockMvc, all endpoints + validation
- **Repository tests** (`OrderRepositoryTest`) — `@DataJpaTest` slice with H2
- **Integration tests** (`OrderIntegrationTest`) — full context, EmbeddedKafka, CRUD flow
