# High-Level Design (HLD): Real-Time Chat System

*Based on a reference implementation*

## 1. Executive Summary

This document presents the high-level design of a real-time chat system based on a reference implementation using Spring Boot, WebSocket/STOMP, and PostgreSQL. The system facilitates real-time messaging between users with different intents (dating, freelance, rideshare, etc.) and integrates with an external FlairBit service for user profile management and authentication.

## 2. System Architecture Overview

```mermaid
flowchart TD
  %% Clients
  WEB[Web Browser<br/>SockJS / STOMP client]
  MOBILE[Mobile App<br/>WebSocket client]

  %% API & Gateway
  LB[Load Balancer / Ingress<br/>TLS termination]
  REST_API[REST API<br/>/api/chat/*]
  WS_ENDPOINT[WebSocket Endpoint<br/>/ws STOMP]

  %% Core services (flat list)
  CHAT_SERVICE[ChatService<br/>message processing]
  SESSION_SERVICE[ChatSessionService<br/>session management]
  PROFILE_SERVICE[ProfileService<br/>profile caching]
  OUTBOX_PUB[OutboxPublisher<br/>scheduled poller]
  RELAY_LISTENER[ChatRelayListener<br/>RabbitListener]
  SERVICE_AUTH[ServiceAuthClient<br/>JWT signer]
  CAFFEINE[Caffeine Cache<br/>profile cache]

  %% Messaging & DB
  RABBITMQ[RabbitMQ<br/>amq.topic / chat.send.queue]
  STOMP_BROKER[STOMP Broker<br/>relay or embedded]
  POSTGRES[(PostgreSQL<br/>chat_sessions<br/>chat_messages<br/>chat_message_outbox)]

  %% External
  FLAIRBIT[FlairBit Service<br/>user profiles]

  %% Connections
  WEB -->|HTTPS + JWT| LB
  MOBILE -->|WSS + JWT| LB

  LB --> REST_API
  LB --> WS_ENDPOINT

  REST_API --> CHAT_SERVICE
  WS_ENDPOINT --> STOMP_BROKER

  CHAT_SERVICE --> SESSION_SERVICE
  CHAT_SERVICE --> PROFILE_SERVICE
  SESSION_SERVICE --> PROFILE_SERVICE

  PROFILE_SERVICE --> CAFFEINE
  PROFILE_SERVICE --> FLAIRBIT
  SERVICE_AUTH --> FLAIRBIT

  CHAT_SERVICE --> POSTGRES
  OUTBOX_PUB --> POSTGRES

  RELAY_LISTENER --> RABBITMQ
  RABBITMQ --> CHAT_SERVICE
  OUTBOX_PUB --> STOMP_BROKER
  STOMP_BROKER --> WS_ENDPOINT

  %% Styling (safe)
  classDef clients fill:#FF9800,color:white,stroke:#F57C00;
  classDef services fill:#4CAF50,color:white,stroke:#388E3C;
  classDef external fill:#2196F3,color:white,stroke:#1976D2;
  classDef data fill:#9C27B0,color:white,stroke:#7B1FA2;

  class WEB,MOBILE clients;
  class REST_API,WS_ENDPOINT,CHAT_SERVICE,SESSION_SERVICE,PROFILE_SERVICE,OUTBOX_PUB,RELAY_LISTENER,SERVICE_AUTH,CAFFEINE services;
  class FLAIRBIT external;
  class POSTGRES,RABBITMQ,STOMP_BROKER data;

```

## 3. Current Implementation Components

### 3.1 Core Components

| Component | Implementation | Purpose |
|-----------|---------------|---------|
| **ChatController** | REST API | Handles HTTP requests for chat operations |
| **ChatServiceImpl** | Service Layer | Core business logic for message processing |
| **ChatSessionService** | Service Layer | Manages chat session creation and retrieval |
| **ProfileService** | Service Layer | Caches and retrieves user profiles from FlairBit |
| **OutboxPublisher** | Scheduled Task | Ensures reliable message delivery via outbox pattern |
| **ChatMessageRelayListener** | RabbitMQ Listener | Processes messages from RabbitMQ queue |

### 3.2 Configuration Components

| Configuration | Purpose |
|--------------|---------|
| **WebSocketConfig** | Configures STOMP over WebSocket with SockJS fallback |
| **RabbitConfig** | Sets up RabbitMQ queues, exchanges, and bindings |
| **SecurityConfig** | JWT-based authentication and authorization |
| **CaffeineConfig** | In-memory caching for profiles and users |
| **FeignConfig** | HTTP client configuration for FlairBit integration |

