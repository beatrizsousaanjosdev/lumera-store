package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import com.lumera.store.lumera_store_api.order.application.command.StockReservationResultCommand;
import com.lumera.store.lumera_store_api.order.application.exception.ConflictingStockReservationResultException;
import com.lumera.store.lumera_store_api.order.application.port.in.ApplyStockReservationResultUsecase;
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
@ConditionalOnProperty(prefix = "app.order.kafka", name = "enabled", havingValue = "true")
public class StockReservationResultConsumer {
    private final IntegrationMessageDecoder decoder;
    private final ApplyStockReservationResultUsecase apply;

    @KafkaListener(id = "order-stock-reservation-result", topics = "${app.kafka.topics.stock-reservation-result}",
            groupId = "${app.order.kafka.group-id:lumera-orders}")
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(record.value(), StockReservationResultMessage.class);
        decoder.requireKey(record.key(), message.orderId());
        try {
            var order = apply.execute(new StockReservationResultCommand(message.orderId(), message.reserved(), message.rejectionReason()));
            log.debug("Order stock result applied: eventId={}, orderId={}, status={}",
                    message.eventId(), message.orderId(), order.getStatus());
        } catch (ConflictingStockReservationResultException exception) {
            throw new InvalidIntegrationMessageException("Conflicting stock reservation result", exception);
        }
    }
}
