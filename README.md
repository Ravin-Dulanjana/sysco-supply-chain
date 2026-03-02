# Micro Supply Chain Order Processing System

Backend-focused microservices demo built to practice enterprise backend concepts with Spring Boot, PostgreSQL, Kafka, JWT auth, saga orchestration, reliability patterns, and containerized local infrastructure.

## Architecture

### Services

- `frontend`
  - Next.js UI for login and order management
  - Talks only to the API gateway
- `api-gateway`
  - Nginx reverse proxy
  - Single backend entry point for the frontend
- `auth-service`
  - Issues JWTs for a demo user
- `order-service`
  - Owns order APIs, order persistence, saga orchestration, idempotency, and outbox relay
- `inventory-service`
  - Kafka-based inventory saga participant
- `payment-service`
  - Kafka-based payment saga participant
- `postgres-db`
  - Stores order data, idempotency records, and outbox rows
- `kafka` and `zookeeper`
  - Messaging backbone for event-driven flow
- `prometheus`
  - Scrapes actuator metrics from the Spring services
- `grafana`
  - Simple dashboard workspace with Prometheus provisioned as the default datasource

### Ports

- Frontend: `http://localhost:3000`
- API Gateway: `http://localhost:8082`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- PostgreSQL: `localhost:5433`
- Kafka: `localhost:9092`

## Key Technical Concepts

- Spring Boot 3.4.5 with Java 21
- PostgreSQL with Spring Data JPA
- Kafka-driven asynchronous communication
- Orchestrated saga across order, inventory, and payment services
- Transactional outbox in `order-service`
- Idempotent order creation using `Idempotency-Key`
- JWT-based authentication
- Actuator + Prometheus metrics
- Docker Compose local environment
- Testcontainers for Postgres and Kafka integration tests

## Order Flow

1. Client logs in through `auth-service` and receives a JWT.
2. Client creates an order through the gateway.
3. `order-service` persists the order and writes saga and operational events into the outbox.
4. `OutboxRelay` publishes those events to Kafka.
5. `inventory-service` consumes `RESERVE_INVENTORY` and responds with `INVENTORY_RESERVED` or `INVENTORY_REJECTED`.
6. `order-service` orchestrates the next step.
7. `payment-service` consumes `REQUEST_PAYMENT` and responds with `PAYMENT_COMPLETED` or `PAYMENT_FAILED`.
8. On payment failure, `order-service` triggers compensation with `RELEASE_INVENTORY`.

## Idempotency

`POST /api/orders` accepts an optional `Idempotency-Key` header.

Behavior:
- first request with a key creates the order and returns `201`
- repeated request with the same key and same payload returns the existing order and `200`
- repeated request with the same key and a different payload returns `400`

The frontend now generates an idempotency key automatically for each order submission.

## Saga Demo Triggers

- Inventory failure: set `quantity > 100`
- Payment failure and compensation: include `FAIL_PAYMENT` in `itemName`

## Monitoring

Prometheus scrapes:
- `auth-service` on `/actuator/prometheus`
- `order-service` on `/actuator/prometheus`
- `inventory-service` on `/actuator/prometheus`
- `payment-service` on `/actuator/prometheus`

Grafana is preconfigured with Prometheus as the default datasource.

Default Grafana credentials:
- username: `admin`
- password: `admin`

## Run Everything

From the project root:

```bash
docker compose up -d --build
```

Then open:
- Frontend: `http://localhost:3000`
- Grafana: `http://localhost:3001`
- Prometheus: `http://localhost:9090`

## Demo Login

- username: `admin`
- password: `admin123`

## API Examples

### Login

```bash
curl -X POST http://localhost:8082/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Create Order

```bash
TOKEN="<jwt token>"

curl -X POST http://localhost:8082/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: order-001" \
  -H "Content-Type: application/json" \
  -d '{"itemName":"Widget A","quantity":10}'
```

### List Orders

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/orders
```

### Update Order Status

```bash
curl -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"SHIPPED"}'
```

## Testing

### Order Service

```bash
cd backend
mvn test
```

Notes:
- unit, controller, repository, and outbox tests run normally
- Testcontainers-based tests run when Docker is available
- if Docker is not available, those container-backed tests are skipped instead of failing the whole build

### Other Services

```bash
cd auth-service && mvn test
cd inventory-service && mvn test
cd payment-service && mvn test
cd frontend && npm run lint
```

## Project Structure

- `/Users/ravinfernando/dev/sysco-supply-chain/backend`
  - order-service
- `/Users/ravinfernando/dev/sysco-supply-chain/auth-service`
  - JWT login service
- `/Users/ravinfernando/dev/sysco-supply-chain/inventory-service`
  - inventory participant
- `/Users/ravinfernando/dev/sysco-supply-chain/payment-service`
  - payment participant
- `/Users/ravinfernando/dev/sysco-supply-chain/frontend`
  - Next.js UI
- `/Users/ravinfernando/dev/sysco-supply-chain/gateway`
  - Nginx config
- `/Users/ravinfernando/dev/sysco-supply-chain/monitoring`
  - Prometheus and Grafana provisioning
