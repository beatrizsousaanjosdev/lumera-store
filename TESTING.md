# Tests

## Unit and web tests without PostgreSQL

```sh
./gradlew test --tests '*domain.*' --tests '*application.*' --tests '*adapter.*' --tests '*shared*'
```

## Integration tests with Podman on macOS

Start your Podman machine if it is stopped, then run:

```sh
./scripts/test-podman.sh --tests '*IntegrationTest'
```

To run the entire suite:

```sh
./scripts/test-podman.sh
```

The script discovers the Podman API socket, unless DOCKER_HOST is already set.
It disables Ryuk for rootless Podman; the JUnit Testcontainers lifecycle stops
and removes the PostgreSQL and Kafka test containers after the tests. If the JVM is killed
abruptly, check Podman for remaining test containers.

Integration test classes use dedicated PostgreSQL 17 containers with dynamically
assigned ports and databases `lumera_integration`, `inventory_integration`,
`outbox_integration`, `inventory_kafka_integration`, `order_integration`, `stock_reservation_integration`, and `application_smoke_test`. Outbox and consumer integration each start an isolated Apache Kafka 4.1.2 broker
and create their own topics. Tests do not use
the application's PostgreSQL or Kafka containers, DB_URL/DB_USERNAME/DB_PASSWORD,
or KAFKA_BOOTSTRAP_SERVERS. Images and dependencies may be downloaded on the first run.

Coverage includes:

- Flyway V1 through V7 on an empty database, with Hibernate schema validation.
- V1 to V2 upgrade preserving legacy products and assigning category OTHER.
- HTTP create/read/update and active-status changes with real commits.
- Pagination, stable ordering and category/active filters in PostgreSQL.
- Invalid HTTP input without unintended database changes.
- Database constraints for category, price and duplicate primary keys.

HTTP integration uses MockMvc with the full Spring context and real persistence;
it does not start an external HTTP server. Outbox tests use a real Kafka broker.

## Inventory coverage

- Domain invariants, stock movements and integer overflow.
- Command validation and services using repository ports.
- HTTP mappings, request validation and ProblemDetail error responses.
- Initialization without resetting stock, including simultaneous initialization.
- Real PostgreSQL movement lifecycle and transaction rollback.
- Foreign key, primary key and non-negative/reserved quantity constraints.
- Pessimistic locking: one transaction holds the row while another reservation
  waits (observed through pg_stat_activity). After commit, the second reservation
  reads the updated balance and rejects overselling.

Run only inventory tests:

```sh
./scripts/test-podman.sh --tests '*inventory*'
```

## Outbox coverage and operation

```sh
./scripts/test-podman.sh --tests '*OutboxIntegrationTest'
```

- Product creation and pending event commit together; an outbox failure rolls back the product.
- JSON serialization retains the event ID, product ID, timestamp and stored payload.
- Real Kafka messages use product ID as the key, without double JSON serialization.
- Published events are not selected again; timestamps are committed through JPA dirty checking.
- Failed sends roll back the batch and retries retain the original event IDs.
- A broker acknowledgement followed by database rollback can produce duplicate delivery.
- SKIP LOCKED lets another worker process unlocked events; locked events remain pending.
- Batch limits and aggregate/event-type filtering are enforced by the database query.
- Unit tests cover synchronous failures, asynchronous failures, timeout, interruption,
  empty batches, invalid configuration, and scheduler enablement/recovery.

All full-context tests explicitly disable the scheduler with app.outbox.enabled=false,
even if OUTBOX_ENABLED=true is set in the shell. Consumers are explicitly
disabled outside their dedicated Kafka integration contexts. Integration calls the transactional
publisher directly so Kafka sends and database state can be asserted deterministically.

Local publishing is enabled with OUTBOX_ENABLED=true. Batch size and acknowledgement
wait are configurable through app.outbox.batch-size (1..100) and app.outbox.send-timeout.
The producer also bounds metadata/buffer blocking (max.block.ms=5000), request timeout
(5000ms), and delivery timeout (10000ms). Acknowledgement wait defaults to 15s.
These settings do not create a single deadline for the entire batch.

Delivery is at least once. Kafka acknowledgements cannot roll back with PostgreSQL;
a failed batch may resend previously acknowledged events. Consumers must be idempotent.
Inventory initialization, order reservation, and order result handling consume events idempotently. Batch size is applied per event route.

## Inventory Kafka consumer coverage

```sh
./scripts/test-podman.sh --tests '*ProductCreatedConsumerTest' --tests '*InventoryKafkaConfigurationTest' --tests '*InventoryKafkaIntegrationTest'
```

