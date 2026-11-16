package com.lumera.store.lumera_store_api.order.application.event;

import com.lumera.store.lumera_store_api.order.domain.Order;
import com.lumera.store.lumera_store_api.order.domain.OrderStatus;
import com.lumera.store.lumera_store_api.order.domain.exception.InvalidOrderException;
import com.lumera.store.lumera_store_api.payment.domain.exception.InvalidPaymentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRequestedEvent(
       UUID eventId,
       UUID orderId,
       BigDecimal amount,
       Instant occurredAt
) {

    public PaymentRequestedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");

        if (amount == null || amount.signum() <= 0) throw new InvalidPaymentException("Payment amount must be greater than zero");

    }

    public static PaymentRequestedEvent create(Order order) {
        Objects.requireNonNull(order, "Order is required");
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) throw new InvalidOrderException("Only orders awaiting payment can request payment.");

        return new PaymentRequestedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getTotal(),
                Instant.now()
        );
    }
}
