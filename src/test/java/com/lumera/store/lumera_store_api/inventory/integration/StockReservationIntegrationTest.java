package com.lumera.store.lumera_store_api.inventory.integration;

import com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.reservation.StockReservationPersistenceAdapter;
import com.lumera.store.lumera_store_api.inventory.application.command.ReserveOrderStockCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.*;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.*;
import com.lumera.store.lumera_store_api.inventory.domain.exception.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.inventory.kafka.enabled=false", "app.inventory.order-kafka.enabled=false", "app.order.kafka.enabled=false"})
@Testcontainers
class StockReservationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("stock_reservation_integration").withUsername("integration").withPassword("integration");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET statement_timeout = '10s'");
    }

    @Autowired ReserveStockUsecase legacyReserve;
    @Autowired ReleaseStockUsecase legacyRelease;
    @Autowired ConfirmStockUsecase legacyConfirm;
    @Autowired AddStockUsecase entries;
    @Autowired ReserveOrderStockUsecase reserve;
    @Autowired ReleaseOrderStockUsecase release;
    @Autowired ConfirmOrderStockUsecase confirm;
    @Autowired StockReservationRepositoryPort reservations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Flyway flyway;
    @MockitoSpyBean StockReservationPersistenceAdapter adapter;

    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void cleanDedicatedDatabase() {
        jdbc.update("DELETE FROM stock_reservation_items");
        jdbc.update("DELETE FROM stock_reservations");
        jdbc.update("DELETE FROM inventories");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM products");
    }

    private List<StockReservationItem> items() {
        return List.of(new StockReservationItem(second, 3), new StockReservationItem(first, 2));
    }

    @Test
    void shouldApplyV6AndPersistAggregateInOriginalItemOrder() {
        assertEquals("7", flyway.info().current().getVersion().toString());
        assertEquals(7, flyway.info().applied().length);
        stock(first, 10); stock(second, 10);
        UUID id = UUID.randomUUID();
        var result = reserve.execute(new ReserveOrderStockCommand(id, items()));
        var saved = reservations.findByOrderId(id).orElseThrow();
        assertEquals(StockReservationStatus.RESERVED, result.getStatus());
        assertEquals(items(), saved.getItems());
        assertNotNull(saved.getCreatedAt());
        assertEquals(1, count("stock_reservations"));
        assertEquals(2, count("stock_reservation_items"));
        assertStock(first, 10, 2); assertStock(second, 10, 3);
    }

    @Test
    void shouldReturnSameReservationForReorderedReplayAndRejectChangedPayload() {
        stock(first, 10); stock(second, 10);
        UUID id = UUID.randomUUID();
        reserve.execute(new ReserveOrderStockCommand(id, items()));
        var reversed = new ArrayList<>(items()); Collections.reverse(reversed);
        reserve.execute(new ReserveOrderStockCommand(id, reversed));
        assertThrows(InvalidStockReservationException.class,
                () -> reserve.execute(new ReserveOrderStockCommand(id, List.of(new StockReservationItem(first, 9)))));
        assertEquals(1, count("stock_reservations"));
        assertEquals(2, count("stock_reservation_items"));
        assertStock(first, 10, 2); assertStock(second, 10, 3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistRejectionWithoutPartialReservationAndKeepResultOnReplay(boolean missing) {
        stock(first, 10);
        if (!missing) stock(second, 1);
        UUID id = UUID.randomUUID();
        var command = new ReserveOrderStockCommand(id, items());
        var result = reserve.execute(command);
        assertEquals(StockReservationStatus.REJECTED, result.getStatus());
        assertNotNull(result.getRejectionReason());
        assertStock(first, 10, 0);
        if (missing) stock(second, 10); else jdbc.update("UPDATE inventories SET quantity=10 WHERE product_id=?", second);
        assertEquals(StockReservationStatus.REJECTED, reserve.execute(command).getStatus());
        assertStock(second, 10, 0);
        assertEquals(items(), reservations.findByOrderId(id).orElseThrow().getItems());
    }

    @Test
    void shouldReleaseAndConfirmOnlyEachOrdersQuantitiesExactlyOnce() {
        stock(first, 10);
        UUID released = UUID.randomUUID(), confirmed = UUID.randomUUID();
        reserve.execute(command(released, 2)); reserve.execute(command(confirmed, 3));
        var ids = jdbc.queryForList("SELECT id FROM stock_reservation_items WHERE order_id=?", UUID.class, released);
        release.execute(released); release.execute(released);
        assertStock(first, 10, 3);
        confirm.execute(confirmed); confirm.execute(confirmed);
        assertStock(first, 7, 0);
        assertEquals(StockReservationStatus.RELEASED, reservations.findByOrderId(released).orElseThrow().getStatus());
        assertEquals(StockReservationStatus.CONFIRMED, reservations.findByOrderId(confirmed).orElseThrow().getStatus());
        assertEquals(ids, jdbc.queryForList("SELECT id FROM stock_reservation_items WHERE order_id=?", UUID.class, released));
        assertThrows(InvalidStockReservationException.class, () -> confirm.execute(released));
        assertThrows(InvalidStockReservationException.class, () -> release.execute(confirmed));
        assertStock(first, 7, 0);
    }

    @Test
    void shouldRollbackStocksWhenReservationPersistenceFails() {
        stock(first, 10); stock(second, 10);
        StockReservationPersistenceAdapter target = AopTestUtils.getUltimateTargetObject(adapter);
        doThrow(new DataAccessResourceFailureException("simulated write failure")).when(target).save(any());
        assertThrows(DataAccessResourceFailureException.class,
                () -> reserve.execute(new ReserveOrderStockCommand(UUID.randomUUID(), items())));
        assertStock(first, 10, 0); assertStock(second, 10, 0);
        assertEquals(0, count("stock_reservations"));
        assertEquals(0, count("stock_reservation_items"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "confirm"})
    void shouldRollbackEarlierStockChangesIfLaterItemIsInconsistent(String operation) {
        stock(first, 10); stock(second, 10);
        UUID id = UUID.randomUUID();
        reserve.execute(new ReserveOrderStockCommand(id, items()));
        // Deliberately corrupt the stock/reservation relationship to exercise rollback.
        jdbc.update("UPDATE inventories SET reserved_quantity=0 WHERE product_id=?", second);
        assertThrows(InvalidInventoryException.class, () -> finalize(operation, id));
        assertStock(first, 10, 2);
        assertStock(second, 10, 0);
        assertEquals(StockReservationStatus.RESERVED, reservations.findByOrderId(id).orElseThrow().getStatus());
    }

    @Test
    @Timeout(25)
    void shouldSerializeDuplicateCreationEvenBeforeReservationExists() throws Exception {
        stock(first, 10);
        UUID id = UUID.randomUUID();
        var locked = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstRequest = executor.submit(() -> transaction().execute(status -> {
                reservations.lockByOrderId(id);
                assertTrue(reservations.findByOrderId(id).isEmpty());
                locked.countDown(); waitFor(proceed);
                return reserve.execute(command(id, 4));
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var duplicate = executor.submit(() -> reserve.execute(command(id, 4)));
                await(() -> jdbc.queryForObject("""
                        SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                        AND wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock%'
                        """, Integer.class) > 0);
                proceed.countDown();
                assertEquals(StockReservationStatus.RESERVED, firstRequest.get(5, TimeUnit.SECONDS).getStatus());
                assertEquals(StockReservationStatus.RESERVED, duplicate.get(5, TimeUnit.SECONDS).getStatus());
            } finally { proceed.countDown(); }
        }
        assertStock(first, 10, 4);
        assertEquals(1, count("stock_reservations"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Timeout(25)
    void shouldReleaseAdvisoryLockOnCommitOrRollbackAndNotBlockOtherOrders(boolean rollback) throws Exception {
        UUID id = UUID.randomUUID();
        var locked = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> transaction().executeWithoutResult(status -> {
                reservations.lockByOrderId(id);
                locked.countDown(); waitFor(proceed);
                if (rollback) throw new IllegalStateException("rollback requested");
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                assertFalse(tryLockFromIndependentConnection(id));
                assertTrue(tryLockFromIndependentConnection(UUID.randomUUID()));
                proceed.countDown();
                if (rollback) assertThrows(ExecutionException.class, () -> holder.get(5, TimeUnit.SECONDS));
                else holder.get(5, TimeUnit.SECONDS);
                assertTrue(tryLockFromIndependentConnection(id));
            } finally { proceed.countDown(); }
        }
    }

    @Test
    @Timeout(25)
    void shouldPreventOversellingForDifferentOrdersWithReversedItemLists() throws Exception {
        stock(first, 5); stock(second, 5);
        var forward = List.of(new StockReservationItem(first, 4), new StockReservationItem(second, 4));
        var reverse = List.of(new StockReservationItem(second, 4), new StockReservationItem(first, 4));
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { ready.countDown(); waitFor(start); return reserve.execute(new ReserveOrderStockCommand(UUID.randomUUID(), forward)); });
            var b = executor.submit(() -> { ready.countDown(); waitFor(start); return reserve.execute(new ReserveOrderStockCommand(UUID.randomUUID(), reverse)); });
            try { assertTrue(ready.await(5, TimeUnit.SECONDS)); } finally { start.countDown(); }
            assertEquals(Set.of(StockReservationStatus.RESERVED, StockReservationStatus.REJECTED),
                    Set.of(a.get(10, TimeUnit.SECONDS).getStatus(), b.get(10, TimeUnit.SECONDS).getStatus()));
        }
        assertStock(first, 5, 4); assertStock(second, 5, 4);
    }

    @Test
    @Timeout(25)
    void shouldAllowOnlyOneConcurrentReleaseOrConfirmation() throws Exception {
        stock(first, 10);
        UUID id = UUID.randomUUID(); reserve.execute(command(id, 2));
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { ready.countDown(); waitFor(start); return attemptFinalize("release", id); });
            var b = executor.submit(() -> { ready.countDown(); waitFor(start); return attemptFinalize("confirm", id); });
            try { assertTrue(ready.await(5, TimeUnit.SECONDS)); } finally { start.countDown(); }
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        var status = reservations.findByOrderId(id).orElseThrow().getStatus();
        assertTrue(status == StockReservationStatus.CONFIRMED || status == StockReservationStatus.RELEASED);
        assertStock(first, status == StockReservationStatus.CONFIRMED ? 8 : 10, 0);
    }

    @Test
    void shouldRequireTransactionForAdvisoryLockAndSave() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalTransactionStateException.class, () -> reservations.lockByOrderId(id));
        assertThrows(IllegalTransactionStateException.class,
                () -> reservations.save(StockReservation.reserved(id, List.of(new StockReservationItem(first, 1)))));
    }

    @Test
    void shouldEnforceReservationAndItemConstraints() {
        stock(first, 10); UUID id = UUID.randomUUID(); reserve.execute(command(id, 2));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservations SET status='UNKNOWN' WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservations SET status='REJECTED' WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservations SET rejection_reason='unexpected' WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservations SET status='REJECTED',rejection_reason=' ' WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservation_items SET quantity=0 WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservation_items SET item_position=-1 WHERE order_id=?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE stock_reservation_items SET order_id=? WHERE order_id=?", UUID.randomUUID(), id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO stock_reservation_items(id,order_id,item_position,product_id,quantity) VALUES (?,?,1,?,1)", UUID.randomUUID(), id, first));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO stock_reservation_items(id,order_id,item_position,product_id,quantity) VALUES (?,?,0,?,1)", UUID.randomUUID(), id, second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "release", "confirm"})
    void shouldBlockLegacyMovementsWhileOrderReservationsAreActive(String operation) {
        stock(first, 10); UUID id = UUID.randomUUID(); reserve.execute(command(id, 2));
        var movement = new com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand(first, 1);
        assertThrows(com.lumera.store.lumera_store_api.inventory.application.exception.OrderManagedStockException.class,
                () -> { switch (operation) {
                    case "reserve" -> legacyReserve.execute(movement);
                    case "release" -> legacyRelease.execute(movement);
                    default -> legacyConfirm.execute(movement);
                }});
        assertStock(first, 10, 2);
        entries.execute(movement);
        assertStock(first, 11, 2);
    }

    @Test
    void shouldAllowLegacyWorkflowAfterAllOrderReservationsAreFinalized() {
        stock(first, 10); UUID id = UUID.randomUUID(); reserve.execute(command(id, 2));
        release.execute(id);
        var movement = new com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand(first, 1);
        legacyReserve.execute(movement);
        legacyConfirm.execute(movement);
        assertStock(first, 9, 0);
    }

    private StockReservation finalize(String operation, UUID id) {
        return operation.equals("release") ? release.execute(id) : confirm.execute(id);
    }
    private boolean attemptFinalize(String operation, UUID id) {
        try { finalize(operation, id); return true; }
        catch (InvalidStockReservationException expected) { return false; }
    }
    private ReserveOrderStockCommand command(UUID id, int amount) {
        return new ReserveOrderStockCommand(id, List.of(new StockReservationItem(first, amount)));
    }
    private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }
    private void stock(UUID id, int amount) {
        jdbc.update("INSERT INTO products(id,name,price,category,active) VALUES (?,'Mouse',10,'OTHER',true)", id);
        jdbc.update("INSERT INTO inventories(product_id,quantity,reserved_quantity) VALUES (?,?,0)", id, amount);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void assertStock(UUID product, int total, int reserved) {
        assertEquals(total, jdbc.queryForObject("SELECT quantity FROM inventories WHERE product_id=?", Integer.class, product));
        assertEquals(reserved, jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE product_id=?", Integer.class, product));
    }
    private void waitFor(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Signal timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try { Thread.sleep(20); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
        fail("Expected advisory-lock wait was not observed");
    }
    private boolean tryLockFromIndependentConnection(UUID id) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1, "stock-reservation:" + id);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    boolean acquired = result.getBoolean(1);
                    connection.rollback();
                    return acquired;
                }
            }
        }
    }
}
