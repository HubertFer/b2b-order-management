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

The Compose environment seeds two demo partners (`db/demo/R__demo_partners.sql`):

| Partner | Credit limit |
|---|---|
| `11111111-1111-1111-1111-111111111111` | 10,000.00 |
| `22222222-2222-2222-2222-222222222222` | 500.00 |

This data is only loaded when `SPRING_FLYWAY_LOCATIONS` includes `classpath:db/demo`, as in `compose.yaml`. The versioned migrations in `db/migration` contain no data. New partners can be registered with `POST /partners`.

To stop and remove all data: `docker compose down -v`.

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
