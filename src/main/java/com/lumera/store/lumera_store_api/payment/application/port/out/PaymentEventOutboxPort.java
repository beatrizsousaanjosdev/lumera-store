package com.lumera.store.lumera_store_api.payment.application.port.out;

import com.lumera.store.lumera_store_api.payment.application.event.PaymentProcessedEvent;

public interface PaymentEventOutboxPort {

    void append(PaymentProcessedEvent event);
}
