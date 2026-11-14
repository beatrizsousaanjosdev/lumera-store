package com.lumera.store.lumera_store_api.inventory.application.command;

import com.lumera.store.lumera_store_api.inventory.domain.StockReservationItem;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReserveOrderStockCommandTest {
    @Test
    void shouldRejectInvalidOrderAndItemLists() {
        UUID order = UUID.randomUUID();
        var item = new StockReservationItem(UUID.randomUUID(), 2);
        assertThrows(InvalidStockReservationException.class, () -> new ReserveOrderStockCommand(null, List.of(item)));
        assertThrows(InvalidStockReservationException.class, () -> new ReserveOrderStockCommand(order, null));
        assertThrows(InvalidStockReservationException.class, () -> new ReserveOrderStockCommand(order, List.of()));
        assertThrows(InvalidStockReservationException.class, () -> new ReserveOrderStockCommand(order, Arrays.asList(item, null)));
        assertThrows(InvalidStockReservationException.class, () -> new ReserveOrderStockCommand(order,
                List.of(item, new StockReservationItem(item.productId(), 3))));
    }

    @Test
    void shouldDefensivelyCopyItems() {
        var items = new ArrayList<>(List.of(new StockReservationItem(UUID.randomUUID(), 2)));
        var command = new ReserveOrderStockCommand(UUID.randomUUID(), items);
        items.clear();
        assertEquals(1, command.items().size());
        assertThrows(UnsupportedOperationException.class, () -> command.items().clear());
    }
}
