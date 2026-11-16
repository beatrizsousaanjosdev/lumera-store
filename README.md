# Lumera Store API

A store backend built with **Java 25, Spring Boot, PostgreSQL, and Apache Kafka**. The project explores modular architecture, business rules, concurrency control, and reliable asynchronous communication through catalog, inventory, and order modules.

A product is saved together with an outbox event. A background publisher sends the event to Kafka, and the inventory consumer initializes its stock without resetting existing quantities on repeated delivery.

## Features

### Catalog

- Create products with a name, description, price, and category.
- Find products by ID and update their details.
- Activate or deactivate products.
- List products with pagination and optional category and active-status filters.
- Record a `ProductCreated` event in the same database transaction as the product.

### Inventory

- Initialize inventory through the API or automatically through Kafka.
- Query total, reserved, and available quantities.
- Add stock, reserve units, release reservations, and confirm reserved stock withdrawal.
- Preserve existing quantities when initialization is repeated.
- Protect stock movements with pessimistic row locking.

### Orders

- Create orders using active catalog products and price/name snapshots.
- Publish `OrderCreated` through the shared transactional outbox.
- Reserve all order items atomically in inventory; duplicate deliveries preserve quantities.
- Publish `StockReservationResult` in the same transaction as the stock decision.
- Request payment after stock is reserved; cancel immediately if the reservation is rejected.
- Complete checkout through an event-driven saga coordinated by the order module.
- Confirm the order after stock withdrawal, or release the reservation after payment refusal.
- Serialize concurrent reservations with an advisory lock per order and consistently ordered inventory row locks.

### Payment

- Simulate approval or refusal with `PAYMENT_SIMULATOR_DECISION=APPROVED|DECLINED`.
- Serialize processing per order and keep one payment per order.
- Persist payment results and outbox events atomically.
- Record the payment identity, amount, and outcome accepted by each order to reject conflicting replays.

### Notifications

- Publish `OrderCompleted` atomically when an order is confirmed or cancelled, including initial stock rejection.
- Consume final outcomes through Kafka in an independent notification module.
- Persist one simulated notification per order; duplicate events preserve the original record.
- Log the simulated message after the notification transaction commits.
- Retry technical failures and route malformed or conflicting messages to a dedicated DLT.

### Reliability and testing

- Transactional Outbox with batch publishing and `FOR UPDATE SKIP LOCKED`.
- At-least-once delivery with idempotent inventory initialization, order reservations, and order result handling.
- Consumer retries and a dead-letter topic (DLT).
- Centralized HTTP errors using `ProblemDetail`.
- Versioned database migrations with Flyway.
- Unit, web-layer, integration, and concurrency tests using JUnit, Mockito, and Testcontainers.

## Technology stack

| Technology | Purpose |
|---|---|
| Java 25 | Application language and runtime |
| Spring Boot 4.1.2 | Application framework |
| Gradle Wrapper | Build and dependency management |
| Spring Web MVC and Validation | REST endpoints and input validation |
| Spring Data JPA / Hibernate | Persistence and locking |
| PostgreSQL 17 | Relational database |
| Flyway | Database migrations |
| Apache Kafka 4.1.2 / Spring Kafka | Event publication and consumption |
| Jackson 3 | JSON serialization |
| Lombok | Constructor and accessor generation |
| springdoc-openapi | OpenAPI documentation and Swagger UI |
| Podman and Compose | Local infrastructure |
| Testcontainers | Isolated PostgreSQL and Kafka integration tests |

## Architecture

The application is a **modular monolith** organized by business capability. Each module follows a hexagonal structure with domain objects, application use cases and ports, and infrastructure adapters.

```text
catalog/       inventory/       order/
├── domain/    ├── domain/      ├── domain/
├── application/               # commands, events, use cases, and ports
└── adapter/                   # web, Kafka, persistence, and outbox adapters

shared/infrastructure/
├── messaging/                 # event routes, decoding, retries, and DLT handling
└── outbox/                    # shared persistence and scheduled publication
```

The catalog also exposes a small public query contract used by orders to obtain
active-product details without accessing catalog persistence directly.

Domain objects enforce business rules without depending on Spring, JPA, or Kafka. Application services coordinate operations through ports. The application layer uses Spring annotations for dependency injection and transaction boundaries.

### Product-to-inventory flow

```mermaid
flowchart LR
    API[POST /products] --> Catalog[Catalog use case]
    Catalog --> TX[PostgreSQL transaction]
    TX --> Product[(Product)]
    TX --> Outbox[(Pending outbox event)]
    Outbox --> Publisher[Scheduled outbox publisher]
    Publisher --> Kafka[ProductCreated topic]
    Kafka --> Consumer[Inventory consumer]
    Consumer --> Init[Idempotent initialization]
    Init --> Inventory[(Inventory)]
    Consumer -->|Processing failure| Retry[Retry]
    Retry -->|Attempts exhausted| DLT[Dead-letter topic]
```

