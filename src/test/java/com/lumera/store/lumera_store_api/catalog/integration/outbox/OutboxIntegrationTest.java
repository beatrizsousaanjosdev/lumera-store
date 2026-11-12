package com.lumera.store.lumera_store_api.catalog.integration.outbox;

import com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox.*;
import com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox.*;
import com.lumera.store.lumera_store_api.catalog.application.event.ProductCreatedEvent;
import com.lumera.store.lumera_store_api.catalog.application.port.in.CreateProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.inventory.kafka.enabled=false"})
@Testcontainers
@SuppressWarnings({"rawtypes", "unchecked"})
class OutboxIntegrationTest {
    static final String TOPIC = "lumera.product.created.test";
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("outbox_integration").withUsername("integration").withPassword("integration");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.topics.product-created", () -> TOPIC);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET statement_timeout = '10s'");
    }

    @BeforeAll
    static void createTopic() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get(20, TimeUnit.SECONDS);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateProductsUsecase createProduct;
    @Autowired OutboxEventPublisher publisher;
    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JsonMapper mapper;
    @MockitoSpyBean(name = "kafkaTemplate")
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafka;
    @MockitoSpyBean ProductEventOutboxAdapter outboxAdapter;

    @BeforeEach
    void clearDedicatedDatabase() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM inventories");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void shouldCommitProductAndPendingEventTogether() {
        var product = create();
        var event = repository.findAll().getFirst();
        assertEquals(product, event.getProductId());
        assertNull(event.getPublishedAt());
        var payload = mapper.readValue(event.getPayload(), ProductCreatedEvent.class);
        assertEquals(product, payload.productId());
        assertEquals(event.getEventId(), payload.eventId());
        assertEquals(1, count("products"));
        assertEquals(1, count("outbox_events"));
    }

    @Test
    void shouldRollbackProductWhenAppendingOutboxFails() {
        ProductEventOutboxAdapter target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outboxAdapter);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("outbox unavailable")).when(target).append(any());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, this::create);
        assertEquals(0, count("products"));
        assertEquals(0, count("outbox_events"));
    }

    @Test
    void shouldRequireTransactionForOutboxWritesAndLocks() {
        assertThrows(IllegalTransactionStateException.class,
                () -> outboxAdapter.append(ProductCreatedEvent.create(UUID.randomUUID())));
        assertThrows(IllegalTransactionStateException.class,
                () -> repository.findPendingForUpdate("ProductCreated", 1));
    }

    @Test
    void shouldPublishStoredJsonToRealKafkaAndNotResendPublishedEvent() throws Exception {
        UUID product = create();
        var event = repository.findAll().getFirst();
        try (var consumer = consumerAtEnd()) {
            publisher.publishPending();
            var records = receive(consumer, 1);
            assertEquals(1, records.size());
            assertEquals(product.toString(), records.getFirst().key());
            assertEquals(event.getPayload(), records.getFirst().value());
            assertEquals(event.getEventId(), mapper.readValue(records.getFirst().value(), ProductCreatedEvent.class).eventId());
            assertNotNull(repository.findById(event.getEventId()).orElseThrow().getPublishedAt());
            publisher.publishPending();
            assertTrue(consumer.poll(Duration.ofMillis(500)).isEmpty());
        }
    }

    @Test
    void shouldKeepFailedBatchPendingAndRetryWithSameEventIds() throws Exception {
        create();
        UUID failingProduct = create();
        var originalIds = new HashSet<>(repository.findAll().stream().map(OutboxEventEntity::getEventId).toList());
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("temporary broker failure")))
                .when(kafka).send(eq(TOPIC), eq(failingProduct.toString()), anyString());
        try (var consumer = consumerAtEnd()) {
            assertThrows(OutboxPublicationException.class, publisher::publishPending);
            assertEquals(0, publishedCount());
            // The first send was acknowledged, but the DB transaction rolled back for the whole batch.
            var firstDelivery = receive(consumer, 1);
            assertEquals(1, firstDelivery.size());
            doCallRealMethod().when(kafka).send(eq(TOPIC), anyString(), anyString());
            publisher.publishPending();
            var retried = receive(consumer, 2);
            var retryIds = new HashSet<>(retried.stream()
                    .map(record -> mapper.readValue(record.value(), ProductCreatedEvent.class).eventId()).toList());
            assertEquals(originalIds, retryIds);
            assertEquals(2, publishedCount());
        }
    }

    @Test
    void shouldPermitDuplicateDeliveryAfterAcknowledgementAndDatabaseRollback() throws Exception {
        create();
        var id = repository.findAll().getFirst().getEventId();
        try (var consumer = consumerAtEnd()) {
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        publisher.publishPending();
                        throw new IllegalStateException("failure after broker acknowledgement");
                    }));
            assertEquals(0, publishedCount());
            var first = receive(consumer, 1).getFirst();
            publisher.publishPending();
            var repeated = receive(consumer, 1).getFirst();
            assertEquals(id, mapper.readValue(first.value(), ProductCreatedEvent.class).eventId());
            assertEquals(first.value(), repeated.value());
            assertEquals(1, publishedCount());
        }
    }

    @Test
    @Timeout(30)
    void shouldSkipLockedEventsAndPublishThemAfterLockRelease() throws Exception {
        create();
        create();
        var acquired = new CompletableFuture<UUID>();
        var release = new CountDownLatch(1);
        try (var consumer = consumerAtEnd(); var executor = Executors.newFixedThreadPool(2)) {
            Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        var held = repository.findPendingForUpdate("ProductCreated", 1).getFirst();
                        acquired.complete(held.getEventId());
                        try {
                            if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Lock release timed out");
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
            try {
                UUID heldId = acquired.get(5, TimeUnit.SECONDS);
                executor.submit(publisher::publishPending).get(8, TimeUnit.SECONDS);
                assertEquals(1, publishedCount());
                assertNull(repository.findById(heldId).orElseThrow().getPublishedAt());
                release.countDown();
                lockHolder.get(5, TimeUnit.SECONDS);
                publisher.publishPending();
                assertEquals(2, publishedCount());
                assertEquals(2, receive(consumer, 2).stream()
                        .map(record -> mapper.readValue(record.value(), ProductCreatedEvent.class).eventId())
                        .distinct().count());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void shouldRespectBatchLimitAndIgnoreOtherEventTypes() {
        create();
        create();
        var otherId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_events(event_id, product_id, event_type, payload, occurred_at)
                VALUES (?, ?, 'OtherEvent', '{}', now())
                """, otherId, UUID.randomUUID());
        var selected = new TransactionTemplate(transactionManager).execute(status ->
                repository.findPendingForUpdate("ProductCreated", 1));
        assertNotNull(selected);
        assertEquals(1, selected.size());
        publisher.publishPending();
        assertEquals(2, publishedCount());
        assertNull(repository.findById(otherId).orElseThrow().getPublishedAt());
    }

    private UUID create() {
        return createProduct.execute("Mouse", "Outbox integration", BigDecimal.TEN, Category.OTHER).getId();
    }

    private int count(String table) {
        // Only fixed table names supplied by this test class.
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int publishedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class);
    }

    private KafkaConsumer<String, String> consumerAtEnd() {
        var consumer = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "group.id", UUID.randomUUID().toString(),
                "enable.auto.commit", "false",
                "key.deserializer", StringDeserializer.class.getName(),
                "value.deserializer", StringDeserializer.class.getName()));
        var partitions = IntStream.range(0, 3).mapToObj(i -> new TopicPartition(TOPIC, i)).toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private List<ConsumerRecord<String, String>> receive(KafkaConsumer<String, String> consumer, int expected) {
        var records = new ArrayList<ConsumerRecord<String, String>>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (records.size() < expected && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200)).forEach(records::add);
        }
        assertEquals(expected, records.size(), "Expected messages were not received from Kafka");
        return records;
    }
}
