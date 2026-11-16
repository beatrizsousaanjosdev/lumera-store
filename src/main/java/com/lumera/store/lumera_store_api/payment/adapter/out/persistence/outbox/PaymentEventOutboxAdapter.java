package com.lumera.store.lumera_store_api.payment.adapter.out.persistence.outbox;

import com.lumera.store.lumera_store_api.payment.application.event.PaymentProcessedEvent;
import com.lumera.store.lumera_store_api.payment.application.port.out.PaymentEventOutboxPort;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventEntity;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class PaymentEventOutboxAdapter  implements PaymentEventOutboxPort {

    private final OutboxEventRepository repository;
    private final JsonMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(PaymentProcessedEvent event) {
        var eventEntity = new OutboxEventEntity(
                event.eventId(),
                event.orderId(),
                "Payment",
                "PaymentProcessed",
                mapper.writeValueAsString(event),
                event.occurredAt()
        );

        repository.save(eventEntity);

    }
}
