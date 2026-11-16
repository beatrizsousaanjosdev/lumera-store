package com.lumera.store.lumera_store_api.payment.application.event;

import com.lumera.store.lumera_store_api.payment.domain.Payment;
import com.lumera.store.lumera_store_api.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentProcessedEvent(
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        Instant occurredAt,
        PaymentStatus status
) {

    public PaymentProcessedEvent {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(paymentId, "Payment ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");
        Objects.requireNonNull(status, "Payment status is required");

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (status == PaymentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Pending payments cannot produce a result event"
            );
        }
    }

    public static PaymentProcessedEvent create(Payment payment) {
        return new PaymentProcessedEvent(
                UUID.randomUUID(),
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                Instant.now(),
                payment.getStatus()
        );
    }
}
