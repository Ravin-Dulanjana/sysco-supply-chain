# Sysco Supply Chain - Microservices Demo 📦

A full-stack supply chain management system developed as a technical demonstration for the **Software Engineer (Backend)** role at Sysco LABS. This project implements a decoupled, event-driven architecture using **Spring Boot** and **Apache Kafka**.

## 🚀 Key Features
- **Event-Driven Architecture:** Decoupled Producer (Supply Service) and Consumer (Warehouse Notification).
- **Data Persistence:** Relational data storage using PostgreSQL.
- **Data Streaming:** Real-time message broadcasting via Kafka.
- **Modern Frontend:** React-based dashboard for placing supply orders.
- **Containerization:** Entire infrastructure (DB + Kafka) managed via Docker Compose.

---

## 🛠️ Tech Stack
- **Backend:** Java 21, Spring Boot 3.x, Spring Data JPA, Hibernate, Spring Kafka.
- **Frontend:** Next.js 14 (App Router), React, Tailwind CSS.
- **Database:** PostgreSQL 15.
- **Messaging:** Apache Kafka + Zookeeper.
- **Tools:** Docker, Maven, Lombok.

---

## ⚙️ Setup & Installation

### 1. Prerequisites
Ensure you have the following installed on your Mac:
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Java JDK 21+](https://openjdk.org/projects/jdk/21/)
- [Node.js 18+](https://nodejs.org/)
- [Maven](https://maven.apache.org/download.cgi)

### 2. Infrastructure Setup (Docker)
From the root directory, start the database and Kafka cluster:
```bash
docker-compose up -d
```

**Note:** The database is configured to run on port 5433 to avoid local conflicts.

### 3. Backend Setup
Navigate to the backend folder and run the Spring Boot application:
```bash
cd backend
mvn spring-boot:run
```

The server will start on http://localhost:8080.

### 4. Frontend Setup
Navigate to the frontend folder, install dependencies, and start the development server:
```bash
cd frontend
npm install
npm run dev
```

The UI will be available at http://localhost:3000.

---

## 🏗️ Architectural Flow
1. **User Action:** Chef places an order via the React UI.
2. **REST API:** The UI calls the `POST /api/orders` endpoint on the Spring Boot backend.
3. **Database:** The `OrderService` saves the order as `PENDING` in PostgreSQL.
4. **Kafka Producer:** The service broadcasts an `ORDER_PLACED` event to the `orders-topic`.
5. **Kafka Consumer:** An internal `WarehouseConsumer` listens to the topic and logs a shipment preparation notification.

---

## 🧪 Testing the API
You can also test the backend independently using `curl`:
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"itemName": "Fresh Salmon", "quantity": 10}'
```

---

## 🛡️ Git Commands
To push your work to GitHub:

```bash
# 1. Initialize and add files
git init
git add .

# 2. Commit
git commit -m "feat: complete producer-consumer loop with Kafka and React UI"

# 3. Create a branch and link to your GitHub repository
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/sysco-supply-chain.git

# 4. Push
git push -u origin main
```

---

## 📝 License
This project is a technical demonstration and is not licensed for commercial use.

## 👤 Author
Created as a portfolio project.