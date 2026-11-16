package com.lumera.store.lumera_store_api.order.application.command;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public record ApplyPaymentResultCommand(
        UUID orderId,
        UUID paymentId,
        BigDecimal amount,
        boolean approved
) {
    public ApplyPaymentResultCommand {
        Objects.requireNonNull(orderId, "Order ID is required");
        Objects.requireNonNull(paymentId, "Payment ID is required");

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}