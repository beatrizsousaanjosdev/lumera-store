package com.lumera.store.lumera_store_api.payment.application.command;

import com.lumera.store.lumera_store_api.payment.domain.exception.InvalidPaymentException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ProcessPaymentCommand(
        UUID orderId,
        BigDecimal amount
) {
    public ProcessPaymentCommand{
        if (orderId == null) {
            throw new InvalidPaymentException("Order ID is required");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidPaymentException(
                    "Payment amount must be greater than zero"
            );
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidPaymentException(
                    "Payment amount must have at most two decimal places"
            );
        }
    }
}
