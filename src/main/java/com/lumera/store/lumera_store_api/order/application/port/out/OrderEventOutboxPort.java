package com.lumera.store.lumera_store_api.order.application.port.out;

import com.lumera.store.lumera_store_api.order.application.event.OrderCreatedEvent;

public interface OrderEventOutboxPort {

    void append(OrderCreatedEvent event);
}
