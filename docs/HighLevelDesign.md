---

# High-Level Design (HLD): Real-Time Chat System

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
    subgraph Components [Internal Components]
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

## Diagram Explanation

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

