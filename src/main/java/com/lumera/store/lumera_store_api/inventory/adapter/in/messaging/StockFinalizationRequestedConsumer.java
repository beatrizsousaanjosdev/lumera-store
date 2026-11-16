package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.port.in.ConfirmOrderStockUsecase;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReleaseOrderStockUsecase;
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
@ConditionalOnProperty(
        prefix = "app.inventory.order-kafka",
        name = "enabled",
        havingValue = "true"
)
public class StockFinalizationRequestedConsumer {

    private final IntegrationMessageDecoder decoder;
    private final ConfirmOrderStockUsecase confirm;
    private final ReleaseOrderStockUsecase release;

    @KafkaListener(
            id = "inventory-stock-finalization",
            topics = "${app.kafka.topics.stock-finalization-requested}",
            groupId = "${app.inventory.order-kafka.group-id:lumera-inventory-orders}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(
                record.value(),
                StockFinalizationRequestedMessage.class
        );

        decoder.requireKey(record.key(), message.orderId());

        try {
            var reservation = message.confirm()
                    ? confirm.execute(message.orderId())
                    : release.execute(message.orderId());

            log.debug(
                    "Stock finalized: eventId={}, orderId={}, status={}",
                    message.eventId(),
                    reservation.getOrderId(),
                    reservation.getStatus()
            );
        } catch (InvalidStockReservationException exception) {
            throw new InvalidIntegrationMessageException(
                    "Conflicting stock finalization request",
                    exception
            );
        }
    }
}