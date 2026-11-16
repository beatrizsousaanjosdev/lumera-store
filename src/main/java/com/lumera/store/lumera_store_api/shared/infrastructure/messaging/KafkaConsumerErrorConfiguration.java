package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "${app.inventory.kafka.enabled:false} || "
                + "${app.inventory.order-kafka.enabled:false} || "
                + "${app.order.kafka.enabled:false} || "
                + "${app.payment.kafka.enabled:false} || "
                + "${app.notification.kafka.enabled:false}"
)
public class KafkaConsumerErrorConfiguration {
    @Bean
    public DefaultErrorHandler integrationKafkaErrorHandler(KafkaTemplate<String, String> template, EventRoutes routes) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(routes.deadLetterTopicFor(record.topic()), record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(15));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(InvalidIntegrationMessageException.class);
        return handler;
    }
}
