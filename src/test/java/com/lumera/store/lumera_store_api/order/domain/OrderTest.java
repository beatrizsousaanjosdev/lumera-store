package com.lumera.store.lumera_store_api.order.domain;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    private OrderItem item() {
        return new OrderItem(UUID.randomUUID(), "Mouse", 2, new BigDecimal("10.25"));
    }

    @Test
    void shouldCreatePendingOrderAndProtectItems() {
        var source = new ArrayList<>(List.of(item(),
                new OrderItem(UUID.randomUUID(), "Keyboard", 3, new BigDecimal("2.50"))));
        var order = new Order(source);
        source.clear();
        assertNotNull(order.getId());
        assertNotNull(order.getCreatedAt());
        assertEquals(OrderStatus.PENDING_STOCK, order.getStatus());
        assertEquals(2, order.getItems().size());
        assertEquals(new BigDecimal("28.00"), order.getTotal());
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().add(item()));
    }

    @Test
    void shouldRejectMissingNullOrDuplicateItems() {
        var item = item();
        assertThrows(InvalidOrderException.class, () -> new Order(null));
        assertThrows(InvalidOrderException.class, () -> new Order(List.of()));
        assertThrows(InvalidOrderException.class, () -> new Order(Arrays.asList(item, null)));
        assertThrows(InvalidOrderException.class, () -> new Order(List.of(item, item)));
    }

    @Test
    void shouldRestoreIdentityTimestampAndStatus() {
        UUID id = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var items = List.of(item());
        var order = Order.restore(id, items, timestamp, OrderStatus.CONFIRMED);
        assertEquals(id, order.getId());
        assertEquals(timestamp, order.getCreatedAt());
        assertEquals(items, order.getItems());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertThrows(InvalidOrderException.class, () -> Order.restore(null, items, timestamp, OrderStatus.CONFIRMED));
        assertThrows(InvalidOrderException.class, () -> Order.restore(id, items, null, OrderStatus.CONFIRMED));
        assertThrows(InvalidOrderException.class, () -> Order.restore(id, items, timestamp, null));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        var allowed = Map.of(
                "stock", Map.of(OrderStatus.PENDING_STOCK, OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_PAYMENT),
                "approve", Map.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_STOCK_CONFIRMATION,
                        OrderStatus.PENDING_STOCK_CONFIRMATION, OrderStatus.PENDING_STOCK_CONFIRMATION, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED),
                "decline", Map.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_STOCK_RELEASE, OrderStatus.PENDING_STOCK_RELEASE, OrderStatus.PENDING_STOCK_RELEASE),
                "withdraw", Map.of(OrderStatus.PENDING_STOCK_CONFIRMATION, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED),
                "release", Map.of(OrderStatus.PENDING_STOCK_RELEASE, OrderStatus.CANCELLED, OrderStatus.CANCELLED, OrderStatus.CANCELLED),
                "cancel", Map.of(OrderStatus.PENDING_STOCK, OrderStatus.CANCELLED, OrderStatus.CANCELLED, OrderStatus.CANCELLED));
        return Arrays.stream(OrderStatus.values()).flatMap(initial -> allowed.entrySet().stream()
                .map(entry -> org.junit.jupiter.params.provider.Arguments.of(initial, entry.getKey(), entry.getValue().get(initial))));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("transitions")
    void shouldEnforceEveryStateTransition(OrderStatus initial, String operation, OrderStatus expected) {
        var order = Order.restore(UUID.randomUUID(), List.of(item()), Instant.now(), initial);
        Runnable transition = switch (operation) {
            case "stock" -> order::markStockReserved;
            case "approve" -> order::markPaymentApproved;
            case "decline" -> order::markPaymentDeclined;
            case "withdraw" -> order::confirmStockWithdrawal;
            case "release" -> order::completeStockRelease;
            case "cancel" -> order::cancel;
            default -> throw new IllegalArgumentException(operation);
        };
        if (expected == null) {
            assertThrows(InvalidOrderException.class, transition::run);
            assertEquals(initial, order.getStatus());
        } else {
            transition.run();
            assertEquals(expected, order.getStatus());
        }
    }
}
