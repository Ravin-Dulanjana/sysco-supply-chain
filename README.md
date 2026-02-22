# Micro Supply Chain Order Processing System

Backend-focused microservices demo with a minimal frontend.

## Services

- `order-service` (`/backend`)
  - Port inside Docker: `8080`
  - Responsibilities: order APIs, PostgreSQL persistence, Kafka producer/consumer, retry, actuator
- `auth-service` (`/auth-service`)
  - Port inside Docker: `8081`
  - Responsibilities: simple login and JWT issuance
- `api-gateway` (`/gateway`, Nginx)
  - Exposed port: `8082`
  - Routes:
    - `/auth/*` -> `auth-service:8081`
    - `/api/*` -> `order-service:8080`
    - `/actuator/*` -> `order-service:8080`
- `frontend` (`/frontend`)
  - Port: `3000`
  - Talks only to `api-gateway` (single backend port)

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.4.5 (Java 21) |
| Database | PostgreSQL |
| Messaging | Apache Kafka + Zookeeper |
| Auth | JWT (JJWT) |
| Gateway | Nginx |
| Frontend | Next.js (TypeScript) |
| Containers | Docker Compose |

## Run with Docker Compose (Full Stack)

From project root:

```bash
docker-compose up -d --build
```

This starts:
- PostgreSQL on `5433`
- Kafka on `9092`
- API Gateway on `8082`
- Frontend on `3000`
- `auth-service` and `order-service` internally on the Docker network

Open: `http://localhost:3000`

## Login

Demo credentials:
- Username: `admin`
- Password: `admin123`

## API Usage Through Gateway

### Login

```bash
curl -X POST http://localhost:8082/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Order APIs (token required)

```bash
TOKEN="<jwt token>"

curl -X POST http://localhost:8082/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"itemName":"Widget A","quantity":10}'

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/orders

curl -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"SHIPPED"}'
```

## Local Development (optional)

If you want to run services manually instead of compose:

```bash
cd auth-service && mvn spring-boot:run
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

Frontend route handlers use `API_GATEWAY_URL` (default `http://localhost:8082`). In Docker compose, this is set to `http://api-gateway:8082`.

## Tests

Order service:

```bash
cd backend
mvn test
```

Auth service:

```bash
cd auth-service
mvn test
```

Frontend lint:

```bash
cd frontend
npm run lint
```
