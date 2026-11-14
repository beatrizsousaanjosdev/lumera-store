package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.command.ReserveOrderStockCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReserveOrderStockUsecase;
import com.lumera.store.lumera_store_api.inventory.domain.StockReservationItem;
import com.lumera.store.lumera_store_api.inventory.domain.exception.InvalidStockReservationException;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.IntegrationMessageDecoder;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.InvalidIntegrationMessageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.inventory.order-kafka", name = "enabled", havingValue = "true")
public class OrderCreatedConsumer {
    private final IntegrationMessageDecoder decoder;
    private final ReserveOrderStockUsecase reserve;

    @KafkaListener(id = "inventory-order-created", topics = "${app.kafka.topics.order-created}",
            groupId = "${app.inventory.order-kafka.group-id:lumera-inventory-orders}")
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(record.value(), OrderCreatedMessage.class);
        decoder.requireKey(record.key(), message.orderId());
        try {
            var result = reserve.execute(new ReserveOrderStockCommand(message.orderId(),
                    message.items().stream().map(item -> new StockReservationItem(item.productId(), item.quantity())).toList()));
            log.debug("Order stock decision: eventId={}, orderId={}, status={}",
                    message.eventId(), message.orderId(), result.getStatus());
        } catch (InvalidStockReservationException exception) {
            throw new InvalidIntegrationMessageException("Invalid or conflicting order reservation request", exception);
        }
    }
}
