package com.lumera.store.lumera_store_api.order.application.port.out;

import com.lumera.store.lumera_store_api.order.application.event.OrderCreatedEvent;
import com.lumera.store.lumera_store_api.order.application.event.OrderCompletedEvent;
import com.lumera.store.lumera_store_api.order.application.event.PaymentRequestedEvent;
import com.lumera.store.lumera_store_api.order.application.event.StockFinalizationRequestedEvent;

public interface OrderEventOutboxPort {

    void appendCompleted(OrderCompletedEvent event);

    void append(OrderCreatedEvent event);

    void appendPaymentRequested(PaymentRequestedEvent event);

    void appendStockFinalizationRequested(
            StockFinalizationRequestedEvent event
    );
}
