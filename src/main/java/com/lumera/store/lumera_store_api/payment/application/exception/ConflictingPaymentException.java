package com.lumera.store.lumera_store_api.payment.application.exception;

import java.util.UUID;

public class ConflictingPaymentException extends RuntimeException {

    public ConflictingPaymentException(UUID orderId) {
        super("Order already has a payment with a different amount: " + orderId);
    }
}
