# Tests

## Unit and web tests without PostgreSQL

```sh
./gradlew test --tests '*domain.*' --tests '*application.*' --tests '*adapter.*'
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
`outbox_integration`, `inventory_kafka_integration`, and `application_smoke_test`. Outbox and consumer integration each start an isolated Apache Kafka 4.1.2 broker
and create their own topics. Tests do not use
the application's PostgreSQL or Kafka containers, DB_URL/DB_USERNAME/DB_PASSWORD,
or KAFKA_BOOTSTRAP_SERVERS. Images and dependencies may be downloaded on the first run.

Coverage includes:

- Flyway V1 through V4 on an empty database, with Hibernate schema validation.
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
- Batch limits and event-type filtering are enforced by the database query.
- Unit tests cover synchronous failures, asynchronous failures, timeout, interruption,
  empty batches, invalid configuration, and scheduler enablement/recovery.

All full-context tests explicitly disable the scheduler with app.outbox.enabled=false,
even if OUTBOX_ENABLED=true is set in the shell. The inventory consumer is explicitly
disabled in other integration tests and enabled only in its dedicated test context. Integration calls the transactional
publisher directly so Kafka sends and database state can be asserted deterministically.

Local publishing is enabled with OUTBOX_ENABLED=true. Batch size and acknowledgement
wait are configurable through app.outbox.batch-size (1..100) and app.outbox.send-timeout.
The producer also bounds metadata/buffer blocking (max.block.ms=5000), request timeout
(5000ms), and delivery timeout (10000ms). Acknowledgement wait defaults to 15s.
These settings do not create a single deadline for the entire batch.

Delivery is at least once. Kafka acknowledgements cannot roll back with PostgreSQL;
a failed batch may resend previously acknowledged events. Consumers must be idempotent.
Inventory initialization consumes these events idempotently.

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
