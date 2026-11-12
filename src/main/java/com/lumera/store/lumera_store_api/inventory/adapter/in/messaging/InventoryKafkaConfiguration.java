package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "app.inventory.kafka",
        name = "enabled",
        havingValue = "true"
)
public class InventoryKafkaConfiguration {

    @Bean
    public DefaultErrorHandler inventoryKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.product-created-dlt}") String dltTopic
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                        new TopicPartition(dltTopic, record.partition())
        );

        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(15));

        var errorHanlder = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));

        errorHanlder.addNotRetryableExceptions(
                InvalidProductCreatedMessageException.class
        );

        return errorHanlder;
    }

}