## 4. Data Flow Architecture

### 4.1 Message Send Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as REST API
    participant Service as ChatService
    participant DB as PostgreSQL
    participant Outbox as OutboxPublisher
    participant STOMP as STOMP Broker
    participant Recipient
    
    Client->>+API: POST /api/chat/message
    API->>+Service: sendMessage()
    
    Service->>Service: Check idempotency<br/>(client_msg_id)
    Service->>Service: Validate session
    Service->>Service: Get sender profile<br/>(cached)
    
    Service->>+DB: BEGIN TRANSACTION
    Service->>DB: INSERT chat_messages
    Service->>DB: INSERT chat_message_outbox<br/>(3 entries)
    Service->>DB: COMMIT
    DB-->>-Service: Success
    
    Service-->>-API: ChatMessage
    API-->>-Client: 200 OK
    
    Note over Outbox: Scheduled poller
    
    Outbox->>+DB: Claim pending messages<br/>FOR UPDATE SKIP LOCKED
    DB-->>-Outbox: Batch of messages
    
    loop For each message
        Outbox->>STOMP: convertAndSend()
        STOMP->>Recipient: Push message
        Outbox->>DB: Mark as processed
    end
```

### 4.2 Session Initialization Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as ChatController
    participant SessionSvc as ChatSessionService
    participant ProfileSvc as ProfileService
    participant Cache as Caffeine Cache
    participant FlairBit
    participant DB as PostgreSQL
    
    Client->>+API: POST /api/chat/init
    API->>+SessionSvc: getOrCreateSession()
    
    SessionSvc->>+ProfileSvc: getByEmail(fromEmail)
    ProfileSvc->>Cache: Check cache
    
    alt Cache Miss
        ProfileSvc->>+FlairBit: GET /internal/chat-service/users/{email}/profile/{intent}
        Note over FlairBit: JWT signed by ServiceAuthClient
        FlairBit-->>-ProfileSvc: ProfileChatDto
        ProfileSvc->>Cache: Store in cache
    end
    
    ProfileSvc-->>-SessionSvc: Profile A
    
    SessionSvc->>ProfileSvc: getByEmail(toEmail)
    ProfileSvc-->>SessionSvc: Profile B
    
    SessionSvc->>+DB: getOrCreate session<br/>(canonical ordering)
    DB-->>-SessionSvc: ChatSession
    
    SessionSvc-->>-API: ChatSession
    API-->>-Client: ChatSessionResponse
```

## 5. Database Schema

```mermaid
erDiagram
    chat_sessions {
        uuid id PK
        uuid sender_profile_id "Canonical first profile"
        uuid receiver_profile_id "Canonical second profile"
        varchar(50) intent "dating/freelance/rideshare"
        timestamp created_at
        timestamp updated_at
    }
    
    chat_messages {
        uuid id PK
        uuid session_id FK
        uuid sender_profile_id "Actual sender"
        text content
        uuid client_msg_id UK "Idempotency key"
        boolean delivered
        boolean seen
        timestamp sent_at
        timestamp updated_at
    }
    
    chat_message_outbox {
        uuid id PK
        jsonb payload "ChatMessageResponse JSON"
        varchar(255) destination "STOMP destination"
        boolean processed
        timestamp created_at
        timestamp sent_at
        timestamp next_retry_at
        int retry_count
    }
    
    chat_sessions ||--o{ chat_messages : contains
    chat_messages ||--o| chat_message_outbox : "triggers (3 entries per message)"
```

## 6. Security Architecture

### 6.1 Authentication Flow

```mermaid
flowchart LR
    subgraph "Client"
        JWT[JWT Token<br/>from FlairBit]
    end
    
    subgraph "Chat Service"
        AUTH_FILTER[AuthFilter]
        VERIFIER[FlairbitTokenVerifier]
        SECURITY_CTX[Security Context]
    end
    
    subgraph "Service-to-Service"
        SERVICE_AUTH[ServiceAuthClient]
        PRIVATE_KEY[RSA Private Key]
    end
    
    JWT -->|Bearer Token| AUTH_FILTER
    AUTH_FILTER --> VERIFIER
    VERIFIER -->|RSA Public Key| VERIFIER
    VERIFIER --> SECURITY_CTX
    
    SERVICE_AUTH --> PRIVATE_KEY
    SERVICE_AUTH -->|Sign JWT| SERVICE_AUTH
```

### 6.2 Security Implementation

