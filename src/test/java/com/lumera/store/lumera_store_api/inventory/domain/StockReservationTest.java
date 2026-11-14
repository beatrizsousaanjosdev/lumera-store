package com.lumera.store.lumera_store_api.inventory.domain;

import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StockReservationTest {
    private StockReservationItem item() { return new StockReservationItem(UUID.randomUUID(), 2); }

    @Test
    void shouldCreateReservedAndRejectedResults() {
        UUID orderId = UUID.randomUUID();
        var items = List.of(item());
        var reserved = StockReservation.reserved(orderId, items);
        assertEquals(orderId, reserved.getOrderId());
        assertEquals(StockReservationStatus.RESERVED, reserved.getStatus());
        assertNull(reserved.getRejectionReason());
        assertNotNull(reserved.getCreatedAt());
        var rejected = StockReservation.rejected(orderId, items, "  Insufficient stock  ");
        assertEquals(StockReservationStatus.REJECTED, rejected.getStatus());
        assertEquals("Insufficient stock", rejected.getRejectionReason());
    }

    @Test
    void shouldCopyItemsAndRestorePersistedState() {
        UUID orderId = UUID.randomUUID();
        var source = new ArrayList<>(List.of(item()));
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var reservation = StockReservation.restore(orderId, source, timestamp, StockReservationStatus.CONFIRMED, null);
        source.clear();
        assertEquals(1, reservation.getItems().size());
        assertEquals(timestamp, reservation.getCreatedAt());
        assertEquals(StockReservationStatus.CONFIRMED, reservation.getStatus());
        assertThrows(UnsupportedOperationException.class, () -> reservation.getItems().clear());
    }

    @Test
    void shouldRejectInvalidIdentityItemsAndRestoredState() {
        UUID id = UUID.randomUUID();
        var item = item();
        var items = List.of(item);
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.reserved(null, items));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.reserved(id, null));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.reserved(id, List.of()));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.reserved(id, Arrays.asList(item, null)));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.reserved(id, List.of(item, item)));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.restore(id, items, null, StockReservationStatus.RESERVED, null));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.restore(id, items, Instant.now(), null, null));
        assertThrows(InvalidStockReservationException.class, () -> StockReservation.restore(id, items, Instant.now(), StockReservationStatus.RESERVED, "reason"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRequireRejectionReason(String reason) {
        assertThrows(InvalidStockReservationException.class,
                () -> StockReservation.rejected(UUID.randomUUID(), List.of(item()), reason));
    }

    @ParameterizedTest
    @CsvSource({
            "RESERVED,release,RELEASED,true", "RELEASED,release,RELEASED,false",
            "CONFIRMED,release,ERROR,false", "REJECTED,release,ERROR,false",
            "RESERVED,confirm,CONFIRMED,true", "CONFIRMED,confirm,CONFIRMED,false",
            "RELEASED,confirm,ERROR,false", "REJECTED,confirm,ERROR,false"
    })
    void shouldEnforceTransitionsAndReportWhetherMovementIsRequired(
            StockReservationStatus initial, String operation, String expected, boolean changed) {
        var reservation = StockReservation.restore(UUID.randomUUID(), List.of(item()), Instant.now(), initial,
                initial == StockReservationStatus.REJECTED ? "No stock" : null);
        java.util.function.BooleanSupplier transition = operation.equals("release")
                ? reservation::release : reservation::confirm;
        if (expected.equals("ERROR")) {
            assertThrows(InvalidStockReservationException.class, transition::getAsBoolean);
            assertEquals(initial, reservation.getStatus());
        } else {
            assertEquals(changed, transition.getAsBoolean());
            assertEquals(StockReservationStatus.valueOf(expected), reservation.getStatus());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveItemQuantity(int amount) {
        assertThrows(InvalidStockReservationException.class, () -> new StockReservationItem(UUID.randomUUID(), amount));
    }

    @Test
    void shouldRejectMissingProductId() {
        assertThrows(InvalidStockReservationException.class, () -> new StockReservationItem(null, 1));
    }
}
