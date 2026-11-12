package com.lumera.store.lumera_store_api.inventory.integration;

import com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox.OutboxEventPublisher;
import com.lumera.store.lumera_store_api.catalog.application.port.in.CreateProductsUsecase;
import com.lumera.store.lumera_store_api.catalog.domain.Category;
import com.lumera.store.lumera_store_api.inventory.adapter.in.messaging.ProductCreatedMessage;
import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.AddStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReserveStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.service.InitializeInventoryService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.outbox.enabled=false",
        "app.inventory.kafka.enabled=true",
        "spring.kafka.consumer.group-id=inventory-kafka-integration"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SuppressWarnings({"rawtypes", "unchecked"})
class InventoryKafkaIntegrationTest {
    static final String TOPIC = "inventory.product.created.test";
    static final String DLT = TOPIC + ".DLT";
    static final String GROUP = "inventory-kafka-integration";
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("inventory_kafka_integration").withUsername("integration").withPassword("integration");
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
        registry.add("app.kafka.topics.product-created-dlt", () -> DLT);
        registry.add("spring.kafka.consumer.properties[allow.auto.create.topics]", () -> false);
    }

    @BeforeAll
    static void createTopics() throws Exception {
        try (var admin = admin()) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1), new NewTopic(DLT, 3, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired CreateProductsUsecase createProduct;
    @Autowired OutboxEventPublisher publisher;
    @Autowired AddStockUsecase add;
    @Autowired ReserveStockUsecase reserve;
    @Autowired KafkaListenerEndpointRegistry listeners;
    @MockitoSpyBean InitializeInventoryService initialize;
    @MockitoSpyBean(name = "kafkaTemplate") KafkaTemplate kafka;

    @AfterAll
    static void stopListenerBeforeContainers(@Autowired KafkaListenerEndpointRegistry registry) {
        registry.stop();
    }

    @BeforeEach
    void prepare() {
        await(() -> {
            var container = listeners.getListenerContainer("inventory-product-created");
            return container != null && container.getAssignedPartitions() != null
                    && container.getAssignedPartitions().size() == 3;
        }, "Listener must be assigned all topic partitions");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM inventories");
        jdbc.update("DELETE FROM products");
    }

    @AfterEach
    void waitForOffsetCommitsBeforeResettingSpies() {
        try (var consumer = inspector(TOPIC); var admin = admin()) {
            var ends = consumer.endOffsets(partitions(TOPIC));
            await(() -> {
                try {
                    var committed = admin.listConsumerGroupOffsets(GROUP)
                            .partitionsToOffsetAndMetadata().get(3, TimeUnit.SECONDS);
                    return ends.entrySet().stream().allMatch(entry -> entry.getValue() == 0
                            || committed.get(entry.getKey()) != null
                            && committed.get(entry.getKey()).offset() >= entry.getValue());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }, "All test records must be committed before the next test");
        }
    }

    @Test
    void shouldCreateInventoryThroughProductOutboxAndKafka() {
        UUID id = createProduct.execute("Mouse", null, BigDecimal.TEN, Category.OTHER).getId();
        publisher.publishPending();
        await(() -> inventoryCount(id) == 1, "Product event must initialize inventory");
        assertState(id, 0, 0);
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE product_id = ? AND published_at IS NOT NULL", Integer.class, id));
    }

    @Test
    void shouldPreserveQuantitiesOnRedeliveryAndDifferentEventForSameProduct() throws Exception {
        UUID id = product();
        String payload = payload(id);
        send(id.toString(), payload);
        await(() -> inventoryCount(id) == 1, "First event must create inventory");
        add.execute(new StockMovementCommand(id, 10));
        reserve.execute(new StockMovementCommand(id, 3));
        send(id.toString(), payload);
        send(id.toString(), payload(id));
        // Wait for committed offsets, not merely existence of the already-created inventory.
        waitForOffsetCommitsBeforeResettingSpies();
        assertState(id, 10, 3);
        assertEquals(1, inventoryCount(id));
        verify(target(), times(3)).execute(id);
    }

    @Test
    void shouldRetryTransientFailureThenInitialize() throws Exception {
        UUID id = product();
        var target = target();
        doThrow(new CannotAcquireLockException("temporary lock conflict"))
                .doCallRealMethod().when(target).execute(id);
        try (var dlt = inspector(DLT)) {
            send(id.toString(), payload(id));
            await(() -> inventoryCount(id) == 1, "Transient failure must be retried");
            waitForOffsetCommitsBeforeResettingSpies();
            verify(target, times(2)).execute(id);
            assertState(id, 0, 0);
            assertTrue(dlt.poll(Duration.ofMillis(300)).isEmpty());
        }
    }

    @Test
    void shouldPublishToDltAfterThreeFailedAttempts() throws Exception {
        UUID id = product();
        doThrow(new DataAccessResourceFailureException("database temporarily unavailable"))
                .when(target()).execute(id);
        String payload = payload(id);
        try (var dlt = inspector(DLT)) {
            var metadata = send(id.toString(), payload);
            var failed = receive(dlt);
            assertEquals(payload, failed.value());
            assertEquals(id.toString(), failed.key());
            assertEquals(metadata.partition(), failed.partition());
            assertEquals(TOPIC, new String(failed.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                    StandardCharsets.UTF_8));
            assertNotNull(failed.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN));
            waitForOffsetCommitsBeforeResettingSpies();
            verify(target(), times(3)).execute(id);
            assertEquals(0, inventoryCount(id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "null"})
    void shouldSendInvalidPayloadDirectlyToDltAndContinue(String invalid) throws Exception {
        UUID key = UUID.randomUUID();
        UUID validProduct = product();
        try (var dlt = inspector(DLT)) {
            // Same partition: a later valid message must not remain blocked by the invalid one.
            sendToPartition(0, key.toString(), invalid);
            var failed = receive(dlt);
            assertEquals(invalid, failed.value());
            assertEquals(0, failed.partition());
            verify(target(), never()).execute(key);
            sendToPartition(0, validProduct.toString(), payload(validProduct));
            await(() -> inventoryCount(validProduct) == 1, "Consumer must continue after DLT recovery");
        }
    }

    @Test
    void shouldSendMismatchedKeyToDltWithoutInitializing() throws Exception {
        UUID id = product();
        try (var dlt = inspector(DLT)) {
            send(UUID.randomUUID().toString(), payload(id));
            receive(dlt);
            verify(target(), never()).execute(id);
            assertEquals(0, inventoryCount(id));
        }
    }

    @Test
    void shouldSendMissingProductToDltWithoutOrphanInventory() throws Exception {
        UUID id = UUID.randomUUID();
        try (var dlt = inspector(DLT)) {
            send(id.toString(), payload(id));
            receive(dlt);
            waitForOffsetCommitsBeforeResettingSpies();
            verify(target(), times(3)).execute(id);
            assertEquals(0, inventoryCount(id));
        }
    }

    @Test
    void shouldNotCommitOriginalOffsetWhenDltPublicationFails() throws Exception {
        UUID key = UUID.randomUUID();
        var allowDlt = new AtomicBoolean(false);
        var failedAttempt = new CountDownLatch(1);
        doAnswer(invocation -> {
            ProducerRecord record = invocation.getArgument(0);
            if (DLT.equals(record.topic()) && key.toString().equals(record.key()) && !allowDlt.get()) {
                failedAttempt.countDown();
                return CompletableFuture.failedFuture(new IllegalStateException("DLT unavailable"));
            }
            return invocation.callRealMethod();
        }).when(kafka).send(any(ProducerRecord.class));
        try (var dlt = inspector(DLT); var admin = admin()) {
            try {
                var sent = send(key.toString(), "{}");
                assertTrue(failedAttempt.await(10, TimeUnit.SECONDS));
                var committed = admin.listConsumerGroupOffsets(GROUP)
                        .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                var offset = committed.get(new TopicPartition(TOPIC, sent.partition()));
                assertTrue(offset == null || offset.offset() <= sent.offset(),
                        "Unrecovered message must not be committed");
                assertTrue(dlt.poll(Duration.ofMillis(200)).isEmpty());
                allowDlt.set(true);
                assertEquals("{}", receive(dlt).value());
            } finally {
                allowDlt.set(true);
            }
        }
    }

    private InitializeInventoryService target() {
        return AopTestUtils.getUltimateTargetObject(initialize);
    }

    private UUID product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO products(id, name, price, category, active) VALUES (?, 'Mouse', 10, 'OTHER', true)", id);
        return id;
    }

    private String payload(UUID id) {
        return mapper.writeValueAsString(new ProductCreatedMessage(UUID.randomUUID(), id, Instant.now()));
    }

    private RecordMetadata send(String key, String value) throws Exception {
        return sendToPartition(null, key, value);
    }

    private RecordMetadata sendToPartition(Integer partition, String key, String value) throws Exception {
        return ((org.springframework.kafka.support.SendResult<String, String>) kafka.send(
                new ProducerRecord<>(TOPIC, partition, key, value)).get(15, TimeUnit.SECONDS)).getRecordMetadata();
    }

    private int inventoryCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM inventories WHERE product_id = ?", Integer.class, id);
    }

    private void assertState(UUID id, int quantity, int reserved) {
        assertEquals(quantity, jdbc.queryForObject("SELECT quantity FROM inventories WHERE product_id = ?", Integer.class, id));
        assertEquals(reserved, jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE product_id = ?", Integer.class, id));
    }

    private static AdminClient admin() {
        return AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
    }

    private List<TopicPartition> partitions(String topic) {
        return IntStream.range(0, 3).mapToObj(i -> new TopicPartition(topic, i)).toList();
    }

    private KafkaConsumer<String, String> inspector(String topic) {
        var consumer = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "group.id", UUID.randomUUID().toString(),
                "enable.auto.commit", "false",
                "key.deserializer", StringDeserializer.class.getName(),
                "value.deserializer", StringDeserializer.class.getName()));
        var partitions = partitions(topic);
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private ConsumerRecord<String, String> receive(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(200));
            if (!records.isEmpty()) {
                assertEquals(1, records.count(), "Only one DLT record is expected");
                return records.iterator().next();
            }
        }
        throw new AssertionError("Expected DLT record was not received");
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(25); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        fail(message);
    }
}
