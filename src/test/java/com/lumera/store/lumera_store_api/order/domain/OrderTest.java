package com.lumera.store.lumera_store_api.order.domain;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "PENDING_STOCK,stock,PENDING_PAYMENT", "PENDING_PAYMENT,stock,PENDING_PAYMENT",
            "CONFIRMED,stock,ERROR", "CANCELLED,stock,ERROR",
            "PENDING_STOCK,payment,ERROR", "PENDING_PAYMENT,payment,CONFIRMED",
            "CONFIRMED,payment,CONFIRMED", "CANCELLED,payment,ERROR",
            "PENDING_STOCK,cancel,CANCELLED", "PENDING_PAYMENT,cancel,CANCELLED",
            "CONFIRMED,cancel,ERROR", "CANCELLED,cancel,CANCELLED"
    })
    void shouldEnforceStateMachine(OrderStatus initial, String operation, String expected) {
        var order = Order.restore(UUID.randomUUID(), List.of(item()), Instant.now(), initial);
        Runnable transition = switch (operation) {
            case "stock" -> order::markStockReserved;
            case "payment" -> order::confirmPayment;
            case "cancel" -> order::cancel;
            default -> throw new IllegalArgumentException(operation);
        };
        if (expected.equals("ERROR")) {
            assertThrows(InvalidOrderException.class, transition::run);
            assertEquals(initial, order.getStatus());
        } else {
            transition.run();
            assertEquals(OrderStatus.valueOf(expected), order.getStatus());
        }
    }
}
