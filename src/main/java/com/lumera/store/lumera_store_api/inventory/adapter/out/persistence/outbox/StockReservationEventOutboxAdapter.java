package com.lumera.store.lumera_store_api.inventory.adapter.out.persistence.outbox;

import com.lumera.store.lumera_store_api.inventory.application.event.StockFinalizedEvent;
import com.lumera.store.lumera_store_api.inventory.application.event.StockReservationResultEvent;
import com.lumera.store.lumera_store_api.inventory.application.port.out.StockReservationEventOutboxPort;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventEntity;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class StockReservationEventOutboxAdapter implements StockReservationEventOutboxPort {
    private final OutboxEventRepository repository;
    private final JsonMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(StockReservationResultEvent event) {
        repository.save(new OutboxEventEntity(event.eventId(), event.orderId(), "StockReservation",
                "StockReservationResult", mapper.writeValueAsString(event), event.occurredAt()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendFinalized(StockFinalizedEvent event) {
        repository.save(new OutboxEventEntity(
                event.eventId(),
                event.orderId(),
                "StockReservation",
                "StockFinalized",
                mapper.writeValueAsString(event),
                event.occurredAt()
        ));
    }
}
