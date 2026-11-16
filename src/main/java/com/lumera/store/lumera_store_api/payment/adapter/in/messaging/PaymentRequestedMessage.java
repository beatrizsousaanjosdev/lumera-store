package com.lumera.store.lumera_store_api.payment.adapter.in.messaging;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRequestedMessage(
        UUID eventId,
        UUID orderId,
        BigDecimal amount,
        Instant occurredAt
) {

    public PaymentRequestedMessage {
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(occurredAt, "Timestamp is required");

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
