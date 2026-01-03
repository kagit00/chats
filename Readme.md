

# Real-Time Chat System

A robust and scalable real-time chat system **based on an implemented reference design** using Spring Boot, WebSocket/STOMP, and PostgreSQL. It focuses on architectural patterns for reliability, low latency, and production-readiness considerations.

---

## Key Features

- **Real-Time Messaging**: Design for instant message delivery using WebSockets (STOMP).
- **Message History**: Paginated fetching of past conversations.
- **Delivery & Read Receipts**: Data model to track when messages are delivered and seen by recipients.
- **Unread Message Count**: Endpoint logic to fetch the number of unread messages for a user.
- **Cross-Instance Scalability**: Designed to use RabbitMQ to relay messages between multiple application instances.
- **Guaranteed Delivery**: Implemented **Outbox Pattern** to ensure no messages are lost during failures.
- **Secure**: JWT-based authentication design for both client requests and inter-service communication.

---

## Architecture Overview

The system is composed of several interconnected components designed to work together to provide a seamless chat experience.

```mermaid
graph TB
    subgraph Client
        A[Mobile App]
        B[Web Client]
    end

    subgraph "Chat Service (Monolith)"
        C[API Layer]
        D[Chat Logic]
        E[Outbox Publisher]
        F[WebSocket Handler]
    end

    subgraph "Infrastructure"
        G[(PostgreSQL)]
        H[(Redis Cache)]
        I[RabbitMQ]
        J[Profile Service]
    end

    A -- HTTPS/WS --> C
    B -- HTTPS/WS --> C
    C --> D
    D --> E
    D --> F
    D --> G
    D --> H
    D --> I
    I --> F
    D --> J
```

**How it Works:**
1.  A client sends a message via a REST API.
2.  The **Chat Logic** validates and persists the message to **PostgreSQL**.
3.  It also creates an entry in an **Outbox** table.
4.  The **Outbox Publisher** (a background worker) picks up the pending entry and broadcasts it through **RabbitMQ**.
5.  **RabbitMQ** ensures the message reaches the correct **WebSocket Handler**, which then pushes it to the recipient's client in real-time.

---

## Core Components

| Component              | Responsibility                                                                                                     |
|------------------------|-------------------------------------------------------------------------------------------------------------------|
| **Chat Service**       | The central hub handling all chat-related business logic, API endpoints, and database interactions.                |
| **Profile Service**    | A separate service (or module) designed to fetch user profile information, with caching for performance.          |
| **Outbox Publisher**   | A reliable, scheduled background worker designed to guarantee message broadcasting to WebSocket clients.           |
| **WebSocket/STOMP**     | Manages persistent connections and uses topics/queues for targeted message delivery to users.                    |
| **RabbitMQ**           | A message broker designed to act as the distribution layer for messages across different service instances.       |
| **PostgreSQL**         | The primary database for persisting chat sessions, messages, and the outbox for reliability.                    |

---

## 🛠️ Technology Stack

| Category               | Technologies                                                              |
|------------------------|---------------------------------------------------------------------------|
| **Backend**            | Java 17, Spring Boot, Spring Security, Spring WebSocket                   |
| **Database**           | PostgreSQL                                                                |
| **Cache**              | Redis (for profile data)                                                 |
| **Message Broker**     | RabbitMQ                                                                  |
| **Inter-Comm**         | REST (Feign Client), STOMP, RabbitMQ                                      |
| **Security**           | JWT (RSA-signed)                                                          |
| **Build & Tooling**    | Docker, Maven                                                             |

---

## 📨 The Life of a Message

1.  **Send**: A user sends a message via `POST /api/chat/message`.
2.  **Persist**: The `ChatService` saves the message to the `chat_messages` table.
3.  **Outbox**: Simultaneously, it writes a `ChatMessageOutbox` entry, containing the destination and payload.
4.  **Publish**: A scheduled `OutboxPublisher` claims unprocessed outbox entries and publishes them to a RabbitMQ exchange.
5.  **Relay & Broadcast**: The message is picked up by the `ChatMessageRelayListener` (or directly by the WebSocket broker) and broadcasted to the recipient's STOMP destination (e.g., `/topic/session.{sessionId}`).
6.  **Receive**: The recipient's connected client receives the message in real-time and updates the UI.
7.  **Acknowledge**: The recipient's client sends read/delivery status, which updates the original message in the database.

---

## Security

- **Client Authentication**: API and WebSocket connections are designed to be protected by JWTs issued by a central authentication service.
- **Inter-Service Authentication**: Service-to-service calls (e.g., to the Profile Service) are secured using short-lived, RSA-signed JWTs.
- **Authorization**: The system logic ensures users can only access their own chat sessions and messages.

---

## Design Characteristics (Intended)

*Note: This section describes the architectural goals and design patterns implemented, rather than validated production metrics.*

- **Guaranteed Delivery**: The **Outbox Pattern** decouples message persistence from message broadcasting. If the WebSocket broker is down, the message remains in the outbox and is retried until successful.
- **Horizontal Scaling**: The stateless nature of the services allows for running multiple instances behind a load balancer. RabbitMQ is designed to ensure messages are routed correctly between instances.
- **Automatic Retries**: Failed operations (like outbox publishing) include logic for retrying with exponential backoff.
- **Caching**: User profiles are designed to be cached in Redis to reduce latency and load on the Profile Service.

---

## Getting Started (Local Development)

### Prerequisites
- Java 17+
- Docker & Docker Compose
- A running PostgreSQL, Redis, and RabbitMQ instance (Docker Compose recommended).

### Setup

1.  **Clone the repository**:
    ```bash
    git clone <repository-url>
    cd chat-service
    ```

2.  **Start Dependencies**:
    ```bash
    docker-compose up -d postgres redis rabbitmq
    ```

3.  **Run the Application**:
    ```bash
    ./mvnw spring-boot:run
    ```

The service will be available at `http://localhost:8080`.

---

## API Summary

| Method | Endpoint                               | Description                                |
|--------|----------------------------------------|--------------------------------------------|
| `POST` | `/api/chat/init`                      | Initializes or retrieves a chat session.   |
| `POST` | `/api/chat/message`                   | Sends a new message.                       |
| `GET`  | `/api/chat/history/{sessionId}`       | Fetches paginated history for a session.   |
| `GET`  | `/api/chat/unread/{sessionId}/{email}` | Gets unread messages for a user in a chat. |
| `POST` | `/ws`                                  | WebSocket endpoint for STOMP connections.  |

---