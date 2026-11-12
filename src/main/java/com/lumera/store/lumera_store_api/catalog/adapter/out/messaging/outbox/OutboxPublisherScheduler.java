package com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxEventPublisher publisher;

    @Scheduled(
            fixedDelayString = "${app.outbox.fixed-delay-ms:5000}",
            initialDelayString = "${app.outbox.initial-delay-ms:10000}"
    )
    public void publish() {
        try {
            publisher.publishPending();
        } catch (RuntimeException exception) {
            log.error("Outbox publication failed; pending events will be retried", exception);
        }
    }
}