- **User Authentication**: JWT tokens issued by FlairBit, verified using RSA public key
- **Service Authentication**: Inter-service calls use JWT signed with service's RSA private key
- **Authorization**: Spring Security with method-level security (`@EnableMethodSecurity`)
- **WebSocket Security**: JWT validation on connection establishment

## 7. Messaging Architecture

### 7.1 RabbitMQ Configuration

```mermaid
graph TD
    subgraph "RabbitMQ Broker"
        EXCHANGE[amq.topic<br/>Topic Exchange]
        QUEUE[chat.send.queue]
        BINDING[Binding<br/>app/chat.send]
    end
    
    subgraph "Publishers"
        SERVICE1[Chat Service<br/>Instance 1]
        SERVICE2[Chat Service<br/>Instance 2]
    end
    
    subgraph "Consumers"
        LISTENER1[ChatMessageRelayListener<br/>Instance 1]
        LISTENER2[ChatMessageRelayListener<br/>Instance 2]
    end
    
    SERVICE1 -->|Publish| EXCHANGE
    SERVICE2 -->|Publish| EXCHANGE
    EXCHANGE -->|Route| QUEUE
    QUEUE -->|Consume| LISTENER1
    QUEUE -->|Consume| LISTENER2
```

### 7.2 STOMP/WebSocket Configuration

- **Broker Type**: Configurable (Embedded or External Relay)
- **Endpoints**: `/ws` with SockJS fallback
- **Message Prefixes**:
  - Application destination: `/app`
  - User destination: `/user`
  - Broker destination: `/topic`, `/queue`

## 8. Caching Strategy

```mermaid
graph TD
    subgraph "Caffeine Cache Configuration"
        PROFILE_CACHE[Profile Cache]
        USER_CACHE[User Cache]
    end
    
    REQUEST[Profile Request] --> CHECK{Cache Hit?}
    CHECK -->|Yes| RETURN[Return Cached]
    CHECK -->|No| FLAIRBIT[Call FlairBit]
    FLAIRBIT --> STORE[Store in Cache]
    STORE --> RETURN
```

## 9. Resilience Patterns

### 9.1 Outbox Pattern

```mermaid
stateDiagram-v2
    [*] --> Created: Message sent
    Created --> Pending: Saved to outbox
    Pending --> Processing: Claimed by scheduler
    Processing --> Delivered: Success
    Processing --> Retry: Failure
    Retry --> Processing: Exponential backoff
    Retry --> Failed: Max retries
    Delivered --> [*]
```

### 9.2 Circuit Breaker

- **Implementation**: Resilience4j Circuit Breaker for FlairBit client
- **Configuration**: Applied with standard failure rate threshold and sliding window

## 10. API Specification

### 10.1 REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat/init` | Initialize chat session |
| POST | `/api/chat/message` | Send a message |
| GET | `/api/chat/{sessionId}/history` | Get message history |
| GET | `/api/chat/{sessionId}/unread` | Get unread messages |

### 10.2 WebSocket/STOMP Destinations

| Type | Destination | Description |
|------|------------|-------------|
| Subscribe | `/topic/session.{sessionId}` | Receive session messages |
| Subscribe | `/user/queue/messages` | Personal message queue |
| Subscribe | `/user/queue/ack` | Message acknowledgments |
| Subscribe | `/user/queue/error` | Error notifications |
| Send | `/app/chat.send` | Send message via WebSocket |

## 11. Design Characteristics (Intended)

- **Stateless Design**: Chat service instances are designed to be stateless
- **Horizontal Scaling**: Multiple instances can operate concurrently
- **Database Optimization**:
  - Indexes on frequently queried columns
  - Partial index for outbox pending messages
  - FOR UPDATE SKIP LOCKED for concurrent processing
- **Reliability Features**:
  - Idempotency via client message ID
  - Transactional outbox pattern
  - Retry mechanism with exponential backoff
  - Connection resilience via SockJS fallback

## 12. System Strengths

1. **Reliability**: Outbox pattern ensures no message loss
2. **Performance**: Multi-level caching reduces external calls
3. **Security**: Proper JWT validation with RSA keys
4. **Flexibility**: Supports multiple intent types
5. **Maintainability**: Clean separation of concerns

## 13. Potential Improvements

1. **Redis Integration**: Add distributed caching for session state
2. **Metrics**: Implement comprehensive Micrometer metrics
3. **Tracing**: Add distributed tracing with Sleuth/Zipkin
4. **Rate Limiting**: Implement per-user rate limiting
5. **Message Encryption**: Add end-to-end encryption option
6. **Bulk Operations**: Support batch message sending
7. **Presence System**: Real-time online/offline status

