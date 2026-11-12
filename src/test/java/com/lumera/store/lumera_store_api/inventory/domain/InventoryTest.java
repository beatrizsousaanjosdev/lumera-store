package com.lumera.store.lumera_store_api.inventory.domain;

import com.lumera.store.lumera_store_api.inventory.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {
    @Test
    void shouldInitializeWithZeroQuantities() {
        UUID id = UUID.randomUUID();
        var inventory = new Inventory(id);
        assertEquals(id, inventory.getProductId());
        assertState(inventory, 0, 0, 0);
    }

    @Test
    void shouldRejectNullProductId() {
        assertThrows(InvalidInventoryException.class, () -> new Inventory(null));
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "10,-1", "10,11"})
    void shouldRejectInvalidRestoredState(int quantity, int reserved) {
        assertThrows(InvalidInventoryException.class,
                () -> Inventory.restore(UUID.randomUUID(), quantity, reserved));
    }

    @Test
    void shouldAddReserveReleaseAndConfirm() {
        var inventory = new Inventory(UUID.randomUUID());
        inventory.addStock(10);
        assertState(inventory, 10, 0, 10);
        inventory.reserve(6);
        assertState(inventory, 10, 6, 4);
        inventory.release(2);
        assertState(inventory, 10, 4, 6);
        inventory.confirmReservation(4);
        assertState(inventory, 6, 0, 6);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveMovementsWithoutChangingState(int amount) {
        var inventory = Inventory.restore(UUID.randomUUID(), 10, 4);
        assertThrows(InvalidInventoryException.class, () -> inventory.addStock(amount));
        assertThrows(InvalidInventoryException.class, () -> inventory.reserve(amount));
        assertThrows(InvalidInventoryException.class, () -> inventory.release(amount));
        assertThrows(InvalidInventoryException.class, () -> inventory.confirmReservation(amount));
        assertState(inventory, 10, 4, 6);
    }

    @Test
    void shouldRejectReservationAboveAvailableQuantity() {
        var inventory = Inventory.restore(UUID.randomUUID(), 10, 4);
        assertThrows(InsufficientStockException.class, () -> inventory.reserve(7));
        assertState(inventory, 10, 4, 6);
        inventory.reserve(6);
        assertState(inventory, 10, 10, 0);
    }

    @Test
    void shouldRejectReleaseAndConfirmationAboveReservedQuantity() {
        var inventory = Inventory.restore(UUID.randomUUID(), 10, 4);
        assertThrows(InvalidInventoryException.class, () -> inventory.release(5));
        assertThrows(InvalidInventoryException.class, () -> inventory.confirmReservation(5));
        assertState(inventory, 10, 4, 6);
    }

    @Test
    void shouldRejectOverflowWithoutChangingQuantity() {
        var inventory = Inventory.restore(UUID.randomUUID(), Integer.MAX_VALUE, 0);
        assertThrows(InvalidInventoryException.class, () -> inventory.addStock(1));
        assertState(inventory, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    }

    private void assertState(Inventory inventory, int quantity, int reserved, int available) {
        assertAll(() -> assertEquals(quantity, inventory.getQuantity()),
                () -> assertEquals(reserved, inventory.getReservedQuantity()),
                () -> assertEquals(available, inventory.getAvailableQuantity()));
    }
}
