package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentProcessedMessage(
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        Instant occurredAt,
        Status status
) {

    public enum Status {
        APPROVED,
        DECLINED
    }

    public PaymentProcessedMessage {
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

        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
