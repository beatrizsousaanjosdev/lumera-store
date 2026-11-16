package com.lumera.store.lumera_store_api.payment.application.service;

import com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand;
import com.lumera.store.lumera_store_api.payment.application.exception.ConflictingPaymentException;
import com.lumera.store.lumera_store_api.payment.application.port.out.*;
import com.lumera.store.lumera_store_api.payment.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessPaymentServiceTest {
    final PaymentRepositoryPort repository=mock(PaymentRepositoryPort.class);
    final PaymentGatewayPort gateway=mock(PaymentGatewayPort.class);
    final PaymentEventOutboxPort events=mock(PaymentEventOutboxPort.class);
    final ProcessPaymentService service=new ProcessPaymentService(repository,gateway,events);
    @ParameterizedTest @EnumSource(PaymentDecision.class)
    void shouldLockProcessAndStageResultInOrder(PaymentDecision decision) {
        UUID order=UUID.randomUUID();
        when(gateway.charge(any(),eq(order),eq(new BigDecimal("10.00")))).thenReturn(decision);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved=service.execute(new ProcessPaymentCommand(order,BigDecimal.TEN));
        assertEquals(PaymentStatus.valueOf(decision.name()),saved.getStatus());
        var sequence=inOrder(repository,gateway,events);
        sequence.verify(repository).lockByOrderId(order);
        sequence.verify(repository).findByOrderId(order);
        sequence.verify(gateway).charge(saved.getId(),order,saved.getAmount());
        sequence.verify(repository).save(saved);
        sequence.verify(events).append(argThat(e -> e.paymentId().equals(saved.getId()) && e.status()==saved.getStatus()));
    }
    @ParameterizedTest @EnumSource(value=PaymentStatus.class,names={"APPROVED","DECLINED"})
    void shouldReturnRecordedOutcomeWithoutChargingOrPublishing(PaymentStatus status) {
        UUID order=UUID.randomUUID();
        var existing=Payment.restore(UUID.randomUUID(),order,BigDecimal.TEN,Instant.now(),status);
        when(repository.findByOrderId(order)).thenReturn(Optional.of(existing));
        assertSame(existing,service.execute(new ProcessPaymentCommand(order,new BigDecimal("10.00"))));
        verifyNoInteractions(gateway,events);verify(repository,never()).save(any());
    }
    @Test void shouldRejectChangedAmountBeforeGateway() {
        UUID order=UUID.randomUUID();when(repository.findByOrderId(order)).thenReturn(Optional.of(new Payment(order,BigDecimal.ONE)));
        assertThrows(ConflictingPaymentException.class,() -> service.execute(new ProcessPaymentCommand(order,BigDecimal.TEN)));
        verifyNoInteractions(gateway,events);
    }
    @Test void shouldResumePendingWithSamePaymentIdentity() {
        UUID order=UUID.randomUUID();var pending=new Payment(order,BigDecimal.TEN);
        when(repository.findByOrderId(order)).thenReturn(Optional.of(pending));
        when(gateway.charge(any(),any(),any())).thenReturn(PaymentDecision.APPROVED);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertSame(pending,service.execute(new ProcessPaymentCommand(order,BigDecimal.TEN)));
        verify(gateway).charge(pending.getId(),order,new BigDecimal("10.00"));
    }
    @Test void shouldPropagateTechnicalFailureWithoutDeclining() {
        when(gateway.charge(any(),any(),any())).thenThrow(new IllegalStateException("Unavailable"));
        assertThrows(IllegalStateException.class,() -> service.execute(new ProcessPaymentCommand(UUID.randomUUID(),BigDecimal.TEN)));
        verify(repository,never()).save(any());verifyNoInteractions(events);
    }
    @Test void shouldRejectNullGatewayDecision() {
        assertThrows(NullPointerException.class,() -> service.execute(new ProcessPaymentCommand(UUID.randomUUID(),BigDecimal.TEN)));
        verify(repository,never()).save(any());verifyNoInteractions(events);
    }
}
