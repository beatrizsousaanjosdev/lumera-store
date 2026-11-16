package com.lumera.store.lumera_store_api.shared.infrastructure.outbox.messaging;

import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventEntity;
import com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.EventRoutes;
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
            routes(), 10, Duration.ofSeconds(15));

    private EventRoutes routes() {
        return new EventRoutes("products", "products.DLT", "orders", "orders.DLT", "results", "results.DLT", "payments", "payments.DLT", "paymentRequests", "paymentRequests.DLT", "finalizationRequests", "finalizationRequests.DLT", "finalized", "finalized.DLT", "completed", "completed.DLT");
    }

    private OutboxEventEntity event() {
        return new OutboxEventEntity(UUID.randomUUID(), UUID.randomUUID(), "Product", "ProductCreated", "{}", Instant.now());
    }

    @Test
    void shouldSendStoredPayloadWithProductKeyAndMarkAcknowledgedEvent() {
        var event = event();
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send("products", event.getAggregateId().toString(), "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.publishPending();
        assertNotNull(event.getPublishedAt());
        verify(kafka).send("products", event.getAggregateId().toString(), "{}");
        verifyNoMoreInteractions(kafka);
    }

    @Test
    void shouldNotContactKafkaWhenOutboxIsEmpty() {
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of());
        publisher.publishPending();
        verifyNoInteractions(kafka);
    }

    @Test
    void shouldLeaveEventPendingOnFailedAcknowledgement() {
        var event = event();
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        assertNull(event.getPublishedAt());
    }

    @Test
    void shouldLeaveEventPendingOnSynchronousSendFailure() {
        var event = event();
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("metadata unavailable"));
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        assertNull(event.getPublishedAt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLeaveEventPendingOnTimeout() throws Exception {
        var event = event();
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of(event));
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
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of(event));
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
        when(repository.findPendingForUpdate("Product", "ProductCreated", 10)).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("send failed")));
        assertThrows(OutboxPublicationException.class, publisher::publishPending);
        verify(kafka, times(1)).send(anyString(), anyString(), anyString());
        assertNull(second.getPublishedAt());
    }

    @Test
    void shouldRouteEverySupportedAggregateAndEventPair() {
        for (var route : routes().all()) {
            var event = new OutboxEventEntity(UUID.randomUUID(), UUID.randomUUID(), route.aggregateType(), route.eventType(), "{}", Instant.now());
            when(repository.findPendingForUpdate(route.aggregateType(), route.eventType(), 10)).thenReturn(List.of(event));
            when(kafka.send(route.topic(), event.getAggregateId().toString(), "{}")).thenReturn(CompletableFuture.completedFuture(null));
        }
        publisher.publishPending();
        for (var route : routes().all()) verify(kafka).send(eq(route.topic()), anyString(), eq("{}"));
        verifyNoMoreInteractions(kafka);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, null, 10, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, routes(), 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, routes(), 101, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxEventPublisher(repository, kafka, routes(), 10, Duration.ZERO));
    }
}
