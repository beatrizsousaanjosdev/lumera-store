package com.lumera.store.lumera_store_api.order.application.port.out;

import com.lumera.store.lumera_store_api.order.application.command.ApplyPaymentResultCommand;
import java.util.Optional;
import java.util.UUID;

public interface OrderPaymentResultPort {
    Optional<ApplyPaymentResultCommand> findByOrderId(UUID orderId);
    void save(ApplyPaymentResultCommand result);
}
