package com.lumera.store.lumera_store_api.order.application.command;

import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CreateOrderCommandTest {
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectNonPositiveQuantity(int amount) {
        assertThrows(InvalidOrderException.class, () -> new CreateOrderItemCommand(UUID.randomUUID(), amount));
    }

    @Test
    void shouldRejectMissingProductIdAndInvalidLists() {
        assertThrows(InvalidOrderException.class, () -> new CreateOrderItemCommand(null, 1));
        var item = new CreateOrderItemCommand(UUID.randomUUID(), 1);
        assertThrows(InvalidOrderException.class, () -> new CreateOrderCommand(null));
        assertThrows(InvalidOrderException.class, () -> new CreateOrderCommand(List.of()));
        assertThrows(InvalidOrderException.class, () -> new CreateOrderCommand(Arrays.asList(item, null)));
        assertThrows(InvalidOrderException.class, () -> new CreateOrderCommand(List.of(item, item)));
    }

    @Test
    void shouldDefensivelyCopyItems() {
        var source = new ArrayList<>(List.of(new CreateOrderItemCommand(UUID.randomUUID(), 2)));
        var command = new CreateOrderCommand(source);
        source.clear();
        assertEquals(1, command.items().size());
        assertThrows(UnsupportedOperationException.class, () -> command.items().clear());
    }
}
