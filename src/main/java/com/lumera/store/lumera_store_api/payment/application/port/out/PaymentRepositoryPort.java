package com.lumera.store.lumera_store_api.payment.application.port.out;

import com.lumera.store.lumera_store_api.payment.domain.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {

    void lockByOrderId(UUID orderId);

    Optional<Payment> findByOrderId(UUID orderId);

    Payment save(Payment payment);
}
