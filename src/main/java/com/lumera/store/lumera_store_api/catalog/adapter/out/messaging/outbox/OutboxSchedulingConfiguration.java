package com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "app.outbox",
        name = "enabled",
        havingValue = "true"
)
public class OutboxSchedulingConfiguration {

    @Bean
    public OutboxPublisherScheduler outboxPublisherScheduler(
            OutboxEventPublisher publisher) {

        return  new OutboxPublisherScheduler(publisher);
    }
}
