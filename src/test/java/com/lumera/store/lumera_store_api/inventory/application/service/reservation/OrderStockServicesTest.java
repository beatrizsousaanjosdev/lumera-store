package com.lumera.store.lumera_store_api.inventory.application.service.reservation;

import com.lumera.store.lumera_store_api.inventory.application.command.ReserveOrderStockCommand;
import com.lumera.store.lumera_store_api.inventory.application.exception.*;
import com.lumera.store.lumera_store_api.inventory.application.port.out.*;
import com.lumera.store.lumera_store_api.inventory.application.service.*;
import com.lumera.store.lumera_store_api.inventory.domain.*;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderStockServicesTest {
    private final InventoryRepositoryPort inventory = mock(InventoryRepositoryPort.class);
    private final StockReservationRepositoryPort reservations = mock(StockReservationRepositoryPort.class);
    private final StockReservationEventOutboxPort events = mock(StockReservationEventOutboxPort.class);
    private final ReserveOrderStockService reserve = new ReserveOrderStockService(inventory, reservations, events);
    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private List<StockReservationItem> items() {
        return List.of(new StockReservationItem(second, 3), new StockReservationItem(first, 2));
    }

    @Test
    void shouldLockOrderThenValidateAllStocksBeforeReservingInSortedOrder() {
        UUID id = UUID.randomUUID();
        var a = Inventory.restore(first, 10, 0);
        var b = Inventory.restore(second, 10, 0);
        when(reservations.findByOrderId(id)).thenReturn(Optional.empty());
        when(inventory.findByProductIdForUpdate(first)).thenReturn(Optional.of(a));
        when(inventory.findByProductIdForUpdate(second)).thenReturn(Optional.of(b));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = reserve.execute(new ReserveOrderStockCommand(id, items()));
        assertEquals(StockReservationStatus.RESERVED, result.getStatus());
        assertEquals(2, a.getReservedQuantity());
        assertEquals(3, b.getReservedQuantity());
        var order = inOrder(reservations, inventory);
        order.verify(reservations).lockByOrderId(id);
        order.verify(reservations).findByOrderId(id);
        order.verify(inventory).findByProductIdForUpdate(first);
        order.verify(inventory).findByProductIdForUpdate(second);
        order.verify(inventory).save(a);
        order.verify(inventory).save(b);
        order.verify(reservations).save(result);
        verify(events).append(argThat(event -> event.orderId().equals(id) && event.reserved() && event.rejectionReason() == null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectWholeRequestWithoutSavingAnyStock(boolean missingInventory) {
        UUID id = UUID.randomUUID();
        var a = Inventory.restore(first, 10, 0);
        when(reservations.findByOrderId(id)).thenReturn(Optional.empty());
        when(inventory.findByProductIdForUpdate(first)).thenReturn(Optional.of(a));
        when(inventory.findByProductIdForUpdate(second)).thenReturn(missingInventory
                ? Optional.empty() : Optional.of(Inventory.restore(second, 2, 0)));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = reserve.execute(new ReserveOrderStockCommand(id, items()));
        assertEquals(StockReservationStatus.REJECTED, result.getStatus());
        assertNotNull(result.getRejectionReason());
        assertEquals(0, a.getReservedQuantity());
        verify(inventory, never()).save(any());
        verify(reservations).save(result);
        verify(events).append(argThat(event -> event.orderId().equals(id) && !event.reserved()
                && event.rejectionReason().equals(result.getRejectionReason())));
    }

    @ParameterizedTest
    @EnumSource(StockReservationStatus.class)
    void shouldReturnRecordedResultWithoutMovingStockForDuplicateRequest(StockReservationStatus status) {
        UUID id = UUID.randomUUID();
        var existing = StockReservation.restore(id, items(), Instant.now(), status,
                status == StockReservationStatus.REJECTED ? "No stock" : null);
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(existing));
        var reversed = new ArrayList<>(items());
        Collections.reverse(reversed);
        assertSame(existing, reserve.execute(new ReserveOrderStockCommand(id, reversed)));
        verifyNoInteractions(inventory);
        verify(reservations, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void shouldRejectChangedPayloadForSameOrder() {
        UUID id = UUID.randomUUID();
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(StockReservation.reserved(id, items())));
        assertThrows(InvalidStockReservationException.class,
                () -> reserve.execute(new ReserveOrderStockCommand(id, List.of(new StockReservationItem(first, 9)))));
        verifyNoInteractions(inventory);
        verify(reservations, never()).save(any());
        verifyNoInteractions(events);
    }

    private Function<UUID, StockReservation> finalizeService(String operation) {
        return operation.equals("release")
                ? new ReleaseOrderStockService(inventory, reservations, events)::execute
                : new ConfirmOrderStockService(inventory, reservations, events)::execute;
    }

    @ParameterizedTest
    @CsvSource({"release,10,0,RELEASED", "confirm,8,0,CONFIRMED"})
    void shouldFinalizeOnlyRecordedQuantitiesInConsistentLockOrder(
            String operation, int total, int remaining, StockReservationStatus status) {
        UUID id = UUID.randomUUID();
        var reservation = StockReservation.reserved(id, items());
        var a = Inventory.restore(first, 10, 2);
        var b = Inventory.restore(second, 10, 3);
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(reservation));
        when(inventory.findByProductIdForUpdate(first)).thenReturn(Optional.of(a));
        when(inventory.findByProductIdForUpdate(second)).thenReturn(Optional.of(b));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = finalizeService(operation).apply(id);
        assertEquals(status, result.getStatus());
        assertEquals(total, a.getQuantity());
        assertEquals(remaining, a.getReservedQuantity());
        assertEquals(operation.equals("release") ? 10 : 7, b.getQuantity());
        var order = inOrder(reservations, inventory);
        order.verify(reservations).lockByOrderId(id);
        order.verify(reservations).findByOrderId(id);
        order.verify(inventory).findByProductIdForUpdate(first);
        order.verify(inventory).save(a);
        order.verify(inventory).findByProductIdForUpdate(second);
        order.verify(inventory).save(b);
        order.verify(reservations).save(reservation);
        verify(events).appendFinalized(argThat(event -> event.orderId().equals(id)
                && event.confirmed() == operation.equals("confirm")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "confirm"})
    void shouldRejectNullOrMissingReservationBeforeAccessingStock(String operation) {
        var service = finalizeService(operation);
        assertThrows(InvalidStockReservationException.class, () -> service.apply(null));
        UUID id = UUID.randomUUID();
        when(reservations.findByOrderId(id)).thenReturn(Optional.empty());
        assertThrows(StockReservationNotFoundException.class, () -> service.apply(id));
        verifyNoInteractions(inventory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "confirm"})
    void shouldNotMoveStockForRepeatedFinalization(String operation) {
        UUID id = UUID.randomUUID();
        var result = StockReservation.reserved(id, items());
        if (operation.equals("release")) result.release(); else result.confirm();
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(result));
        assertSame(result, finalizeService(operation).apply(id));
        verifyNoInteractions(inventory);
        verify(reservations, never()).save(any());
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @CsvSource({"release,CONFIRMED", "release,REJECTED", "confirm,RELEASED", "confirm,REJECTED"})
    void shouldRejectIncompatibleFinalStateWithoutMovingStock(String operation, StockReservationStatus status) {
        UUID id = UUID.randomUUID();
        var result = StockReservation.restore(id, items(), Instant.now(), status,
                status == StockReservationStatus.REJECTED ? "No stock" : null);
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(result));
        assertThrows(InvalidStockReservationException.class, () -> finalizeService(operation).apply(id));
        verifyNoInteractions(inventory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "confirm"})
    void shouldNotSaveFinalStateIfInventoryIsMissing(String operation) {
        UUID id = UUID.randomUUID();
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(StockReservation.reserved(id, items())));
        when(inventory.findByProductIdForUpdate(first)).thenReturn(Optional.empty());
        assertThrows(InventoryNotFoundException.class, () -> finalizeService(operation).apply(id));
        verify(reservations, never()).save(any());
        verifyNoInteractions(events);
    }
}