- Payload structure, required identifiers/timestamp, JSON errors, null payload and message key validation.
- Processing exceptions propagate to the error handler rather than being swallowed.
- Consumer and retry/DLT configuration are enabled together by app.inventory.kafka.enabled.
- Full product -> outbox -> real Kafka -> inventory flow.
- Duplicate delivery and distinct events for the same product preserve stock and reservations.
- A transient failure succeeds on retry without sending the record to the DLT.
- Three failed processing attempts send the original key/payload and source metadata to the DLT.
- Malformed messages and mismatched keys go directly to the DLT without initializing stock.
- A later valid record on the same partition is consumed after invalid-message recovery.
- A nonexistent product is retried and then sent to the DLT without orphan inventory.
- Failure to publish to the DLT leaves the original offset uncommitted; processing recovers when DLT publishing succeeds.

Tests wait for committed offsets before cleaning data/resetting spies and stop the
listener before container shutdown. These checks exercise record acknowledgement
with real Kafka, not just direct calls to the listener method.

## Order coverage (creation and lookup)

```sh
./scripts/test-podman.sh --tests '*order*'
```

- Order/item invariants, monetary precision, immutable snapshots, totals, and every current state transition.
- Command validation, rejection of duplicate products, and defensive copying of item lists.
- Creation uses active catalog products and server-selected names/prices; missing/inactive products prevent persistence.
- Public catalog contract mapping and both ordinary and locking persistence lookups.
- HTTP creation/lookup, Location header, nested input validation and ProblemDetail responses.
- OpenAPI success/error schemas and distinct request/response item schemas.
- Flyway V5 and Hibernate mapping validation against real PostgreSQL.
- Aggregate persistence with cascade, stable item order, price snapshots surviving catalog changes,
  and preservation of item IDs during status-only updates.
- Rollback of parent and all items when the transaction fails or one item violates a database constraint.
- Database foreign key, uniqueness, status, quantity, name, position and price constraints.
- Concurrent status changes serialize on the order row; ordinary reads remain non-blocking.

Order-specific reservation services are implemented and tested separately below.
Order-created events and stock reservation results are wired through Kafka. Payment workflow and the saga are not implemented yet.
State transitions are currently tested in the domain and through the persistence port;
there are no HTTP endpoints for advancing those states.

## Order-specific stock reservations

```sh
./scripts/test-podman.sh --tests '*StockReservationTest' --tests '*ReserveOrderStockCommandTest' --tests '*OrderStockServicesTest' --tests '*OrderReservationGuardTest' --tests '*StockReservationIntegrationTest'
```

- Domain state machine and idempotent release/confirmation; command validation and immutable request items.
- Advisory lock acquired before lookup, including concurrent requests for a reservation that does not exist yet.
- Identical replays (regardless of item ordering) return the recorded result; changed payloads are rejected.
- All requested stock is checked before applying reservations; missing/insufficient stock records REJECTED without partial quantities.
- Rejected requests remain rejected on replay, even if stock becomes available later.
- Release and confirmation affect only each order's recorded quantities and preserve reservation item IDs.
- Independent PostgreSQL connections verify advisory-lock exclusion, unrelated-order independence, and release on commit/rollback.
- Different orders requesting overlapping products in opposite input order do not oversell; locks follow a consistent product ordering.
- Concurrent release vs confirmation admits only one final outcome.
- A failure saving the reservation rolls back stock changes; an inconsistent later item rolls back earlier finalization changes.
- Flyway V6, JPA mapping, foreign key/uniqueness/status/rejection-reason/quantity constraints.
- Legacy quantity-based reservation/release/confirmation is rejected with HTTP 409 while a product has active order reservations.
  This guard runs after acquiring the inventory row lock. Stock entries remain allowed.

## Order reservation Kafka integration

```sh
./scripts/test-podman.sh --tests '*OrderReservationKafkaIntegrationTest'
```

A dedicated PostgreSQL container and Kafka broker run the complete product -> inventory
initialization -> order -> reservation -> order status flow, using three event topics
and three DLTs. The test context enables all three listeners and invokes the transactional
publisher directly with the scheduler disabled.

- Successful multi-item reservation moves the order to PENDING_PAYMENT.
- Insufficient or missing inventory cancels the order without partial reservation.
- Duplicate creation and result messages preserve stock and produce one reservation-result event.
- Failure writing the result outbox rolls back stock and reservation; retry succeeds.
- A transient result-outbox failure is retried by the Kafka listener without double reservation.
- Changed reservation payloads and contradictory results go to DLT without overwriting valid state.
- Order-outbox append failures roll back the parent order and all items.
- Malformed order/result messages reach their respective DLTs and subsequent messages continue.
- Unit tests verify all result transitions, conflicting results, decoding, and topic routing.
- The V6 -> V7 migration preserves legacy aggregate IDs, payloads, and publication state.

These tests use isolated infrastructure and wait for committed offsets before cleanup.
Payment processing, compensation, and reservation expiry are outside this stage.