The publisher marks an event as published only after Kafka acknowledges the send. If Kafka acknowledges a message but the database transaction rolls back, the message can be sent again with the same event ID. Inventory initialization handles this by creating stock only when no record exists for that product.

Database commits and Kafka offset commits are separate. The system relies on idempotency rather than claiming exactly-once processing across both systems.

### Checkout saga

```mermaid
flowchart LR
    O[Order created] --> R[Reserve stock]
    R -->|Reserved| P[Request simulated payment]
    R -->|Rejected| C[Order CANCELLED]
    P -->|Approved| W[Confirm stock withdrawal]
    P -->|Declined| L[Release reservation]
    W --> F[Order CONFIRMED]
    L --> C
```

Every step communicates through Kafka and a transactional outbox. The order module
coordinates the workflow using durable intermediate states. Each handler commits
its database changes and outgoing event together; offset commits remain separate.
Duplicate handling therefore uses business identity and recorded outcomes.

A successful order follows `PENDING_STOCK -> PENDING_PAYMENT -> PENDING_STOCK_CONFIRMATION -> CONFIRMED`.
A refused payment follows `PENDING_PAYMENT -> PENDING_STOCK_RELEASE -> CANCELLED`.
The release of reserved stock is the compensation for payment refusal.

Technical failures are retried and can reach a route-specific DLT. An order may
remain pending until the cause is resolved and the event is explicitly replayed;
there is no automatic DLT replay or reservation-expiry job.

## Getting started

### Prerequisites

- JDK 25.
- Podman with a Compose provider available through `podman compose`.
- A running Podman machine on macOS.
- Ports `5432`, `9092`, and `8080` available. Optional browser tools use `8081` and `5050`.

Gradle is provided by the repository wrapper.

### 1. Clone the repository

```bash
git clone https://github.com/beatrizsousaanjosdev/lumera-store.git
cd lumera-store
```

### 2. Start PostgreSQL and Kafka

On macOS, start your Podman machine if it is stopped:

```bash
podman machine start
```

Start the infrastructure:

```bash
podman compose up -d --wait postgres kafka
```

### 3. Create the Kafka topics

Automatic topic creation is disabled. Create the eight event topics and their DLTs:

```bash
./scripts/create-kafka-topics.sh
```

| Event | Topic |
|---|---|
| ProductCreated | `lumera.product.created.v1` |
| OrderCreated | `lumera.order.created.v1` |
| StockReservationResult | `lumera.stock.reservation.result.v1` |
| PaymentRequested | `lumera.payment.requested.v1` |
| PaymentProcessed | `lumera.payment.processed.v1` |
| StockFinalizationRequested | `lumera.stock.finalization.requested.v1` |
| StockFinalized | `lumera.stock.finalized.v1` |
| OrderCompleted | `lumera.order.completed.v1` |

Each topic has a corresponding `.DLT`. The script uses three partitions and replication factor one for local development and preserves existing topics. If an existing topic has a different partition count, ensure its DLT has the same count.

The DLT has the same number of partitions because failed messages retain their source partition.

### 4. Run the API

Enable the outbox publisher and the consumers:

```bash
OUTBOX_ENABLED=true INVENTORY_KAFKA_ENABLED=true \
INVENTORY_ORDER_KAFKA_ENABLED=true ORDER_KAFKA_ENABLED=true \
PAYMENT_KAFKA_ENABLED=true NOTIFICATION_KAFKA_ENABLED=true ./gradlew bootRun
```

When running from an IDE, set these six environment variables in its run configuration. A plain `./gradlew bootRun` leaves background publishing and consumers disabled by default; the manual inventory endpoints remain available.

Flyway applies pending migrations at startup, and Hibernate validates the resulting schema.

### Local services

| Service | Address |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Kafka UI (optional) | http://localhost:8081 |
| pgAdmin (optional) | http://localhost:5050 |

Start the optional interfaces:

```bash
podman compose up -d kafka-ui pgadmin
```

For pgAdmin, log in with `admin@lumera.com` / `pgadmin_dev`, then register a server using host `postgres`, port `5432`, database `lumera`, user `lumera`, and password `lumera_dev`.

The application uses `localhost:9092` for Kafka. Containers on the Compose network use `kafka:29092`. Likewise, pgAdmin connects to `postgres`, not `localhost`.

The Compose credentials are local development defaults. This setup uses a single Kafka broker and a single PostgreSQL instance.

