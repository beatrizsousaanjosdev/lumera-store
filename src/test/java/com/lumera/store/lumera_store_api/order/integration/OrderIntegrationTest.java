package com.lumera.store.lumera_store_api.order.integration;

import com.jayway.jsonpath.JsonPath;
import com.lumera.store.lumera_store_api.order.adapter.out.persistence.*;
import com.lumera.store.lumera_store_api.order.application.command.*;
import com.lumera.store.lumera_store_api.order.application.port.in.CreateOrderUsecase;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.domain.*;
import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.notification.kafka.enabled=false", "app.payment.kafka.enabled=false", "app.inventory.kafka.enabled=false", "app.inventory.order-kafka.enabled=false", "app.order.kafka.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class OrderIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("order_integration").withUsername("integration").withPassword("integration");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET statement_timeout = '10s'");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired CreateOrderUsecase create;
    @Autowired OrderRepositoryPort port;
    @Autowired OrderJpaRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Flyway flyway;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter orderOutbox;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM order_payment_results");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM inventories");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void shouldRollbackOrderAndItemsWhenOutboxAppendFails() {
        UUID product = product("Mouse", "10.00", true);
        com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox.OrderEventOutboxAdapter target =
                org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderOutbox);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("Outbox unavailable"))
                .when(target).append(org.mockito.ArgumentMatchers.any());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> create.execute(new CreateOrderCommand(List.of(new CreateOrderItemCommand(product, 2)))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class));
    }

    @Test
    void shouldApplyMigrationsAndValidateJpaMappings() {
        assertEquals("11", flyway.info().current().getVersion().toString());
        assertEquals(11, flyway.info().applied().length);
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name IN ('orders','order_items')
                """, Integer.class));
    }

    @Test
    void shouldCreateAndReadOrderWithAllItemsAndTotal() throws Exception {
        UUID first = product("Mouse", "10.25", true);
        UUID second = product("Keyboard", "2.50", true);
        String body = "{\"items\":[{\"productId\":\"" + first + "\",\"quantity\":2},"
                + "{\"productId\":\"" + second + "\",\"quantity\":3}]}";
        var response = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(28.00))
                .andExpect(jsonPath("$.status").value("PENDING_STOCK"))
                .andExpect(jsonPath("$.items[0].productId").value(first.toString()))
                .andExpect(jsonPath("$.items[1].productId").value(second.toString()))
                .andReturn().getResponse();
        UUID id = UUID.fromString(JsonPath.read(response.getContentAsString(), "$.id"));
        assertTrue(response.getHeader("Location").endsWith("/orders/" + id));
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(28.00))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                .andExpect(jsonPath("$.items[1].subTotal").value(7.50));
        assertEquals(1, count("orders"));
        assertEquals(2, count("order_items"));
        assertEquals(0, count("inventories"));
    }

    @Test
    void shouldPreserveSnapshotsAfterCatalogChanges() throws Exception {
        UUID productId = product("Original name", "10.25", true);
        var order = create(productId);
        jdbc.update("UPDATE products SET name='New name', price=99.90, active=false WHERE id=?", productId);
        mvc.perform(get("/orders/{id}", order.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Original name"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(10.25))
                .andExpect(jsonPath("$.total").value(20.50));
    }

    @Test
    void shouldRejectMissingInactiveOrDuplicateProductsWithoutPartialOrder() throws Exception {
        UUID valid = product("Mouse", "10.00", true);
        UUID inactive = product("Keyboard", "20.00", false);
        UUID missing = UUID.randomUUID();
        for (var entry : Map.of(inactive, 409, missing, 404).entrySet()) {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(
                            "{\"items\":[{\"productId\":\"" + valid + "\",\"quantity\":1},"
                                    + "{\"productId\":\"" + entry.getKey() + "\",\"quantity\":1}]}"))
                    .andExpect(status().is(entry.getValue()));
        }
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"items\":[{\"productId\":\"" + valid + "\",\"quantity\":1},"
                                + "{\"productId\":\"" + valid + "\",\"quantity\":2}]}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
    }

    @Test
    void shouldReturn404ForUnknownOrder() throws Exception {
        mvc.perform(get("/orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldRollbackEntireAggregateOnFailure() {
        UUID product = product("Mouse", "10.00", true);
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status -> {
            create(product);
            throw new IllegalStateException("failure before commit");
        }));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
    }

    @Test
    void shouldRollbackParentAndAllItemsWhenDatabaseRejectsOneItem() {
        assertThrows(DataIntegrityViolationException.class, () -> transaction().executeWithoutResult(status -> {
            var entity = new OrderEntity(UUID.randomUUID(), Instant.now(), OrderStatus.PENDING_STOCK);
            entity.addItem(new OrderItemEntity(0, UUID.randomUUID(), "Valid", 1, BigDecimal.TEN));
            entity.addItem(new OrderItemEntity(1, UUID.randomUUID(), "Invalid", 0, BigDecimal.TEN));
            repository.saveAndFlush(entity);
        }));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
    }

    @Test
    void shouldPreserveItemIdsAndSnapshotsWhenUpdatingOnlyStatus() {
        var created = create(product("Mouse", "10.00", true));
        var itemIds = jdbc.queryForList("SELECT id FROM order_items WHERE order_id=? ORDER BY item_position", UUID.class, created.getId());
        transaction().executeWithoutResult(status -> {
            var order = port.findByIdForUpdate(created.getId()).orElseThrow();
            order.markStockReserved();
            port.save(order);
        });
        var updated = port.findById(created.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING_PAYMENT, updated.getStatus());
        assertEquals(created.getItems(), updated.getItems());
        assertEquals(itemIds, jdbc.queryForList("SELECT id FROM order_items WHERE order_id=? ORDER BY item_position", UUID.class, created.getId()));
    }

    @Test
    void shouldRequireExistingTransactionForSaveAndWriteLock() {
        var order = new Order(List.of(new OrderItem(UUID.randomUUID(), "Mouse", 1, BigDecimal.TEN)));
        assertThrows(IllegalTransactionStateException.class, () -> port.save(order));
        assertThrows(IllegalTransactionStateException.class, () -> port.findByIdForUpdate(order.getId()));
    }

    @Test
    void shouldEnforceDatabaseConstraints() {
        var order = create(product("Mouse", "10.00", true));
        UUID id = order.getId();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE orders SET status='UNKNOWN' WHERE id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE order_items SET quantity=0 WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE order_items SET unit_price=0 WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE order_items SET product_name=' ' WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE order_items SET item_position=-1 WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE order_items SET order_id=? WHERE order_id=?", UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO order_items SELECT ?,order_id,item_position,product_id,product_name,quantity,unit_price FROM order_items WHERE order_id=?", UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO order_items(id,order_id,item_position,product_id,product_name,quantity,unit_price) SELECT ?,order_id,1,product_id,product_name,quantity,unit_price FROM order_items WHERE order_id=?", UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO order_items(id,order_id,item_position,product_id,product_name,quantity,unit_price) SELECT ?,order_id,0,?,product_name,quantity,unit_price FROM order_items WHERE order_id=?", UUID.randomUUID(), UUID.randomUUID(), id));
        assertEquals(1, count("orders"));
        assertEquals(1, count("order_items"));
    }

    @Test
    @Timeout(25)
    void shouldSerializeStateChangesAndAllowOrdinaryReadsDuringLock() throws Exception {
        UUID id = create(product("Mouse", "10.00", true)).getId();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var first = executor.submit(() -> transaction().executeWithoutResult(status -> {
                var order = port.findByIdForUpdate(id).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Release timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                order.cancel();
                port.save(order);
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var read = executor.submit(() -> port.findById(id).orElseThrow());
                assertEquals(OrderStatus.PENDING_STOCK, read.get(3, TimeUnit.SECONDS).getStatus());
                var second = executor.submit(() -> transaction().executeWithoutResult(status -> {
                    var order = port.findByIdForUpdate(id).orElseThrow();
                    order.markStockReserved();
                    port.save(order);
                }));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    if (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Integer.class) > 0) {
                        waiting = true; break;
                    }
                    Thread.sleep(20);
                }
                assertTrue(waiting, "Competing transition must wait for the order row lock");
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                var failure = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
                assertInstanceOf(InvalidOrderException.class, failure.getCause());
            } finally { release.countDown(); }
        }
        assertEquals(OrderStatus.CANCELLED, port.findById(id).orElseThrow().getStatus());
    }

    @Test
    void shouldDocumentSuccessAndErrorSchemasWithoutItemSchemaCollision() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var docs = mapper.readTree(json);
        for (String code : List.of("201", "400", "404", "409", "500")) {
            assertTrue(docs.at("/paths/~1orders/post/responses/" + code).isObject(), code);
        }
        for (String code : List.of("200", "400", "404", "500")) {
            assertTrue(docs.at("/paths/~1orders~1{id}/get/responses/" + code).isObject(), code);
        }
        assertEquals("#/components/schemas/OrderResponseDto",
                docs.at("/paths/~1orders/post/responses/201/content/application~1json/schema/$ref").asText());
        assertEquals("#/components/schemas/OrderResponseDto",
                docs.at("/paths/~1orders~1{id}/get/responses/200/content/application~1json/schema/$ref").asText());
        assertTrue(docs.at("/paths/~1orders/post/responses/400/content/application~1problem+json/schema").has("$ref"));
        var responseRef = docs.at("/components/schemas/OrderResponseDto/properties/items/items/$ref").asText();
        var requestRef = docs.at("/components/schemas/CreateOrderRequestDto/properties/items/items/$ref").asText();
        assertTrue(docs.at(responseRef.substring(1) + "/properties").has("unitPrice"));
        assertFalse(docs.at(requestRef.substring(1) + "/properties").has("unitPrice"),
                "Request schema must not include server-selected price");
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }
    private Order create(UUID product) {
        return create.execute(new CreateOrderCommand(List.of(new CreateOrderItemCommand(product, 2))));
    }
    private UUID product(String name, String price, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO products(id,name,price,category,active) VALUES (?,?,?,'OTHER',?)", id, name, new BigDecimal(price), active);
        return id;
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
}
