
# High-Level Design (HLD): Real-Time Chat System

---

## 1. Introduction

This document outlines the **High-Level Design (HLD)** for a scalable, real-time chat system.  
The system supports **web and mobile clients**, provides **instant messaging**, **message history**, **unread counts**, and integrates with external services.  
Key design goals are **scalability**, **reliability**, **low latency**, and **security**.

---

## 2. Architecture Overview

The architecture follows a **microservices** approach with the following major components:

![Real-Time Chat System Architecture](###diagram)  
*(Scroll down for the Mermaid diagram)*

- **Clients**: Web and mobile applications.
- **API Gateway / Load Balancer**: Entry point for all traffic.
- **Chat Service Cluster**: Stateless microservice instances handling business logic.
- **Message Broker**: RabbitMQ for cross-instance communication.
- **Database**: PostgreSQL for persistent storage.
- **STOMP Broker**: Enables real-time push over WebSocket.
- **External Services**: FlairBit (user profiles), caching, and authentication.

---

## 3. System Diagram

```mermaid
flowchart TD
    %% Clients
    subgraph Clients [Clients]
        C1[Web Browser\n(SockJS/STOMP)]
        C2[Mobile App\n(WebSocket)]
    end

    %% API & WebSocket Gateway
    subgraph Gateway [API Gateway / Load Balancer]
        LB[Ingress / Nginx / Cloud LB]
    end

    %% Chat Service Cluster
    subgraph ChatServiceCluster [chat-service (N Instances - Kubernetes)]
        direction TB
        A1[Instance 1]
        A2[Instance 2]
        A3[Instance N]
    end

    %% Internal Components
    subgraph Components [Internal Components per Instance]
        direction TB
        REST[REST Controller\n/api/chat/*]
        WS[WebSocket Config\n/ws]
        RABBIT_CONSUMER[@RabbitListener\nchat.send.queue]
        OUTBOX[OutboxPublisher\n@Scheduled Poller]
        CACHE[Caffeine Cache\nprofileCache]
        FEIGN[FlairBit Feign Client\n+CircuitBreaker]
        SERVICE_AUTH[ServiceAuthClient\nJWT Signer]
    end

    %% External Systems
    subgraph External [External Services]
        FLAIRBIT[FlairBit Service\n/internal/chat-service/...]
        RABBITMQ[RabbitMQ Cluster\namq.topic]
        STOMP_BROKER[STOMP Broker\n(RabbitMQ Relay or Embedded)]
        POSTGRES[PostgreSQL\nchat_sessions\nchat_messages\nchat_message_outbox]
    end

    %% Connections
    C1 -->|HTTPS + JWT| LB
    C2 -->|WSS + JWT| LB

    LB -->|HTTP| A1
    LB -->|HTTP| A2
    LB -->|HTTP| A3
    LB -->|WebSocket| A1
    LB -->|WebSocket| A2
    LB -->|WebSocket| A3

    A1 --> REST
    A2 --> REST
    A3 --> REST
    REST -->|JDBC| POSTGRES

    A1 --> WS
    A2 --> WS
    A3 --> WS
    WS -->|STOMP| STOMP_BROKER
    STOMP_BROKER -->|/topic/session.*| A1
    STOMP_BROKER -->|/user/*/queue/*| A1
    STOMP_BROKER -->|/topic/session.*| A2
    STOMP_BROKER -->|/user/*/queue/*| A2
    STOMP_BROKER -->|/topic/session.*| A3
    STOMP_BROKER -->|/user/*/queue/*| A3

    A1 --> RABBIT_CONSUMER
    A2 --> RABBIT_CONSUMER
    A3 --> RABBIT_CONSUMER
    RABBIT_CONSUMER -->|app/chat.send| RABBITMQ
    RABBITMQ -->|Publish| A1
    RABBITMQ -->|Publish| A2
    RABBITMQ -->|Publish| A3

    A1 --> OUTBOX
    A2 --> OUTBOX
    A3 --> OUTBOX
    OUTBOX -->|claimPendingBatch()| POSTGRES
    OUTBOX -->|convertAndSend()| STOMP_BROKER

    REST --> FEIGN
    FEIGN -->|Bearer JWT| FLAIRBIT
    FEIGN -->|Cached| CACHE
    FEIGN --> SERVICE_AUTH
    SERVICE_AUTH -->|RSA Sign| FEIGN

    %% Styling
    classDef service fill:#4CAF50,color:white,stroke:#388E3C;
    classDef external fill:#2196F3,color:white,stroke:#1976D2;
    classDef client fill:#FF9800,color:white,stroke:#F57C00;
    classDef component fill:#9C27B0,color:white,stroke:#7B1FA2;

    class A1,A2,A3 service
    class FLAIRBIT,RABBITMQ,STOMP_BROKER,POSTGRES external
    class C1,C2 client
    class REST,WS,RABBIT_CONSUMER,OUTBOX,CACHE,FEIGN,SERVICE_AUTH component
```

---

## 4. Component Description

### 4.1 Clients
| Type       | Protocol                | Details                                                                 |
|------------|-------------------------|-------------------------------------------------------------------------|
| Web        | SockJS + STOMP          | Fallback to HTTP long-polling if WebSocket fails.                       |
| Mobile     | WebSocket (WSS)         | Native, fast, low-overhead connection.                                  |
| **Auth**   | JWT (Bearer)            | All requests include a valid JWT signed by `ServiceAuth`.               |

### 4.2 API Gateway / Load Balancer
- **Responsibilities**:
    - TLS termination (HTTPS/WSS).
    - Routing HTTP requests to `chat-service` instances.
    - Routing WebSocket connections to the appropriate instance.
    - Load balancing (Round Robin, Least Connections, etc.).
    - Rate limiting, DDoS protection, and IP whitelisting.

- **Technologies**:  
  `Nginx`, `AWS ALB`, `Google Cloud Load Balancer`, or a Kubernetes Ingress Controller.

### 4.3 Chat Service (`chat-service`)
- **Stateless Microservice** deployed as **N instances** on Kubernetes.
- **Key Sub-components** (see diagram):

| Component               | Purpose                                                                 |
|-------------------------|-------------------------------------------------------------------------|
| **REST Controller**     | Exposes HTTP APIs: `POST /api/chat/send`, `GET /api/chat/history`, `GET /api/chat/unread` |
| **WebSocket Config**    | Manages real-time connections using **STOMP** over WebSocket. Clients subscribe to topics like `/topic/chat/{chatId}`. |
| **RabbitListener**      | Listens to `chat.send.queue` (RabbitMQ) to receive messages published by other instances. Relays them via STOMP. |
| **OutboxPublisher**     | Guarantees message delivery using the **Outbox Pattern**. Periodically polls `chat_message_outbox` and publishes to STOMP & RabbitMQ. |
| **Caffeine Cache**      | Caches user profiles from FlairBit to reduce latency and external calls. |
| **FlairBit Feign Client**| Calls the external FlairBit service to fetch user profiles. Wrapped with a **Circuit Breaker** (Hystrix/Resilience4j). |
| **ServiceAuth Client**  | Generates and validates JWT tokens for internal service communication. |

### 4.4 Message Broker – RabbitMQ
- **Purpose**: Enables **cross-instance communication**.
- **Exchange**: `amq.topic` for flexible routing.
- **Queue**: `chat.send.queue` where instances publish messages they receive, allowing other instances to re-broadcast them.
- **Durable Queues**: Messages survive broker restarts.

### 4.5 STOMP Broker
- **Implementation Options**:
    1. **RabbitMQ with STOMP plugin** (used here).
    2. **Embedded STOMP broker** (e.g., `SockJS` + `BrokerRelayingMessageBroker` in Spring).

- **Key Topics/Queues**:
    - `/topic/chat/{chatId}`: Broadcast to all subscribers of a chat.
    - `/user/queue/notifications`: User-specific queue for private messages.

### 4.6 Database – PostgreSQL
| Table                  | Purpose                                                                 |
|------------------------|-------------------------------------------------------------------------|
| `chat_sessions`        | Stores metadata about chat sessions (e.g., participants, creation time). |
| `chat_messages`        | Persistent message store (message ID, sender, receiver, timestamp, payload, status). |
| `chat_message_outbox`  | **Outbox Pattern**: Holds messages **before** they are broadcast. Ensures atomic write to DB and eventual send. |

#### Outbox Pattern Workflow
1. When a message is received via REST, it is **inserted** into both `chat_messages` and `chat_message_outbox`.
2. `OutboxPublisher` runs periodically (e.g., every 5 seconds) and:
    - Fetches pending entries from `chat_message_outbox`.
    - **Deletes** them from the outbox.
    - Publishes to **STOMP** (real-time) and **RabbitMQ** (cross-instance).

### 4.7 External Services

#### FlairBit Service
- **Role**: Provides **user profile** data (display name, avatar, status).
- **Integration**:
    - Called via **Feign client** with a **circuit breaker**.
    - Results are cached in **Caffeine** to reduce latency and load.

#### ServiceAuth
- **Role**: Manages **JWT signing/verification**.
- **Usage**:  
  `chat-service` uses `ServiceAuthClient` to sign internal requests to FlairBit.

---

## 5. Data Flow – Key Use Cases

### 5.1 Sending a Message

1. **Client** → `POST /api/chat/send` (or via WebSocket).
2. **Chat Service**:
    - Validates request & JWT.
    - Inserts message into `chat_messages` **and** `chat_message_outbox`.
    - Returns ACK to client.
3. **OutboxPublisher** (same instance):
    - Detects new row in `chat_message_outbox`.
    - Removes row.
    - Publishes message to **STOMP Broker** (`/topic/chat/{chatId}`).
    - Publishes same message to **RabbitMQ** (`app/chat.send`).
4. **Other Instances** (via RabbitMQ):
    - Consume message from `app/chat.send` queue.
    - Re-publish to their **STOMP Broker**.
5. All connected clients subscribed to the topic receive the message **in real-time**.

### 5.2 Receiving Real-Time Updates

1. Client subscribes to `/topic/chat/{chatId}` via STOMP.
2. Whenever a message is published to that topic (by any instance), the **STOMP Broker** pushes it to all subscribed clients.

### 5.3 Fetching Message History

1. **Client** → `GET /api/chat/history?chatId={id}&page=1&size=50`.
2. **Chat Service** queries `chat_messages` and returns paginated results.

### 5.4 Unread Count

1. **Client** → `GET /api/chat/unread?chatId={id}`.
2. **Chat Service** counts rows in `chat_messages` where `receiver = user` and `status = UNREAD`.

---

## 6. Scalability & Performance

| Layer                | Scalability Strategy                                                                 |
|----------------------|--------------------------------------------------------------------------------------|
| **Clients**          | Unlimited – each client connects independently.                                       |
| **Load Balancer**    | Horizontal scaling; supports millions of connections.                                 |
| **Chat Service**     | **Stateless** → add more Kubernetes pods. Autoscaling based on CPU/RabbitMQ queue depth. |
| **RabbitMQ**         | Clustered deployment for high availability and sharding.                              |
| **PostgreSQL**       | Read replicas for heavy read loads. Partitioning for large tables.                   |
| **Caching**          | Caffeine reduces DB & external service load.                                          |

---

## 7. Fault Tolerance & Reliability

| Concern                     | Solution                                                                             |
|-----------------------------|--------------------------------------------------------------------------------------|
| **Message Loss**            | **Outbox Pattern** + **Durable RabbitMQ queues**.                                     |
| **Node Failure**            | Stateless services + Kubernetes self-healing.                                        |
| **RabbitMQ Down**           | Retry with backoff in `RabbitListener`. Fallback to STOMP only.                      |
| **Database Down**           | Connection pooling with retry. OutboxPublisher skips until DB is back.               |
| **External Service Failure**| **Circuit Breaker** on FlairBit client; fallback to cached data.                     |

---

## 8. Security

| Aspect              | Implementation                                                                 |
|---------------------|---------------------------------------------------------------------------------|
| **Transport**       | TLS (HTTPS/WSS) enforced by Load Balancer.                                      |
| **Authentication**  | JWT (Bearer) for both client and service-to-service communication.               |
| **Authorization**   | `ServiceAuth` validates JWT claims; fine-grained access control in `chat-service`. |
| **Data Integrity**  | All messages are stored with a checksum (optional).                              |

---

## 9. Technology Stack

| Layer          | Technology                                                                 |
|----------------|----------------------------------------------------------------------------|
| **Client**     | Web: SockJS, STOMP.js; Mobile: WebSocket                                   |
| **API Gateway**| Nginx / AWS ALB / Kubernetes Ingress                                       |
| **Chat Service**| Spring Boot (Java 17), Kotlin Coroutines (alternative)                     |
| **Messaging**  | RabbitMQ (STOMP plugin)                                                    |
| **Database**   | PostgreSQL 14+                                                             |
| **Cache**      | Caffeine                                                                   |
| **External**   | OpenFeign + Resilience4j (Circuit Breaker)                                 |
| **Auth**       | JWT, Spring Security                                                       |
| **Container**  | Docker, Kubernetes                                                         |
| **CI/CD**      | GitHub Actions / Jenkins / ArgoCD                                          |

---

## 10. Deployment

- **Docker Containers** for each component.
- **Kubernetes** manages:
    - Deployments (chat-service pods).
    - Services (internal DNS for discovery).
    - Horizontal Pod Autoscaler (HPA).
    - Ingress for external access.
- **Helm Charts** for easy provisioning.

---

## 11. Monitoring & Observability

| Area          | Tool                                                                     |
|---------------|--------------------------------------------------------------------------|
| **Metrics**   | Prometheus + Grafana                                                     |
| **Logging**   | ELK Stack (Elasticsearch, Logstash, Kibana) or Loki                       |
| **Tracing**   | Jaeger / Zipkin                                                          |
| **Health Checks** | Kubernetes liveness/readiness probes, RabbitMQ & DB health endpoints. |

---

## 12. Future Enhancements

1. **Message Edit/Delete** – Add support to modify or remove messages after sending.
2. **End-to-End Encryption** – For private chats.
3. **File/Image Sharing** – Extend message payload to support binary data.
4. **Presence & Typing Indicators** – Use additional STOMP topics.
5. **Push Notifications** – For mobile apps when the app is in the background.

---

### Diagram Explanation

| Component | Role |
|---------|------|
| **Clients** | Web & Mobile via WebSocket (SockJS fallback) |
| **Load Balancer** | Routes HTTP + WebSocket, terminates TLS |
| **chat-service (N)** | Stateless, horizontally scalable |
| **REST API** | Message send, history, unread |
| **WebSocket** | Real-time push via STOMP |
| **RabbitMQ** | Cross-instance message relay |
| **OutboxPublisher** | Guarantees STOMP delivery |
| **PostgreSQL** | Source of truth |
| **FlairBit** | External profile service (cached) |
| **Caffeine** | Hot profile cache |

---