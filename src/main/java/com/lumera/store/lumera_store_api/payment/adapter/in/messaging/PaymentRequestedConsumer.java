package com.lumera.store.lumera_store_api.payment.adapter.in.messaging;

import com.lumera.store.lumera_store_api.payment.application.command.ProcessPaymentCommand;
import com.lumera.store.lumera_store_api.payment.application.exception.ConflictingPaymentException;
import com.lumera.store.lumera_store_api.payment.application.port.in.ProcessPaymentUsecase;
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
        prefix = "app.payment.kafka",
        name = "enabled",
        havingValue = "true"
)
public class PaymentRequestedConsumer {

    private final IntegrationMessageDecoder decoder;
    private final ProcessPaymentUsecase processPayment;

    @KafkaListener(
            id = "payment-requested",
            topics = "${app.kafka.topics.payment-requested}",
            groupId = "${app.payment.kafka.group-id:lumera-payments}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        var message = decoder.decode(
                record.value(),
                PaymentRequestedMessage.class
        );

        decoder.requireKey(record.key(), message.orderId());

        try {
            var payment = processPayment.execute(
                    new ProcessPaymentCommand(message.orderId(), message.amount())
            );

            log.debug("Payment processed: eventId={}, orderId={}, status={}",
                    message.eventId(),
                    payment.getOrderId(),
                    payment.getStatus()
                    );
        } catch (ConflictingPaymentException exception) {
            throw new InvalidIntegrationMessageException("Conflicting payment request", exception);
        }

    }
}
