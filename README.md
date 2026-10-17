# B2B Order Management

[![CI](https://github.com/HubertFer/b2b-order-management/actions/workflows/ci.yml/badge.svg)](https://github.com/HubertFer/b2b-order-management/actions/workflows/ci.yml)

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

If ports `8080` or `5432` are already in use on your machine, choose other host ports:

```bash
APP_PORT=9090 POSTGRES_PORT=15432 docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html. Every request and response field has a description and an example, and the examples work as-is against the Compose environment: "Try it out" on `POST /orders` uses the demo partner `22222222-2222-2222-2222-222222222222`.
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

Integration tests start PostgreSQL with Testcontainers, so Docker must be running. The same suite runs on every push and pull request through GitHub Actions (`.github/workflows/ci.yml`).

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/partners` | Registers a partner with a credit limit |
| `GET` | `/partners/{id}` | Gets a partner with its credit limit, used credit and available credit |
| `POST` | `/orders` | Creates an order (status `PENDENTE`); accepts an optional `Idempotency-Key` header |
| `GET` | `/orders/{id}` | Gets an order |
| `GET` | `/orders?status=&createdFrom=&createdTo=&page=&size=` | Paginated search by status and creation period |
| `POST` | `/orders/{id}/approve` | Approves the order and debits the partner's credit |
| `PATCH` | `/orders/{id}/status` | Moves the order to any status allowed by the lifecycle; `APROVADO` and `CANCELADO` apply the same credit rules as `/approve` and `/cancel` |
| `POST` | `/orders/{id}/cancel` | Cancels the order and refunds the credit if it was debited |

Errors use the `ProblemDetail` format (RFC 9457) with content type `application/problem+json`. Request bodies are strict: a field that is not part of the request, such as `id` or `usedCredit` sent to `POST /partners`, is rejected with `400` instead of being silently ignored. Validation errors also list the fields that failed:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failure",
  "instance": "/orders",
  "errors": [
    {"field": "items[0].quantity", "message": "must be greater than 0"},
    {"field": "items[0].unitPrice", "message": "numeric value out of bounds (<17 digits>.<2 digits> expected)"}
  ]
}
```

| Status | When |
|---|---|
| 400 | Invalid body or parameters |
| 404 | Order or partner not found |
| 409 | Status transition not allowed |
| 422 | Partner not found, without available credit, or `Idempotency-Key` reused with a different request |

### Walkthrough

Every step can also be run from the Swagger UI. The walkthrough uses the demo partner seeded by Compose; to use your own partner, register it in step 1 and use the returned `id` as `partnerId`. Replace `{orderId}` with the `id` returned in step 2.

**1. Register a partner** (or use the demo partner `22222222-2222-2222-2222-222222222222`)

```bash
curl -X POST http://localhost:8080/partners \
  -H 'Content-Type: application/json' \
  -d '{"creditLimit": 1000.00}'
```

`201 Created`

```json
{"id": "8f0b95e7-8c92-43fb-adb0-0a5f13023db8", "creditLimit": 1000.00, "usedCredit": 0.00, "availableCredit": 1000.00}
```

**2. Create an order** (`Idempotency-Key` is optional)

```bash
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f3c2a90-order-001' \
  -d '{
        "partnerId": "22222222-2222-2222-2222-222222222222",
        "items": [
          {"productId": "33333333-3333-3333-3333-333333333333", "quantity": 2, "unitPrice": 100.00}
        ]
      }'
```

`201 Created` (repeating the same request with the same key returns `200 OK` with the same order)

```json
{
  "id": "0a3dff20-b5b1-458b-be17-16bdf4436a95",
  "partnerId": "22222222-2222-2222-2222-222222222222",
  "items": [{"productId": "33333333-3333-3333-3333-333333333333", "quantity": 2, "unitPrice": 100.00}],
  "total": 200.00,
  "status": "PENDENTE",
  "createdAt": "2026-10-17T12:00:00Z",
  "updatedAt": "2026-10-17T12:00:00Z"
}
```

**3. Approve it** (debits the partner's credit)

```bash
curl -X PATCH http://localhost:8080/orders/{orderId}/status \
  -H 'Content-Type: application/json' \
  -d '{"status": "APROVADO"}'
```

`200 OK` with the order in status `APROVADO`. `POST /orders/{orderId}/approve` does the same. The log shows the notification:

```
Notification sent: eventType=ORDER_STATUS_CHANGED ... "previousStatus": "PENDENTE", "newStatus": "APROVADO" ...
```

**4. Check the credit**

```bash
curl http://localhost:8080/partners/22222222-2222-2222-2222-222222222222
```

```json
{"id": "22222222-2222-2222-2222-222222222222", "creditLimit": 500.00, "usedCredit": 200.00, "availableCredit": 300.00}
```

**5. Move it forward or cancel it**

```bash
curl -X PATCH http://localhost:8080/orders/{orderId}/status \
  -H 'Content-Type: application/json' -d '{"status": "EM_PROCESSAMENTO"}'

curl -X POST http://localhost:8080/orders/{orderId}/cancel
```

Cancelling an approved or processing order returns the `200.00` to the partner's available credit.

**6. Search**

```bash
curl 'http://localhost:8080/orders?status=CANCELADO&createdFrom=2026-10-01T00:00:00Z&createdTo=2026-11-01T00:00:00Z&page=0&size=20'
```

```json
{"content": [{"id": "0a3dff20-b5b1-458b-be17-16bdf4436a95", "status": "CANCELADO", "...": "..."}], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1}
```

The demo partner has a 500.00 limit, so it can approve the order above twice; register a partner with a larger limit to run the walkthrough more times.

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

- **Amounts:** prices and credit limits have at most two decimal places; requests with more are rejected with `400` instead of being rounded. Responses always show amounts with two decimal places.
- **Limits:** an order has at most 100 items, each with a quantity of at most 1,000,000, and its total must fit the `NUMERIC(19,2)` columns (17 integer digits). Larger orders are rejected with `400`.
- **Creation:** checks that the available credit (`limit - used`) covers the order total. Nothing is debited.
- **Approval:** debits the order total from the partner's credit.
- **Cancellation:** if the order had already been approved (`APROVADO` or `EM_PROCESSAMENTO`), the total is returned to the partner's available credit. Cancelling a `PENDENTE` order does not change the credit.

`PATCH /status` accepts every status. `APROVADO` and `CANCELADO` are delegated to the same use cases as `POST /approve` and `POST /cancel`, so the credit is debited or refunded the same way whichever endpoint is used.

### Concurrency

Approval must prevent negative balances and double approvals, even under concurrent requests:

1. The order is read with `SELECT ... FOR UPDATE`. Approvals and cancellations of the same order are serialized, so a second request sees the new status and is rejected with 409.
2. The debit is a single conditional `UPDATE`:
   ```sql
   update partner set used_credit = used_credit + :amount
    where id = :id and credit_limit - used_credit >= :amount
   ```
   If no row is updated, there is not enough credit and the whole transaction is rolled back. Because the check and the debit happen in the same statement, concurrent approvals for the same partner cannot exceed the limit, and no `SELECT ... FOR UPDATE` on the partner is needed; PostgreSQL holds the partner row only until the approval transaction commits.
3. A `CHECK` constraint (`used_credit >= 0 AND used_credit <= credit_limit`) is the final guarantee in the database.

Refunds follow the same pattern, with `used_credit >= :amount` as the condition.

`OrderConcurrencyTest` runs parallel approvals, cancellations and idempotent creations against a real PostgreSQL instance.

### Idempotent order creation

Clients retrying a `POST /orders` after a timeout must not create duplicate orders. Sending an `Idempotency-Key` header (1 to 100 printable ASCII characters, unique per partner) makes the request safe to retry:

- **First request:** the order is created and the response is `201`.
- **Same key and same request:** the order created by the first request is returned with `200`, even if the partner no longer has available credit.
- **Same key with a different request:** `422`.

The key is stored in `order_idempotency_key` with `INSERT ... ON CONFLICT DO NOTHING`, in the same transaction as the order. When two requests with the same key arrive at the same time, the second one waits for the first to commit and then returns the same order. Requests are compared by a SHA-256 fingerprint of the partner and items. Requests without the header behave as before.

### Notifications (Outbox)

Every status change writes an `ORDER_STATUS_CHANGED` event to the `outbox_event` table **in the same transaction** that changes the order. If the operation fails, no event is written; once it commits, the event cannot be lost.

A scheduled job (`OutboxRelay`) reads pending events in order, using `FOR UPDATE SKIP LOCKED` so several instances can run at the same time, publishes them and sets `published_at` with a single `UPDATE` per batch. When a batch comes back full, the relay takes the next one right away, each in its own short transaction, until the backlog is empty or `drain-timeout` is reached, so notification throughput keeps up with the order traffic instead of being capped at one batch per interval. If publishing fails, the batch stops at that event and it is retried on the next run. Delivery is *at-least-once*; consumers can use the `eventId` in the payload to discard duplicates. `JdbcOutboxStoreConcurrencyTest` checks that two relays running at the same time lock different events, and that events locked by a failed relay become available again.

Messaging is simulated by `LoggingNotificationPublisher`, which logs each event:

```
Notification sent: eventType=ORDER_STATUS_CHANGED aggregateType=ORDER aggregateId=... payload={"eventId": "...", "orderId": "...", "partnerId": "...", "previousStatus": "PENDENTE", "newStatus": "APROVADO", "occurredAt": "..."}
```

Using a real broker only requires another `NotificationPublisher` implementation.

| Property | Default | Description |
|---|---|---|
| `notification.outbox.relay-interval` | `PT1S` | Pause between relay runs once the backlog is empty |
| `notification.outbox.batch-size` | `50` | Events locked, published and marked per transaction |
| `notification.outbox.drain-timeout` | `PT10S` | Maximum time a single run keeps draining full batches |

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

### Load test

A [k6](https://k6.io) script in `load-test/orders.js` runs as a Compose service, so it needs nothing besides Docker:

```bash
docker compose --profile load-test run --rm k6
docker compose --profile load-test run --rm -e RATE=500 -e DURATION=2m k6
```

It runs three scenarios at the same time:

| Scenario | What it does |
|---|---|
| `order_lifecycle` | `RATE` iterations per second (default 100), each creating an order with an `Idempotency-Key`, retrying the same request, approving the order and reading it back, spread across 50 partners |
| `search` | `RATE / 4` paginated searches per second by status |
| `hot_partner` | 50 concurrent users sending 500 create and approve attempts for a single partner whose credit only covers 100 of them |

The run fails if more than 1% of requests return an unexpected status, if the 95th percentile latency exceeds 500 ms, or if the hot partner ends with its used credit above (or below) its limit.

Results on a 24-core development machine, with the application, PostgreSQL and k6 all running in Docker on the same host:

| Load | Requests | Throughput | p50 / p95 `order_lifecycle` | p50 / p95 `search` | Failed requests | Hot partner | Pending notifications at the end |
|---|---|---|---|---|---|---|---|
| `RATE=100` | 26,166 | ~430 req/s | 4.0 / 8.9 ms | 3.7 / 9.5 ms | 0% | 100 approved, 400 rejected, used credit 1000 of 1000 | 12 of 6,095 |
| `RATE=500` | 124,908 | ~2,060 req/s | 5.8 / 194 ms | 5.0 / 238 ms | 0% | 100 approved, 400 rejected, used credit 1000 of 1000 | 0 of 29,292 |

These numbers come from a single instance with default settings, sharing the machine with the database and k6, and are meant to show behavior under concurrency, not a production benchmark. At `RATE=500` the median stays around 5 ms while the 95th percentile grows with contention for the CPU and the 20 database connections. The last column shows that notifications are published as fast as orders change status: the outbox is empty, or close to it, when the test ends.

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
