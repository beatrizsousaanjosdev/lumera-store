package com.lumera.store.lumera_store_api.inventory.application.service;

import com.lumera.store.lumera_store_api.inventory.application.command.StockMovementCommand;
import com.lumera.store.lumera_store_api.inventory.application.exception.*;
import com.lumera.store.lumera_store_api.inventory.application.port.in.StockMovementUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.out.InventoryRepositoryPort;
import com.lumera.store.lumera_store_api.inventory.domain.Inventory;
import com.lumera.store.lumera_store_api.inventory.domain.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServicesTest {
    private final InventoryRepositoryPort port = mock(InventoryRepositoryPort.class);

    private StockMovementUsecase service(String operation) {
        return switch (operation) {
            case "add" -> new AddStockService(port);
            case "reserve" -> new ReserveStockService(port);
            case "release" -> new ReleaseStockService(port);
            case "confirm" -> new ConfirmStockService(port);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    @ParameterizedTest
    @CsvSource({"add,12,4", "reserve,10,6", "release,10,2", "confirm,8,2"})
    void shouldLockApplyMovementAndSave(String operation, int quantity, int reserved) {
        UUID id = UUID.randomUUID();
        var inventory = Inventory.restore(id, 10, 4);
        var saved = Inventory.restore(id, quantity, reserved);
        when(port.findByProductIdForUpdate(id)).thenReturn(Optional.of(inventory));
        when(port.save(inventory)).thenReturn(saved);
        assertSame(saved, service(operation).execute(new StockMovementCommand(id, 2)));
        assertEquals(quantity, inventory.getQuantity());
        assertEquals(reserved, inventory.getReservedQuantity());
        var order = inOrder(port);
        order.verify(port).findByProductIdForUpdate(id);
        order.verify(port).save(inventory);
        verifyNoMoreInteractions(port);
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "reserve", "release", "confirm"})
    void shouldNotSaveMissingInventory(String operation) {
        UUID id = UUID.randomUUID();
        when(port.findByProductIdForUpdate(id)).thenReturn(Optional.empty());
        assertThrows(InventoryNotFoundException.class,
                () -> service(operation).execute(new StockMovementCommand(id, 1)));
        verify(port, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "release", "confirm"})
    void shouldNotSaveWhenMovementViolatesDomainRule(String operation) {
        UUID id = UUID.randomUUID();
        var inventory = Inventory.restore(id, 10, 4);
        when(port.findByProductIdForUpdate(id)).thenReturn(Optional.of(inventory));
        Class<? extends RuntimeException> expected = operation.equals("reserve")
                ? InsufficientStockException.class : InvalidInventoryException.class;
        assertThrows(expected,
                () -> service(operation).execute(new StockMovementCommand(id, 11)));
        verify(port, never()).save(any());
        assertEquals(10, inventory.getQuantity());
        assertEquals(4, inventory.getReservedQuantity());
    }

    @Test
    void shouldNotSaveOnQuantityOverflow() {
        UUID id = UUID.randomUUID();
        var inventory = Inventory.restore(id, Integer.MAX_VALUE, 0);
        when(port.findByProductIdForUpdate(id)).thenReturn(Optional.of(inventory));
        assertThrows(InvalidInventoryException.class,
                () -> new AddStockService(port).execute(new StockMovementCommand(id, 1)));
        verify(port, never()).save(any());
    }

    @Test
    void shouldInitializeWithoutOverwritingExistingStock() {
        UUID id = UUID.randomUUID();
        var inventory = Inventory.restore(id, 10, 3);
        when(port.findByProductId(id)).thenReturn(Optional.of(inventory));
        assertSame(inventory, new InitializeInventoryService(port).execute(id));
        var order = inOrder(port);
        order.verify(port).initializeIfAbsent(id);
        order.verify(port).findByProductId(id);
        verifyNoMoreInteractions(port);
    }

    @Test
    void shouldReportMissingInventoryAfterInitialization() {
        UUID id = UUID.randomUUID();
        when(port.findByProductId(id)).thenReturn(Optional.empty());
        assertThrows(InventoryInitializationException.class,
                () -> new InitializeInventoryService(port).execute(id));
    }

    @Test
    void shouldFindInventoryWithoutLocking() {
        UUID id = UUID.randomUUID();
        var inventory = new Inventory(id);
        when(port.findByProductId(id)).thenReturn(Optional.of(inventory));
        assertSame(inventory, new FindInventoryByProductIdService(port).execute(id));
        verify(port).findByProductId(id);
        verifyNoMoreInteractions(port);
    }

    @Test
    void shouldReportMissingInventoryOnLookup() {
        UUID id = UUID.randomUUID();
        when(port.findByProductId(id)).thenReturn(Optional.empty());
        assertThrows(InventoryNotFoundException.class,
                () -> new FindInventoryByProductIdService(port).execute(id));
    }

    @Test
    void shouldRejectMissingIdBeforeAccessingRepository() {
        assertThrows(InvalidInventoryException.class, () -> new InitializeInventoryService(port).execute(null));
        assertThrows(InvalidInventoryException.class, () -> new FindInventoryByProductIdService(port).execute(null));
        verifyNoInteractions(port);
    }
}
