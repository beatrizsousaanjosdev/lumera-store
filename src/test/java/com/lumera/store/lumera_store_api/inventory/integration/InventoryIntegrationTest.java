package com.lumera.store.lumera_store_api.inventory.integration;

import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.*;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InsufficientStockException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.inventory.kafka.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class InventoryIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("inventory_integration").withUsername("integration").withPassword("integration");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET statement_timeout = '10s'");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired InitializeInventoryUsecase initialize;
    @Autowired AddStockUsecase add;
    @Autowired ReserveStockUsecase reserve;
    @Autowired InventoryRepositoryPort port;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDedicatedTestDatabase() {
        jdbc.update("DELETE FROM inventories");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void shouldApplyMigrationsAndValidateMapping() {
        assertEquals("4", flyway.info().current().getVersion().toString());
        assertEquals(4, flyway.info().applied().length);
        UUID id = product();
        initialize.execute(id);
        assertDatabaseState(id, 0, 0);
    }

    @Test
    void shouldCompleteHttpStockLifecycleWithRealCommits() throws Exception {
        UUID id = product();
        mvc.perform(put("/inventories/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(0));
        movement(id, "entries", 10, 200);
        movement(id, "reservations", 6, 200);
        assertDatabaseState(id, 10, 6);
        movement(id, "reservations/release", 2, 200);
        assertDatabaseState(id, 10, 4);
        movement(id, "reservations/confirm", 4, 200);
        assertDatabaseState(id, 6, 0);
        mvc.perform(get("/inventories/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(id.toString()))
                .andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.availableQuantity").value(6));
    }

    @Test
    void shouldPreserveStockOnRepeatedInitialization() throws Exception {
        UUID id = product();
        initialize.execute(id);
        add.execute(new StockMovementCommand(id, 10));
        reserve.execute(new StockMovementCommand(id, 3));
        mvc.perform(put("/inventories/{id}", id)).andExpect(status().isOk());
        mvc.perform(put("/inventories/{id}", id)).andExpect(status().isOk());
        assertDatabaseState(id, 10, 3);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM inventories", Integer.class));
    }

    @Test
    @Timeout(20)
    void shouldInitializeSameProductConcurrentlyWithoutDuplicates() throws Exception {
        UUID id = product();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Inventory> task = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start signal timed out");
                return initialize.execute(id);
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            assertEquals(id, first.get(10, TimeUnit.SECONDS).getProductId());
            assertEquals(id, second.get(10, TimeUnit.SECONDS).getProductId());
        }
        assertDatabaseState(id, 0, 0);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM inventories", Integer.class));
    }

    @Test
    void shouldRejectInsufficientOrInvalidMovementsWithoutPartialUpdates() throws Exception {
        UUID id = product();
        initialize.execute(id);
        add.execute(new StockMovementCommand(id, 5));
        reserve.execute(new StockMovementCommand(id, 2));
        movement(id, "reservations", 4, 409);
        movement(id, "reservations/release", 3, 400);
        movement(id, "reservations/confirm", 3, 400);
        movement(id, "entries", 0, 400);
        assertDatabaseState(id, 5, 2);
    }

    @Test
    void shouldReturn404ForMissingProductAndMissingInventory() throws Exception {
        UUID unknownProduct = UUID.randomUUID();
        mvc.perform(put("/inventories/{id}", unknownProduct))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
        UUID existingProduct = product();
        mvc.perform(get("/inventories/{id}", existingProduct))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Inventory not found"));
        movement(existingProduct, "entries", 1, 404);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM inventories", Integer.class));
    }

    @Test
    void shouldEnforceDatabaseConstraints() {
        UUID id = product();
        initialize.execute(id);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE inventories SET quantity = -1 WHERE product_id = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE inventories SET reserved_quantity = -1 WHERE product_id = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE inventories SET reserved_quantity = 1 WHERE product_id = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO inventories VALUES (?, 0, 0)", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO inventories VALUES (?, 0, 0)", UUID.randomUUID()));
        assertDatabaseState(id, 0, 0);
    }

    @Test
    @Timeout(25)
    void shouldBlockConcurrentReservationAndPreventOverselling() throws Exception {
        UUID id = product();
        initialize.execute(id);
        add.execute(new StockMovementCommand(id, 5));
        var locked = new CountDownLatch(1);
        var commitFirst = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        var inventory = port.findByProductIdForUpdate(id).orElseThrow();
                        locked.countDown();
                        try {
                            if (!commitFirst.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("Commit signal timed out");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        inventory.reserve(4);
                        port.save(inventory);
                    }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                Future<?> second = executor.submit(() -> reserve.execute(new StockMovementCommand(id, 4)));
                // Observe an actual PostgreSQL lock wait, rather than relying on thread timing.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    Integer count = jdbc.queryForObject("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database() AND wait_event_type = 'Lock'
                            """, Integer.class);
                    if (count != null && count > 0) { waiting = true; break; }
                    Thread.sleep(20);
                }
                assertTrue(waiting, "Second reservation must wait for the row lock");
                commitFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                var failure = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
                assertInstanceOf(InsufficientStockException.class, failure.getCause());
            } finally {
                commitFirst.countDown();
            }
        }
        assertDatabaseState(id, 5, 4);
        assertEquals(1, port.findByProductId(id).orElseThrow().getAvailableQuantity());
    }

    @Test
    void shouldRollbackStockMovementWhenTransactionFails() {
        UUID id = product();
        initialize.execute(id);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    add.execute(new StockMovementCommand(id, 10));
                    throw new IllegalStateException("Simulated failure before commit");
                }));
        assertDatabaseState(id, 0, 0);
    }

    private UUID product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, price, category, active) VALUES (?, 'Mouse', 10, 'OTHER', true)", id);
        return id;
    }

    private void movement(UUID id, String route, int amount, int expectedStatus) throws Exception {
        mvc.perform(post("/inventories/{id}/" + route, id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().is(expectedStatus));
    }

    private void assertDatabaseState(UUID id, int quantity, int reserved) {
        assertEquals(quantity, jdbc.queryForObject(
                "SELECT quantity FROM inventories WHERE product_id = ?", Integer.class, id));
        assertEquals(reserved, jdbc.queryForObject(
                "SELECT reserved_quantity FROM inventories WHERE product_id = ?", Integer.class, id));
    }
}
