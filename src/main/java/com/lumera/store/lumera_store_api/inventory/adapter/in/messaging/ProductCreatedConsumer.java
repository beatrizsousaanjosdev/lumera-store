package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.port.in.InitializeInventoryUsecase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.inventory.kafka",
        name = "enabled",
        havingValue = "true"
)
public class ProductCreatedConsumer {

    private final InitializeInventoryUsecase initializeInventoryUsecase;
    private final JsonMapper jsonMapper;

    @KafkaListener(
            id = "inventory-product-created",
            topics = "${app.kafka.topics.product-created}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        ProductCreatedMessage message = deserialize(record.value());

        if (!message.productId().toString().equals(record.key())) {
            throw new InvalidProductCreatedMessageException(
                    "Message key must match product ID"
            );
        }

        initializeInventoryUsecase.execute(message.productId());

        log.debug(
                "Inventory initialization completed: eventId={}, productId={}",
                message.eventId(),
                message.productId()
        );
    }

    private ProductCreatedMessage deserialize(String payload) {
        ProductCreatedMessage message;

        try {
            message = jsonMapper.readValue(
                    payload,
                    ProductCreatedMessage.class
            );
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new InvalidProductCreatedMessageException(
                    "Invalid ProductCreated payload",
                    exception
            );
        }

        if (message == null
                || message.eventId() == null
                || message.productId() == null
                || message.occurredAt() == null) {
            throw new InvalidProductCreatedMessageException(
                    "Event ID, product ID and timestamp are required"
            );
        }

        return message;
    }
}