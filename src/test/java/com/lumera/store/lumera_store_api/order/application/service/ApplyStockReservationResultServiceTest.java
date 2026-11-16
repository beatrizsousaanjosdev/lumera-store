package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.StockReservationResultCommand;
import com.lumera.store.lumera_store_api.order.application.exception.*;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderRepositoryPort;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderPaymentResultPort;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderEventOutboxPort;
import com.lumera.store.lumera_store_api.order.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplyStockReservationResultServiceTest {
    @ParameterizedTest
    @CsvSource({"PENDING_STOCK,true,PENDING_PAYMENT", "PENDING_STOCK,false,CANCELLED",
            "PENDING_PAYMENT,true,PENDING_PAYMENT", "PENDING_PAYMENT,false,ERROR",
            "PENDING_STOCK_CONFIRMATION,true,PENDING_STOCK_CONFIRMATION",
            "PENDING_STOCK_RELEASE,true,PENDING_STOCK_RELEASE",
            "PENDING_STOCK_CONFIRMATION,false,ERROR", "PENDING_STOCK_RELEASE,false,ERROR",
            "CONFIRMED,true,CONFIRMED", "CONFIRMED,false,ERROR",
            "CANCELLED,false,CANCELLED", "CANCELLED,true,ERROR"})
    void shouldApplyInitialDecisionIgnoreDuplicatesAndRejectConflicts(OrderStatus initial, boolean reserved, String expected) {
        var port = mock(OrderRepositoryPort.class);
        UUID id = UUID.randomUUID();
        var order = Order.restore(id, List.of(new OrderItem(UUID.randomUUID(), "Mouse", 2, BigDecimal.TEN)), Instant.now(), initial);
        when(port.findByIdForUpdate(id)).thenReturn(Optional.of(order));
        when(port.save(order)).thenReturn(order);
        var events = mock(OrderEventOutboxPort.class);
        var service = new ApplyStockReservationResultService(port, events, mock(OrderPaymentResultPort.class));
        var command = new StockReservationResultCommand(id, reserved, reserved ? null : "No stock");
        if (expected.equals("ERROR")) {
            assertThrows(ConflictingStockReservationResultException.class, () -> service.execute(command));
            assertEquals(initial, order.getStatus());
            verify(port, never()).save(any());
        } else {
            assertSame(order, service.execute(command));
            assertEquals(OrderStatus.valueOf(expected), order.getStatus());
            if (initial == OrderStatus.PENDING_STOCK) verify(port).save(order);
            else verify(port, never()).save(any());
        }
        if (initial == OrderStatus.PENDING_STOCK && reserved) {
            verify(events).appendPaymentRequested(argThat(event -> event.orderId().equals(id)
                    && event.amount().compareTo(order.getTotal()) == 0));
        } else if (initial == OrderStatus.PENDING_STOCK) {
            verify(events).appendCompleted(argThat(event -> event.orderId().equals(id) && event.status() == OrderStatus.CANCELLED));
        } else verifyNoInteractions(events);
        assertEquals(new BigDecimal("20.00"), order.getTotal());
        verify(port).findByIdForUpdate(id);
    }

    @Test
    void shouldReportMissingOrder() {
        var port = mock(OrderRepositoryPort.class);
        UUID id = UUID.randomUUID();
        when(port.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class,
                () -> new ApplyStockReservationResultService(port, mock(OrderEventOutboxPort.class), mock(OrderPaymentResultPort.class)).execute(new StockReservationResultCommand(id, true, null)));
        verify(port, never()).save(any());
    }
}
