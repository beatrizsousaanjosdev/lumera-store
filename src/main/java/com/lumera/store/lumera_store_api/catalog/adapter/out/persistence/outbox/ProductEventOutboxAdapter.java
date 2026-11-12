package com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox;

import com.lumera.store.lumera_store_api.catalog.application.event.ProductCreatedEvent;
import com.lumera.store.lumera_store_api.catalog.application.port.out.ProductEventOutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class ProductEventOutboxAdapter implements ProductEventOutboxPort {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ProductCreatedEvent event) {
        String payload = jsonMapper.writeValueAsString(event);

        OutboxEventEntity entity = new OutboxEventEntity(
                event.eventId(),
                event.productId(),
                "ProductCreated",
                payload,
                event.occurredAt()
        );

        repository.save(entity);
    }
}