package com.lumera.store.lumera_store_api.order.domain;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {
    @Test
    void shouldNormalizeSnapshotAndCalculateSubtotal() {
        var item = new OrderItem(UUID.randomUUID(), "  Mouse  ", 3, new BigDecimal("10.5"));
        assertEquals("Mouse", item.productName());
        assertEquals(new BigDecimal("10.50"), item.unitPrice());
        assertEquals(new BigDecimal("31.50"), item.subTotal());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectMissingOrBlankName(String name) {
        assertThrows(InvalidOrderException.class,
                () -> new OrderItem(UUID.randomUUID(), name, 1, BigDecimal.ONE));
    }

    @Test
    void shouldRejectLongNameAndMissingProduct() {
        assertThrows(InvalidOrderException.class,
                () -> new OrderItem(UUID.randomUUID(), "a".repeat(151), 1, BigDecimal.ONE));
        assertThrows(InvalidOrderException.class, () -> new OrderItem(null, "Mouse", 1, BigDecimal.ONE));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveQuantity(int quantity) {
        assertThrows(InvalidOrderException.class,
                () -> new OrderItem(UUID.randomUUID(), "Mouse", quantity, BigDecimal.ONE));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "100000000000000000.00"})
    void shouldRejectInvalidPrice(String price) {
        assertThrows(InvalidOrderException.class, () -> new OrderItem(UUID.randomUUID(), "Mouse", 1,
                price == null ? null : new BigDecimal(price)));
    }

    @Test
    void shouldAcceptDatabasePriceBoundaryAndExactTrailingZeros() {
        assertEquals(new BigDecimal("99999999999999999.99"),
                new OrderItem(UUID.randomUUID(), "Mouse", 1, new BigDecimal("99999999999999999.99")).unitPrice());
        assertEquals(new BigDecimal("10.00"),
                new OrderItem(UUID.randomUUID(), "Mouse", 1, new BigDecimal("10.000")).unitPrice());
    }
}
