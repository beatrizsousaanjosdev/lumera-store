package com.lumera.store.lumera_store_api.order.application.exception;

import java.util.UUID;

public class ConflictingPaymentResultException extends RuntimeException {

    public ConflictingPaymentResultException(UUID orderId) {
        super("Payment result conflicts with order: " + orderId);
    }
}
