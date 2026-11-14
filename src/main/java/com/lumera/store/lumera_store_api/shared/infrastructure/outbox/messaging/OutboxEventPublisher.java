package com.lumera.store.lumera_store_api.shared.infrastructure.outbox.messaging;

import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventEntity;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.EventRoutes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventRoutes routes;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxEventPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            EventRoutes routes,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.send-timeout:15s}") Duration sendTimeout) {
        Assert.notNull(routes, "Event routes are required");
        Assert.isTrue(batchSize >= 1 && batchSize <= 100, "Outbox batch size must be between 1 and 100");
        Assert.notNull(sendTimeout, "Outbox send timeout is required");
        Assert.isTrue(sendTimeout.toMillis() > 0, "Outbox send timeout must be positive");
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.routes = routes;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Transactional
    public void publishPending() {
        for (var route : routes.all()) {
            var events = repository.findPendingForUpdate(route.aggregateType(), route.eventType(), batchSize);
            for (var event : events) {
                sendAndAwaitAcknowledgement(event, route.topic());
                event.markPublished(Instant.now());
                log.debug("Kafka acknowledged outbox event: eventId={}, aggregateId={}, eventType={}",
                        event.getEventId(), event.getAggregateId(), event.getEventType());
            }
        }
        // Dirty checking persists the timestamps at commit. A failed batch remains pending.
        // Kafka acknowledgements cannot be rolled back: retries may deliver duplicates.
    }

    private void sendAndAwaitAcknowledgement(OutboxEventEntity event, String topic) {
        try {
            kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublicationException(event.getEventId(), exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw new OutboxPublicationException(event.getEventId(), exception);
        }
    }
}
