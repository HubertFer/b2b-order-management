# B2B Order Management

Microservice for managing B2B orders: creation, lookup, status lifecycle, partner credit control and status change notifications.

## Tech stack

- Java 21, Spring Boot 4.1, Maven
- PostgreSQL 17, Spring Data JPA, Flyway
- springdoc-openapi (Swagger UI)
- JUnit 5, MockMvc and Testcontainers
- Docker Compose

## Running

Requirement: Docker.

```bash
docker compose up --build
```

Compose starts PostgreSQL and the application at `http://localhost:8080`.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI (JSON): http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health, with `/liveness` and `/readiness` probes

The `app` service has a Docker healthcheck based on the readiness probe, so `docker compose up --wait` returns only when the API is ready to serve requests.

The Compose environment seeds two demo partners (`db/demo/R__demo_partners.sql`):

| Partner | Credit limit |
|---|---|
| `11111111-1111-1111-1111-111111111111` | 10,000.00 |
| `22222222-2222-2222-2222-222222222222` | 500.00 |

This data is only loaded when `SPRING_FLYWAY_LOCATIONS` includes `classpath:db/demo`, as in `compose.yaml`. The versioned migrations in `db/migration` contain no data. New partners can be registered with `POST /partners`.

To stop and remove all data: `docker compose down -v`.

### Running the application outside a container

Requirements: Java 21 and Docker (only for the database). Useful for development and debugging.

**With a temporary database (Testcontainers):**

```bash
./mvnw spring-boot:test-run
```

A throwaway PostgreSQL container is started and removed automatically. It starts empty, without demo partners, so register partners with `POST /partners`.

**With the Compose database:**

```bash
docker compose up -d --wait postgres

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/orders \
SPRING_DATASOURCE_USERNAME=orders \
SPRING_DATASOURCE_PASSWORD=orders \
SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo \
./mvnw spring-boot:run
```

Keep `SPRING_FLYWAY_LOCATIONS` the same as in `compose.yaml`: once the demo partners are loaded into the database, Flyway refuses to start without the `db/demo` location.

### Tests

```bash
./mvnw test
```

Integration tests start PostgreSQL with Testcontainers, so Docker must be running.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/partners` | Registers a partner with a credit limit |
| `GET` | `/partners/{id}` | Gets a partner with its credit limit, used credit and available credit |
| `POST` | `/orders` | Creates an order (status `PENDENTE`); accepts an optional `Idempotency-Key` header |
| `GET` | `/orders/{id}` | Gets an order |
| `GET` | `/orders?status=&createdFrom=&createdTo=&page=&size=` | Paginated search by status and creation period |
| `POST` | `/orders/{id}/approve` | Approves the order and debits the partner's credit |
| `PATCH` | `/orders/{id}/status` | Moves the order to `EM_PROCESSAMENTO`, `ENVIADO` or `ENTREGUE` |
| `POST` | `/orders/{id}/cancel` | Cancels the order and refunds the credit if it was debited |

Errors use the `ProblemDetail` format (RFC 9457):

| Status | When |
|---|---|
| 400 | Invalid body or parameters |
| 404 | Order or partner not found |
| 409 | Status transition not allowed |
| 422 | Partner not found, without available credit, or `Idempotency-Key` reused with a different request |

### Example

```bash
curl -X POST http://localhost:8080/partners \
  -H 'Content-Type: application/json' -d '{"creditLimit": 500.00}'

curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f3c2a90-order-001' \
  -d '{
        "partnerId": "{partnerId}",
        "items": [
          {"productId": "33333333-3333-3333-3333-333333333333", "quantity": 3, "unitPrice": 100.00}
        ]
      }'

curl -X POST http://localhost:8080/orders/{id}/approve
curl -X PATCH http://localhost:8080/orders/{id}/status \
  -H 'Content-Type: application/json' -d '{"status": "EM_PROCESSAMENTO"}'
