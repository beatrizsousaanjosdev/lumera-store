package com.lumera.store.lumera_store_api.notification.adapter.in.messaging;

import com.lumera.store.lumera_store_api.notification.application.command.NotifyOrderOutcomeCommand;
import com.lumera.store.lumera_store_api.notification.application.exception.ConflictingNotificationException;
import com.lumera.store.lumera_store_api.notification.application.port.in.NotifyOrderOutcomeUsecase;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.IntegrationMessageDecoder;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.InvalidIntegrationMessageException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.kafka", name = "enabled", havingValue = "true")
public class OrderCompletedConsumer {
    private final IntegrationMessageDecoder decoder;
    private final NotifyOrderOutcomeUsecase notify;

    @KafkaListener(id = "notification-order-completed", topics = "${app.kafka.topics.order-completed}",
            groupId = "${app.notification.kafka.group-id:lumera-notifications}")
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(record.value(), OrderCompletedMessage.class);
        decoder.requireKey(record.key(), message.orderId());
        try {
            notify.execute(new NotifyOrderOutcomeCommand(message.eventId(), message.orderId(), message.status()));
        } catch (ConflictingNotificationException exception) {
            throw new InvalidIntegrationMessageException("Conflicting order notification", exception);
        }
    }
}
