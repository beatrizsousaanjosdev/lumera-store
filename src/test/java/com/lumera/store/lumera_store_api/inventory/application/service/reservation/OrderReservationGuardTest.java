package com.lumera.store.lumera_store_api.inventory.application.service.reservation;

import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.exception.OrderManagedStockException;
import com.lumera.store.lumera_store_api.inventory.application.port.in.StockMovementUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.*;
import com.lumera.store.lumera_store_api.inventory.application.service.*;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderReservationGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {"reserve", "release", "confirm"})
    void shouldCheckOrderReservationsAfterRowLockAndBeforeSaving(String operation) {
        var inventory = mock(InventoryRepositoryPort.class);
        var reservations = mock(StockReservationRepositoryPort.class);
        var guard = new OrderReservationGuard(reservations);
        StockMovementUsecase service = switch (operation) {
            case "reserve" -> new ReserveStockService(inventory, guard);
            case "release" -> new ReleaseStockService(inventory, guard);
            default -> new ConfirmStockService(inventory, guard);
        };
        UUID id = UUID.randomUUID();
        var stock = Inventory.restore(id, 10, 3);
        when(inventory.findByProductIdForUpdate(id)).thenReturn(Optional.of(stock));
        when(reservations.hasActiveReservationForProduct(id)).thenReturn(true);
        assertThrows(OrderManagedStockException.class, () -> service.execute(new StockMovementCommand(id, 1)));
        var order = inOrder(inventory, reservations);
        order.verify(inventory).findByProductIdForUpdate(id);
        order.verify(reservations).hasActiveReservationForProduct(id);
        verify(inventory, never()).save(any());
        assertEquals(10, stock.getQuantity());
        assertEquals(3, stock.getReservedQuantity());
    }
}
