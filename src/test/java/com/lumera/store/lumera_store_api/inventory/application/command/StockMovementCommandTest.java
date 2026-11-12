package com.lumera.store.lumera_store_api.inventory.application.command;

import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidInventoryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StockMovementCommandTest {
    @Test
    void shouldRejectMissingProductId() {
        assertThrows(InvalidInventoryException.class, () -> new StockMovementCommand(null, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveAmount(int amount) {
        assertThrows(InvalidInventoryException.class,
                () -> new StockMovementCommand(UUID.randomUUID(), amount));
    }
}