curl -X POST http://localhost:8080/orders/{id}/cancel
curl 'http://localhost:8080/orders?status=CANCELADO&createdFrom=2026-01-01T00:00:00Z'
curl http://localhost:8080/partners/{partnerId}
```

## Business rules

### Statuses

Status values are kept in Portuguese, as defined in the challenge: `PENDENTE` (pending), `APROVADO` (approved), `EM_PROCESSAMENTO` (processing), `ENVIADO` (shipped), `ENTREGUE` (delivered) and `CANCELADO` (cancelled).

```
PENDENTE ──► APROVADO ──► EM_PROCESSAMENTO ──► ENVIADO ──► ENTREGUE
   │            │                │
   └────────────┴────────────────┴──► CANCELADO
```

`ENTREGUE` and `CANCELADO` are final. Any other transition returns 409.

### Credit

- **Creation:** checks that the available credit (`limit - used`) covers the order total. Nothing is debited.
- **Approval:** debits the order total from the partner's credit.
- **Cancellation:** if the order had already been approved (`APROVADO` or `EM_PROCESSAMENTO`), the total is returned to the partner's available credit. Cancelling a `PENDENTE` order does not change the credit.

`APROVADO` and `CANCELADO` can only be reached through their own endpoints, because they change the partner's credit. `PATCH /status` only accepts the fulfillment statuses.

### Concurrency

Approval must prevent negative balances and double approvals, even under concurrent requests:

1. The order is read with `SELECT ... FOR UPDATE`. Approvals and cancellations of the same order are serialized, so a second request sees the new status and is rejected with 409.
2. The debit is a single conditional `UPDATE`:
   ```sql
   update partner set used_credit = used_credit + :amount
    where id = :id and credit_limit - used_credit >= :amount
   ```
   If no row is updated, there is not enough credit and the whole transaction is rolled back. Different orders from the same partner cannot exceed the limit without needing a lock on the partner row.
3. A `CHECK` constraint (`used_credit >= 0 AND used_credit <= credit_limit`) is the final guarantee in the database.

Refunds follow the same pattern, with `used_credit >= :amount` as the condition.

`ApproveOrderConcurrencyTest` runs parallel approvals, cancellations and idempotent creations against a real PostgreSQL instance.

### Idempotent order creation

Clients retrying a `POST /orders` after a timeout must not create duplicate orders. Sending an `Idempotency-Key` header (1 to 100 printable ASCII characters, unique per partner) makes the request safe to retry:

- **First request:** the order is created and the response is `201`.
- **Same key and same request:** the order created by the first request is returned with `200`, even if the partner no longer has available credit.
- **Same key with a different request:** `422`.

The key is stored in `order_idempotency_key` with `INSERT ... ON CONFLICT DO NOTHING`, in the same transaction as the order. When two requests with the same key arrive at the same time, the second one waits for the first to commit and then returns the same order. Requests are compared by a SHA-256 fingerprint of the partner and items. Requests without the header behave as before.

### Notifications (Outbox)

Every status change writes an `ORDER_STATUS_CHANGED` event to the `outbox_event` table **in the same transaction** that changes the order. If the operation fails, no event is written; once it commits, the event cannot be lost.

A scheduled job (`OutboxRelay`) reads pending events in order, using `FOR UPDATE SKIP LOCKED` so several instances can run at the same time, publishes them and sets `published_at`. If publishing fails, the batch stops at that event and it is retried on the next run. Delivery is *at-least-once*; consumers can use the `eventId` in the payload to discard duplicates.

Messaging is simulated by `LoggingNotificationPublisher`, which logs each event:

```
Notification sent: eventType=ORDER_STATUS_CHANGED aggregateType=ORDER aggregateId=... payload={"eventId": "...", "orderId": "...", "partnerId": "...", "previousStatus": "PENDENTE", "newStatus": "APROVADO", "occurredAt": "..."}
```

Using a real broker only requires another `NotificationPublisher` implementation.

| Property | Default | Description |
|---|---|---|
| `notification.outbox.relay-interval` | `PT1S` | Interval between relay runs |
| `notification.outbox.batch-size` | `50` | Events published per run |

## Runtime configuration

| Property | Value | Why |
|---|---|---|
| `spring.threads.virtual.enabled` | `true` | Requests and background tasks run on Java 21 virtual threads, so threads blocked on database I/O do not limit how many concurrent requests the service accepts |
| `spring.datasource.hikari.maximum-pool-size` | `20` | Upper bound on database connections per instance. With virtual threads this pool becomes the real concurrency limit for database work |
| `spring.datasource.hikari.minimum-idle` | `5` | Keeps connections ready for traffic spikes |
| `spring.datasource.hikari.connection-timeout` | `3000` (ms) | Fails fast under overload instead of piling up waiting requests |
| `management.endpoints.web.exposure.include` | `health` | Only the health endpoint is exposed over HTTP |

Every property can be overridden with an environment variable, for example `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=40`.

## Scalability

The service is designed to run as several identical instances behind a load balancer, with PostgreSQL as the single source of truth.

- **Stateless instances.** No session or in-memory state is shared between requests. Any instance can serve any request, and the readiness probe tells the load balancer when an instance can receive traffic.
- **Fine-grained locking.** There is no global lock. Approvals and cancellations lock only the order being changed, and the partner credit is updated by a single conditional `UPDATE`, which holds the partner row only until the short approval transaction commits. Orders from different partners never wait for each other.
- **Safe retries.** `Idempotency-Key` lets clients and gateways retry `POST /orders` after timeouts without creating duplicates, even when the retries land on different instances.
- **Outbox across instances.** Every instance runs the outbox relay. `FOR UPDATE SKIP LOCKED` lets them split pending events instead of publishing the same event twice or waiting for each other.
- **Efficient reads.** Searches are paginated (maximum page size of 100) and backed by indexes on `status`, `created_at` and `partner_id`. Order items of a page are loaded in batches instead of one query per order. Read endpoints use read-only transactions, and `open-in-view` is disabled so connections are released as soon as the transaction ends.
- **Bounded resources.** Virtual threads accept many concurrent requests cheaply, while the connection pool caps the load each instance puts on the database. The short connection timeout turns overload into fast errors instead of growing queues.

### Limits and next steps

- **Database connections.** The number of instances times `maximum-pool-size` must stay below PostgreSQL's `max_connections` (100 by default). Beyond that, a connection pooler such as PgBouncer is the next step.
- **Hot partners.** Approvals for the same partner serialize on that partner's row. This is the price of never exceeding the credit limit; each approval holds the row only for a few milliseconds.
- **Read scaling.** Searches could be moved to a read replica if read traffic outgrows the primary database.
- **Real messaging.** Replacing `LoggingNotificationPublisher` with a Kafka or RabbitMQ publisher keeps the same delivery guarantees, since the outbox already decouples the order transaction from the broker.

## Architecture

Modular monolith organized by feature, with layers inside each module:

```
order/
  api/             controller, DTOs and error handling
  application/     use cases and outbound ports (port/out)
  domain/          Order, OrderItem, OrderStatus, transitions and events
  infrastructure/  JPA persistence, outbox and use case wiring
partner/
  api/             controller, DTOs and error handling
  application/     use cases and outbound ports (port/out)
  domain/          Partner
  infrastructure/  persistence, atomic credit operations and use case wiring
notification/
  outbox/          relay and storage/publishing contracts
  infrastructure/  JDBC storage, scheduling and simulated publisher
```

- The domain is immutable and does not depend on Spring. Transitions return a new `Order` instance.
- Use cases depend only on ports. Infrastructure adapters implement those ports, and the services are registered in `OrderUseCaseConfiguration`, with no Spring stereotype annotations in the application layer.
- The `order` module references partners by id only, with no JPA relationship between the entities.

## Decisions and limitations

- **Partner management** is limited to registration and balance lookup. The credit limit cannot be changed after registration.
- **Order creation** does not trigger a notification, since it is not a status change.
- **Idempotency keys** are kept indefinitely. A production setup would delete them after a retention period, such as 24 hours.
- **Authentication** is not implemented.
- **Swagger UI** is enabled by default. In production it can be disabled with `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`.
