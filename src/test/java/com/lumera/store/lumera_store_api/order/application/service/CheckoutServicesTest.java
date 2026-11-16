package com.lumera.store.lumera_store_api.order.application.service;

import com.lumera.store.lumera_store_api.order.application.command.*;
import com.lumera.store.lumera_store_api.order.application.exception.*;
import com.lumera.store.lumera_store_api.order.application.port.out.*;
import com.lumera.store.lumera_store_api.order.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutServicesTest {
    final OrderRepositoryPort repository=mock(OrderRepositoryPort.class);
    final OrderEventOutboxPort events=mock(OrderEventOutboxPort.class);
    final OrderPaymentResultPort results=mock(OrderPaymentResultPort.class);
    final ApplyPaymentResultService apply=new ApplyPaymentResultService(repository,events,results);
    Order order(OrderStatus status) {
        var order=Order.restore(UUID.randomUUID(),List.of(new OrderItem(UUID.randomUUID(),"Mouse",2,BigDecimal.TEN)),Instant.now(),status);
        when(repository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(repository.save(order)).thenReturn(order);return order;
    }
    ApplyPaymentResultCommand result(Order order,boolean approved) {
        return new ApplyPaymentResultCommand(order.getId(),UUID.randomUUID(),order.getTotal(),approved);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void shouldRecordOutcomeAndRequestFinalizationOnlyOnce(boolean approved) {
        var order=order(OrderStatus.PENDING_PAYMENT);var command=result(order,approved);
        assertSame(order,apply.execute(command));
        assertEquals(approved?OrderStatus.PENDING_STOCK_CONFIRMATION:OrderStatus.PENDING_STOCK_RELEASE,order.getStatus());
        var sequence=inOrder(repository,results,events);
        sequence.verify(repository).findByIdForUpdate(order.getId());
        sequence.verify(results).findByOrderId(order.getId());
        sequence.verify(results).save(command);sequence.verify(repository).save(order);
        sequence.verify(events).appendStockFinalizationRequested(argThat(e -> e.orderId().equals(order.getId()) && e.confirm()==approved));
        when(results.findByOrderId(order.getId())).thenReturn(Optional.of(command));
        apply.execute(command);
        verify(events,times(1)).appendStockFinalizationRequested(any());
    }
    @ParameterizedTest @EnumSource(OrderStatus.class)
    void shouldRejectFirstResultOutsidePaymentStage(OrderStatus status) {
        if(status==OrderStatus.PENDING_PAYMENT) return;
        var order=order(status);
        assertThrows(ConflictingPaymentResultException.class,() -> apply.execute(result(order,true)));
        verifyNoInteractions(events);verify(results,never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"identity","amount","decision"})
    void shouldRejectConflictingReplay(String change) {
        var order=order(OrderStatus.CONFIRMED);var old=result(order,true);
        when(results.findByOrderId(order.getId())).thenReturn(Optional.of(old));
        var conflicting=new ApplyPaymentResultCommand(order.getId(),change.equals("identity")?UUID.randomUUID():old.paymentId(),
                change.equals("amount")?BigDecimal.ONE:old.amount(),!change.equals("decision"));
        assertThrows(ConflictingPaymentResultException.class,() -> apply.execute(conflicting));verifyNoInteractions(events);
    }
    @ParameterizedTest @CsvSource({"PENDING_STOCK_CONFIRMATION,true,CONFIRMED", "CONFIRMED,true,CONFIRMED",
            "PENDING_STOCK_RELEASE,false,CANCELLED", "CANCELLED,false,CANCELLED",
            "PENDING_PAYMENT,true,ERROR", "PENDING_PAYMENT,false,ERROR", "PENDING_STOCK_RELEASE,true,ERROR", "PENDING_STOCK_CONFIRMATION,false,ERROR",
            "CANCELLED,true,ERROR", "CONFIRMED,false,ERROR"})
    void shouldCompleteOnlyMatchingStockOutcome(OrderStatus initial,boolean confirmed,String expected) {
        var order=order(initial);var complete=new CompleteOrderStockService(repository, events);
        var command=new CompleteOrderStockCommand(order.getId(),confirmed);
        if(expected.equals("ERROR")) {
            assertThrows(ConflictingStockFinalizationException.class,() -> complete.execute(command));
            assertEquals(initial,order.getStatus());verify(repository,never()).save(any());
        } else {
            assertSame(order,complete.execute(command));assertEquals(OrderStatus.valueOf(expected),order.getStatus());
            if(initial==order.getStatus()) {
                verify(repository,never()).save(any());verifyNoInteractions(events);
            } else {
                verify(repository).save(order);
                verify(events).appendCompleted(argThat(event -> event.orderId().equals(order.getId()) && event.status()==order.getStatus()));
            }
        }
    }
    @Test void shouldReportMissingOrder() {
        UUID id=UUID.randomUUID();
        assertThrows(OrderNotFoundException.class,() -> apply.execute(new ApplyPaymentResultCommand(id,id,BigDecimal.TEN,true)));
        assertThrows(OrderNotFoundException.class,() -> new CompleteOrderStockService(repository, events).execute(new CompleteOrderStockCommand(id,true)));
        verifyNoInteractions(events,results);
    }
}
