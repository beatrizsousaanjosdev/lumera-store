package com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox;

import com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxEventPublisherTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxEventPublisher publisher = new OutboxEventPublisher(repository, kafka,
            "products", 10, Duration.ofSeconds(15));

    private OutboxEventEntity event() {
        return new OutboxEventEntity(UUID.randomUUID(), UUID.randomUUID(), "ProductCreated", "{}", Instant.now());
    }

    @Test
    void shouldSendStoredPayloadWithProductKeyAndMarkAcknowledgedEvent() {
        var event = event();
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send("products", event.getProductId().toString(), "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertNotNull(event.getPublishedAt());
        verify(kafka).send("products", event.getProductId().toString(), "{}");
        verifyNoMoreInteractions(kafka);
    }

    @Test
    void shouldNotContactKafkaWhenOutboxIsEmpty() {
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of());
        publisher.publishPending();
        verifyNoInteractions(kafka);
    }

    @Test
    void shouldLeaveEventPendingOnFailedAcknowledgement() {
        var event = event();
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        assertNull(event.getPublishedAt());
    }

    @Test
    void shouldLeaveEventPendingOnSynchronousSendFailure() {
        var event = event();
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("metadata unavailable"));
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        assertNull(event.getPublishedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLeaveEventPendingOnTimeout() throws Exception {
        var event = event();
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.get(15000, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException());
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        assertNull(event.getPublishedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRestoreInterruptFlag() throws Exception {
        var event = event();
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(future.get(15000, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());
        try {
            assertThrows(OutboxPublicationException.class, publisher::publishPending);
            assertTrue(Thread.currentThread().isInterrupted());
            assertNull(event.getPublishedAt());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldStopBatchAfterFailure() {
        var first = event();
        var second = event();
        when(repository.findPendingForUpdate("ProductCreated", 10)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("send failed")));
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        assertNull(second.getPublishedAt());
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, "", 10, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, "products", 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, "products", 101, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, "products", 10, Duration.ZERO));
    }
}