### Application configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/lumera` | Application database URL |
| `DB_USERNAME` | `lumera` | Database user |
| `DB_PASSWORD` | `lumera_dev` | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap address |
| `OUTBOX_ENABLED` | `false` | Enable scheduled event publication |
| `INVENTORY_KAFKA_ENABLED` | `false` | Enable ProductCreated consumption by inventory |
| `INVENTORY_ORDER_KAFKA_ENABLED` | `false` | Enable OrderCreated consumption and stock reservation |
| `ORDER_KAFKA_ENABLED` | `false` | Enable reservation, payment, and finalization result consumption by orders |
| `PAYMENT_KAFKA_ENABLED` | `false` | Enable PaymentRequested consumption |
| `NOTIFICATION_KAFKA_ENABLED` | `false` | Enable OrderCompleted consumption and simulated notifications |
| `PAYMENT_SIMULATOR_DECISION` | `APPROVED` | Simulated result: APPROVED or DECLINED |

A `.env` file is not automatically loaded by Spring Boot. Export variables in your shell or configure them in your IDE. The current Compose file defines its database credentials directly; changing application variables does not change an existing PostgreSQL user's password.

Outbox batch size, acknowledgement timeout, and scheduling intervals are configured under `app.outbox` in `src/main/resources/application.yaml`.

## API endpoints

### Products

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/products` | Create a product |
| `GET` | `/products/{id}` | Find a product |
| `PUT` | `/products/{id}` | Update name, description, price, and category |
| `PATCH` | `/products/{id}/active` | Change active status |
| `GET` | `/products` | List and filter products |

Supported categories: `PERIPHERALS`, `COMPUTERS`, `MONITORS`, `ACCESSORIES`, and `OTHER`.

Listing accepts `page` (default `0`), `size` (default `20`, range `1..100`), `category`, and `active`. Results are ordered by name and ID. Omitting `active` returns both active and inactive products.

```http
GET /products?category=PERIPHERALS&active=true&page=0&size=10
```

The page response contains `content`, `page`, `size`, `totalElements`, and `totalPages`.

### Inventory

| Method | Endpoint | Description |
|---|---|---|
| `PUT` | `/inventories/{productId}` | Initialize inventory without resetting existing stock |
| `GET` | `/inventories/{productId}` | Query inventory |
| `POST` | `/inventories/{productId}/entries` | Add units |
| `POST` | `/inventories/{productId}/reservations` | Reserve available units |
| `POST` | `/inventories/{productId}/reservations/release` | Release reserved units |
| `POST` | `/inventories/{productId}/reservations/confirm` | Confirm withdrawal of reserved units |

All movement endpoints accept a positive integer:

```json
{
  "amount": 3
}
```

Inventory maintains these invariants:

```text
quantity >= 0
0 <= reservedQuantity <= quantity
availableQuantity = quantity - reservedQuantity
```

A reservation reduces availability without changing total quantity. Releasing a reservation increases availability. Confirming a reservation reduces both total and reserved quantities.

The quantity-based movement endpoints are not idempotent: repeating a successful
request applies another movement. They reject reservation/release/confirmation with
HTTP 409 while the product has active order-specific reservations, so they cannot
consume units allocated to an order. Stock entries remain allowed.

Order-specific reservation services support idempotent reserve/release/confirmation,
all-or-nothing multi-product allocation, and a transaction-level advisory lock per
order. The Kafka adapter consumes `OrderCreated` and records the reservation decision
and its result event atomically. Order result handling locks the order row and accepts
matching duplicates. Conflicting results go to the result DLT.

The order creation endpoint returns `PENDING_STOCK`; subsequent `GET /orders/{id}`
requests observe the asynchronous transition. Successful reservation keeps units
reserved while payment is pending. Payment approval triggers stock withdrawal before
the order becomes CONFIRMED; refusal releases the reservation before cancellation.
Orders created while consumers are disabled remain pending until event processing resumes.

## Example workflow

Create a product:

```bash
curl -i -X POST 'http://localhost:8080/products' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Mechanical Keyboard",
    "description": "USB keyboard with backlight",
    "price": 249.90,
    "category": "PERIPHERALS"
  }'
```

Copy the returned product ID:

```bash
PRODUCT_ID='replace-with-the-returned-uuid'
```

With the background components enabled, wait a few seconds and query the automatically initialized stock:

```bash
curl -i "http://localhost:8080/inventories/$PRODUCT_ID"
```

Initialization is asynchronous, so an immediate lookup may return `404`. The publisher has a 10-second initial delay and a 5-second delay between runs.

If using only the synchronous API, initialize it manually instead:

```bash
curl -i -X PUT "http://localhost:8080/inventories/$PRODUCT_ID"
```

Add 10 units and reserve 3:

```bash
curl -i -X POST "http://localhost:8080/inventories/$PRODUCT_ID/entries" \
  -H 'Content-Type: application/json' \
  -d '{"amount": 10}'

