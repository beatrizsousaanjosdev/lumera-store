package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import com.lumera.store.lumera_store_api.order.application.command.CompleteOrderStockCommand;
import com.lumera.store.lumera_store_api.order.application.exception.ConflictingStockFinalizationException;
import com.lumera.store.lumera_store_api.order.application.port.in.CompleteOrderStockUsecase;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.IntegrationMessageDecoder;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.InvalidIntegrationMessageException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.order.kafka",
        name = "enabled",
        havingValue = "true"
)
public class StockFinalizedConsumer {

    private final IntegrationMessageDecoder decoder;
    private final CompleteOrderStockUsecase complete;

    @KafkaListener(
            id = "order-stock-finalized",
            topics = "${app.kafka.topics.stock-finalized}",
            groupId = "${app.order.kafka.group-id:lumera-orders}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(
                record.value(),
                StockFinalizedMessage.class
        );

        decoder.requireKey(record.key(), message.orderId());

        try {
            complete.execute(new CompleteOrderStockCommand(
                    message.orderId(),
                    message.confirmed()
            ));
        } catch (ConflictingStockFinalizationException exception) {
            throw new InvalidIntegrationMessageException(
                    "Conflicting stock finalization result",
                    exception
            );
        }
    }
}