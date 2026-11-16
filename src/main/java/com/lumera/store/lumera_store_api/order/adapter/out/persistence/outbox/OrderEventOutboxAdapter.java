package com.lumera.store.lumera_store_api.order.adapter.out.persistence.outbox;

import com.lumera.store.lumera_store_api.order.application.event.OrderCreatedEvent;
import com.lumera.store.lumera_store_api.order.application.event.OrderCompletedEvent;
import com.lumera.store.lumera_store_api.order.application.event.PaymentRequestedEvent;
import com.lumera.store.lumera_store_api.order.application.event.StockFinalizationRequestedEvent;
import com.lumera.store.lumera_store_api.order.application.port.out.OrderEventOutboxPort;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventEntity;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class OrderEventOutboxAdapter implements OrderEventOutboxPort {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OrderCreatedEvent event) {
        String payload = jsonMapper.writeValueAsString(event);

        var entity = new OutboxEventEntity(
                event.eventId(),
                event.orderId(),
                "Order",
                "OrderCreated",
                payload,
                event.occurredAt()
        );

        repository.save(entity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendPaymentRequested(PaymentRequestedEvent event) {
        var payload = jsonMapper.writeValueAsString(event);
        var eventEntity = new OutboxEventEntity(
                event.eventId(),
                event.orderId(),
                "Order",
                "PaymentRequested",
                payload,
                event.occurredAt()
        );
        repository.save(eventEntity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendStockFinalizationRequested(StockFinalizationRequestedEvent event) {
        var payload = jsonMapper.writeValueAsString(event);
        var eventEntity = new OutboxEventEntity(
                event.eventId(),
                event.orderId(),
                "Order",
                "StockFinalizationRequested",
                payload,
                event.occurredAt()
        );
        repository.save(eventEntity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendCompleted(OrderCompletedEvent event) {
        repository.save(new OutboxEventEntity(event.eventId(), event.orderId(), "Order",
                "OrderCompleted", jsonMapper.writeValueAsString(event), event.occurredAt()));
    }
}