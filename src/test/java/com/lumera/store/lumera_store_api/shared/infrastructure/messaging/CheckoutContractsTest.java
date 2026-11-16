package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

import com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand;
import com.lumera.store.lumera_store_api.payment.application.event.PaymentProcessedEvent;
import com.lumera.store.lumera_store_api.payment.domain.*;
import com.lumera.store.lumera_store_api.payment.domain.exception.InvalidPaymentException;
import com.lumera.store.lumera_store_api.order.application.command.ApplyPaymentResultCommand;
import com.lumera.store.lumera_store_api.order.application.event.*;
import com.lumera.store.lumera_store_api.order.domain.*;
import com.lumera.store.lumera_store_api.inventory.application.event.StockFinalizedEvent;
import com.lumera.store.lumera_store_api.inventory.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutContractsTest {
    @ParameterizedTest @ValueSource(strings={"0","-1","10.001"})
    void shouldRejectInvalidAmountsBeforeProcessing(String value) {
        UUID id=UUID.randomUUID();var amount=new BigDecimal(value);
        assertThrows(InvalidPaymentException.class,() -> new ProcessPaymentCommand(id,amount));
        assertThrows(RuntimeException.class,() -> new ApplyPaymentResultCommand(id,id,amount,true));
    }
    @Test void shouldNormalizeMoneyAndRequireIdentity() {
        UUID id=UUID.randomUUID();
        assertEquals(new BigDecimal("10.00"),new ProcessPaymentCommand(id,BigDecimal.TEN).amount());
        assertEquals(new BigDecimal("10.00"),new ApplyPaymentResultCommand(id,id,BigDecimal.TEN,true).amount());
        assertThrows(InvalidPaymentException.class,() -> new ProcessPaymentCommand(null,BigDecimal.TEN));
        assertThrows(NullPointerException.class,() -> new ApplyPaymentResultCommand(id,null,BigDecimal.TEN,true));
    }
    @ParameterizedTest @EnumSource(PaymentStatus.class)
    void shouldPublishOnlyFinalPaymentOutcome(PaymentStatus status) {
        var payment=Payment.restore(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.TEN,Instant.now(),status);
        if(status==PaymentStatus.PENDING) assertThrows(IllegalArgumentException.class,() -> PaymentProcessedEvent.create(payment));
        else {
            var event=PaymentProcessedEvent.create(payment);
            assertEquals(payment.getId(),event.paymentId());assertEquals(payment.getOrderId(),event.orderId());
            assertEquals(status,event.status());assertNotNull(event.eventId());
        }
    }
    @ParameterizedTest @EnumSource(OrderStatus.class)
    void shouldCreateEventsOnlyForCorrespondingStage(OrderStatus status) {
        var order=Order.restore(UUID.randomUUID(),List.of(new OrderItem(UUID.randomUUID(),"Mouse",2,BigDecimal.TEN)),Instant.now(),status);
        if(status==OrderStatus.CONFIRMED || status==OrderStatus.CANCELLED)
            assertEquals(status,OrderCompletedEvent.create(order).status());
        else assertThrows(IllegalArgumentException.class,() -> OrderCompletedEvent.create(order));
        if(status==OrderStatus.PENDING_PAYMENT) assertEquals(order.getTotal(),PaymentRequestedEvent.create(order).amount());
        else assertThrows(com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException.class,() -> PaymentRequestedEvent.create(order));
        if(status==OrderStatus.PENDING_STOCK_CONFIRMATION || status==OrderStatus.PENDING_STOCK_RELEASE)
            assertEquals(status==OrderStatus.PENDING_STOCK_CONFIRMATION,StockFinalizationRequestedEvent.create(order).confirm());
        else assertThrows(IllegalArgumentException.class,() -> StockFinalizationRequestedEvent.create(order));
    }
    @ParameterizedTest @EnumSource(StockReservationStatus.class)
    void shouldCreateFinalStockEventsOnlyAfterFinalization(StockReservationStatus status) {
        var reservation=StockReservation.restore(UUID.randomUUID(),List.of(new StockReservationItem(UUID.randomUUID(),1)),Instant.now(),status,
                status==StockReservationStatus.REJECTED?"No stock":null);
        if(status==StockReservationStatus.CONFIRMED || status==StockReservationStatus.RELEASED)
            assertEquals(status==StockReservationStatus.CONFIRMED,StockFinalizedEvent.create(reservation).confirmed());
        else assertThrows(IllegalArgumentException.class,() -> StockFinalizedEvent.create(reservation));
    }
}
