package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

import com.lumera.store.lumera_store_api.payment.adapter.in.messaging.*;
import com.lumera.store.lumera_store_api.payment.application.port.in.ProcessPaymentUsecase;
import com.lumera.store.lumera_store_api.payment.application.exception.ConflictingPaymentException;
import com.lumera.store.lumera_store_api.payment.domain.Payment;
import com.lumera.store.lumera_store_api.order.adapter.in.messaging.*;
import com.lumera.store.lumera_store_api.order.application.port.in.*;
import com.lumera.store.lumera_store_api.order.application.exception.*;
import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.inventory.adapter.in.messaging.*;
import com.lumera.store.lumera_store_api.inventory.application.port.in.*;
import com.lumera.store.lumera_store_api.inventory.domain.*;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutConsumersTest {
    final JsonMapper mapper=JsonMapper.builder().build();
    final IntegrationMessageDecoder decoder=new IntegrationMessageDecoder(mapper);
    final ProcessPaymentUsecase process=mock(ProcessPaymentUsecase.class);
    final ApplyPaymentResultUsecase apply=mock(ApplyPaymentResultUsecase.class);
    final CompleteOrderStockUsecase complete=mock(CompleteOrderStockUsecase.class);
    final ConfirmOrderStockUsecase confirm=mock(ConfirmOrderStockUsecase.class);
    final ReleaseOrderStockUsecase release=mock(ReleaseOrderStockUsecase.class);
    Consumer<ConsumerRecord<String,String>> consumer(int index) {
        return switch(index) {
            case 0 -> new PaymentRequestedConsumer(decoder,process)::consume;
            case 1 -> new PaymentProcessedConsumer(decoder,apply)::consume;
            case 2 -> new StockFinalizationRequestedConsumer(decoder,confirm,release)::consume;
            case 3 -> new StockFinalizedConsumer(decoder,complete)::consume;
            default -> throw new IllegalArgumentException();
        };
    }
    String payload(int index,UUID order,boolean approved) {
        UUID event=UUID.randomUUID();var now=Instant.now();
        Object message=switch(index) {
            case 0 -> new PaymentRequestedMessage(event,order,BigDecimal.TEN,now);
            case 1 -> new PaymentProcessedMessage(event,UUID.randomUUID(),order,BigDecimal.TEN,now,
                    approved?PaymentProcessedMessage.Status.APPROVED:PaymentProcessedMessage.Status.DECLINED);
            case 2 -> new StockFinalizationRequestedMessage(event,order,now,approved);
            case 3 -> new StockFinalizedMessage(event,order,now,approved);
            default -> throw new IllegalArgumentException();
        };
        return mapper.writeValueAsString(message);
    }
    ConsumerRecord<String,String> record(UUID key,String payload) {return new ConsumerRecord<>("events",0,0L,key.toString(),payload);}
    @ParameterizedTest @CsvSource({"0,true","1,true","1,false","2,true","2,false","3,true","3,false"})
    void shouldDelegateValidatedPayload(int index,boolean approved) {
        UUID id=UUID.randomUUID();
        when(process.execute(any())).thenReturn(new Payment(id,BigDecimal.TEN));
        when(apply.execute(any())).thenReturn(mock(Order.class));
        var reservation=StockReservation.reserved(id,List.of(new StockReservationItem(UUID.randomUUID(),1)));
        when(confirm.execute(any())).thenReturn(reservation);when(release.execute(any())).thenReturn(reservation);
        consumer(index).accept(record(id,payload(index,id,approved)));
        switch(index) {
            case 0 -> verify(process).execute(argThat(c -> c.orderId().equals(id) && c.amount().compareTo(BigDecimal.TEN)==0));
            case 1 -> verify(apply).execute(argThat(c -> c.orderId().equals(id) && c.approved()==approved));
            case 2 -> {if(approved) {verify(confirm).execute(id);verifyNoInteractions(release);}else {verify(release).execute(id);verifyNoInteractions(confirm);}}
            case 3 -> verify(complete).execute(argThat(c -> c.orderId().equals(id) && c.confirmed()==approved));
        }
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void shouldRejectMalformedMissingAndMismatchedKeys(int index) {
        UUID id=UUID.randomUUID();
        for(String body:Arrays.asList(null,"{","null","{}"))
            assertThrows(InvalidIntegrationMessageException.class,() -> consumer(index).accept(record(id,body)));
        assertThrows(InvalidIntegrationMessageException.class,() -> consumer(index).accept(record(UUID.randomUUID(),payload(index,id,true))));
        verifyNoInteractions(process,apply,complete,confirm,release);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void shouldPropagateTechnicalFailuresForRetry(int index) {
        UUID id=UUID.randomUUID();var failure=new org.springframework.dao.CannotAcquireLockException("Retry");
        when(process.execute(any())).thenThrow(failure);when(apply.execute(any())).thenThrow(failure);
        when(confirm.execute(any())).thenThrow(failure);when(complete.execute(any())).thenThrow(failure);
        assertSame(failure,assertThrows(org.springframework.dao.CannotAcquireLockException.class,
                () -> consumer(index).accept(record(id,payload(index,id,true)))));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void shouldClassifyBusinessConflictsAsPermanent(int index) {
        UUID id=UUID.randomUUID();
        when(process.execute(any())).thenThrow(new ConflictingPaymentException(id));
        when(apply.execute(any())).thenThrow(new ConflictingPaymentResultException(id));
        when(confirm.execute(any())).thenThrow(new InvalidStockReservationException("Already released"));
        when(complete.execute(any())).thenThrow(new ConflictingStockFinalizationException(id));
        assertThrows(InvalidIntegrationMessageException.class,() -> consumer(index).accept(record(id,payload(index,id,true))));
    }
    @Test void shouldRejectMissingActionAndPendingPaymentOutcome() {
        UUID id=UUID.randomUUID();
        for(int index:new int[]{2,3}) {
            String body=payload(index,id,true).replace(index==2?"\"confirm\":true":"\"confirmed\":true",
                    index==2?"\"confirm\":null":"\"confirmed\":null");
            final int route=index;
            assertThrows(InvalidIntegrationMessageException.class,() -> consumer(route).accept(record(id,body)));
        }
        String pending=payload(1,id,true).replace("APPROVED","PENDING");
        assertThrows(InvalidIntegrationMessageException.class,() -> consumer(1).accept(record(id,pending)));
        verifyNoInteractions(process,apply,complete,confirm,release);
    }
}