curl -i -X POST "http://localhost:8080/inventories/$PRODUCT_ID/reservations" \
  -H 'Content-Type: application/json' \
  -d '{"amount": 3}'
```

The inventory response will show:

```json
{
  "productId": "<product-uuid>",
  "quantity": 10,
  "reservedQuantity": 3,
  "availableQuantity": 7
}
```

Confirm withdrawal of those 3 units:

```bash
curl -i -X POST "http://localhost:8080/inventories/$PRODUCT_ID/reservations/confirm" \
  -H 'Content-Type: application/json' \
  -d '{"amount": 3}'
```

The resulting quantities are total `7`, reserved `0`, and available `7`.

## Error handling

HTTP errors use `application/problem+json`, with fields such as `title`, `status`, `detail`, and `instance`. Request-validation errors also include field-level details.

| Status | Example |
|---|---|
| `400` | Invalid UUID, category, amount, or business-rule input |
| `404` | Product or inventory not found |
| `409` | Insufficient available stock or a concurrency conflict |
| `500` | Unexpected internal failure |

Kafka failures are handled separately from HTTP errors. Invalid messages are sent directly to the DLT; processing failures get two retries after the initial attempt. If DLT publication fails, the original offset remains uncommitted for recovery. DLT records require explicit operational review and replay; they are not automatically replayed after the cause is fixed.

## Testing

Run unit and web-layer tests without containers:

```bash
./gradlew test --tests '*domain.*' --tests '*application.*' --tests '*adapter.*' --tests '*shared*'
```

Run the entire suite with Podman:

```bash
./scripts/test-podman.sh
```

Run only the consumer integration tests:

```bash
./scripts/test-podman.sh --tests '*InventoryKafkaIntegrationTest'
```

The script discovers the Podman API socket on macOS. With another compatible runtime, configure `DOCKER_HOST` as needed. Integration tests provision isolated databases and brokers on dynamic ports; they do not use the application's data or topics.

Coverage includes transaction rollback, migrations, simultaneous stock reservations, concurrent initialization, outbox locking, real Kafka publication, duplicate delivery, consumer retries, and DLT/offset behavior.

See [TESTING.md](TESTING.md) for more details.

## Current scope and roadmap

Catalog, inventory, order creation/lookup, and the asynchronous order-stock reservation
and simulated-payment checkout flows are implemented. Flyway migrations include the
shared outbox (V7), payment storage (V8), intermediate order states (V9), and accepted
payment results for replay validation (V10), and simulated notification records (V11).

Planned extensions:

- HTTP idempotency for order creation (Kafka replay idempotency is already implemented).
- Integrate a real payment provider with durable provider idempotency and reconciliation.
- Real notification delivery, reservation expiry, cancellation, and recovery of stalled orders.
- Operational metrics, alerts, and a DLT replay workflow.

Authentication and authorization are not implemented. This is a portfolio project with a local development environment; production deployment would require additional operational and security work.

### Testing payment outcomes locally

Start with all consumer flags enabled as above. The default simulator approves every
new payment. To exercise refusal, restart with the same flags and
`PAYMENT_SIMULATOR_DECISION=DECLINED`, then create a new order with sufficient stock.
Existing payments keep their recorded outcome on replay, even if the configuration changes.

The simulator performs no external charge. A real provider requires a stable, persisted
idempotency key before making an external request, plus reconciliation after ambiguous
failures; database rollback cannot reverse an external charge.

### Inspecting simulated notifications

After creating an order with all consumer flags enabled, wait for its terminal status
and for the notification consumer. The application writes an INFO log such as:

```text
Simulated order notification: notificationId=..., orderId=..., outcome=CONFIRMED, message=Your order ... has been confirmed.
```

The durable record can be inspected in PostgreSQL:

```sql
SELECT order_id, outcome, processed_at
FROM order_notifications
ORDER BY processed_at DESC;
```

`CANCELLED` is also produced for insufficient stock and refused payment. Notification
failures do not change the completed order or undo stock movements. Technical failures
get two retries after the initial attempt; exhausted messages require explicit review
and replay from `lumera.order.completed.v1.DLT`.

This version simulates delivery; it has no email recipient or external email provider.
The database row is the durable record of the simulation. Logs run after commit and
are diagnostic output; a process crash between commit and logging can omit a log line.
A future external delivery adapter needs provider idempotency and reconciliation to
handle failures between sending a message and recording its delivery.
