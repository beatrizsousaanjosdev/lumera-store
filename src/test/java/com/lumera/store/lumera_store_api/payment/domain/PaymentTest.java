package com.lumera.store.lumera_store_api.payment.domain;

import com.lumera.store.lumera_store_api.payment.domain.exception.InvalidPaymentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {
    @Test void shouldCreatePendingAndRestoreIdentity() {
        UUID order=UUID.randomUUID();
        var payment=new Payment(order,BigDecimal.TEN);
        assertEquals(PaymentStatus.PENDING,payment.getStatus());
        assertEquals(new BigDecimal("10.00"),payment.getAmount());
        var restored=Payment.restore(payment.getId(),order,payment.getAmount(),payment.getCreatedAt(),PaymentStatus.APPROVED);
        assertEquals(payment.getId(),restored.getId());
        assertEquals(payment.getCreatedAt(),restored.getCreatedAt());
        assertEquals(order,restored.getOrderId());
        assertEquals(PaymentStatus.APPROVED,restored.getStatus());
    }
    @ParameterizedTest @CsvSource({"PENDING,true,APPROVED", "PENDING,false,DECLINED",
            "APPROVED,true,APPROVED", "DECLINED,false,DECLINED", "APPROVED,false,ERROR", "DECLINED,true,ERROR"})
    void shouldEnforceOutcomeAndIdempotency(PaymentStatus initial,boolean approve,String expected) {
        var payment=Payment.restore(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.TEN,Instant.now(),initial);
        if(expected.equals("ERROR")) {
            assertThrows(InvalidPaymentException.class,() -> {if(approve) payment.approve();else payment.decline();});
            assertEquals(initial,payment.getStatus());
        } else {
            assertEquals(initial==PaymentStatus.PENDING,approve?payment.approve():payment.decline());
            assertEquals(PaymentStatus.valueOf(expected),payment.getStatus());
        }
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.001"})
    void shouldRejectInvalidAmount(String value) {
        assertThrows(InvalidPaymentException.class,() -> new Payment(UUID.randomUUID(),new BigDecimal(value)));
    }
    @Test void shouldRejectMissingFields() {
        UUID id=UUID.randomUUID();var now=Instant.now();
        assertThrows(InvalidPaymentException.class,() -> new Payment(null,BigDecimal.TEN));
        assertThrows(InvalidPaymentException.class,() -> new Payment(id,null));
        assertThrows(InvalidPaymentException.class,() -> Payment.restore(null,id,BigDecimal.TEN,now,PaymentStatus.PENDING));
        assertThrows(InvalidPaymentException.class,() -> Payment.restore(id,id,BigDecimal.TEN,null,PaymentStatus.PENDING));
        assertThrows(InvalidPaymentException.class,() -> Payment.restore(id,id,BigDecimal.TEN,now,null));
    }
}
