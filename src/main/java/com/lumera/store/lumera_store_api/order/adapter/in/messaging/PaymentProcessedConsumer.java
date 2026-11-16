package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import com.lumera.store.lumera_store_api.order.application.command.ApplyPaymentResultCommand;
import com.lumera.store.lumera_store_api.order.application.exception.ConflictingPaymentResultException;
import com.lumera.store.lumera_store_api.order.application.port.in.ApplyPaymentResultUsecase;
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
        prefix = "app.order.kafka",
        name = "enabled",
        havingValue = "true"
)
public class PaymentProcessedConsumer {

    private final IntegrationMessageDecoder decoder;
    private final ApplyPaymentResultUsecase apply;

    @KafkaListener(
            id = "order-payment-processed",
            topics = "${app.kafka.topics.payment-processed}",
            groupId = "${app.order.kafka.group-id:lumera-orders}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(
                record.value(),
                PaymentProcessedMessage.class
        );

        decoder.requireKey(record.key(), message.orderId());

        try {
            var order = apply.execute(new ApplyPaymentResultCommand(
                    message.orderId(),
                    message.paymentId(),
                    message.amount(),
                    message.status() == PaymentProcessedMessage.Status.APPROVED
            ));

            log.debug(
                    "Payment result applied: eventId={}, orderId={}, status={}",
                    message.eventId(),
                    order.getId(),
                    order.getStatus()
            );
        } catch (ConflictingPaymentResultException exception) {
            throw new InvalidIntegrationMessageException(
                    "Conflicting payment result",
                    exception
            );
        }
    }
}