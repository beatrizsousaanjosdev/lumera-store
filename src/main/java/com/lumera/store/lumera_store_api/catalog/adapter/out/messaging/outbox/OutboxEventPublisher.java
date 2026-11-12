package com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox;

import com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox.OutboxEventEntity;
import com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
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
    private final String productCreatedTopic;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxEventPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.product-created}") String productCreatedTopic,
            @Value("${app.outbox.batch-size:50}") int batchSize,
            @Value("${app.outbox.send-timeout:15s}") Duration sendTimeout) {
        Assert.hasText(productCreatedTopic, "Product-created topic is required");
        Assert.isTrue(batchSize >= 1 && batchSize <= 100, "Outbox batch size must be between 1 and 100");
        Assert.notNull(sendTimeout, "Outbox send timeout is required");
        Assert.isTrue(sendTimeout.toMillis() > 0, "Outbox send timeout must be positive");
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.productCreatedTopic = productCreatedTopic;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Transactional
    public void publishPending() {
        var events = repository.findPendingForUpdate("ProductCreated", batchSize);
        for (var event : events) {
            sendAndAwaitAcknowledgement(event);
            event.markPublished(Instant.now());
            log.debug("Kafka acknowledged outbox event: eventId={}, productId={}",
                    event.getEventId(), event.getProductId());
        }
        // Dirty checking persists the timestamps at commit. A failed batch remains pending.
        // Kafka acknowledgements cannot be rolled back: retries may deliver duplicates.
    }

    private void sendAndAwaitAcknowledgement(OutboxEventEntity event) {
        try {
            kafkaTemplate.send(productCreatedTopic, event.getProductId().toString(), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublicationException(event.getEventId(), exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw new OutboxPublicationException(event.getEventId(), exception);
        }
    }
}
