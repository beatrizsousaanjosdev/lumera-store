package com.lumera.store.lumera_store_api.payment.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGatewayPort {

    //payment simulator

    PaymentDecision charge(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount
    );
}
