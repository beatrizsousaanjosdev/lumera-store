package com.lumera.store.lumera_store_api.catalog.adapter.out.persistence.outbox;

import com.lumera.store.lumera_store_api.catalog.application.event.ProductCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductEventOutboxAdapterTest {
    @Test
    void shouldSerializeEventAndPreserveItsIdentifiers() {
        var repository = mock(OutboxEventRepository.class);
        var mapper = JsonMapper.builder().build();
        var event = ProductCreatedEvent.create(UUID.randomUUID());
        new ProductEventOutboxAdapter(repository, mapper).append(event);
        var captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertEquals(event.eventId(), saved.getEventId());
        assertEquals(event.productId(), saved.getProductId());
        assertEquals(event.occurredAt(), saved.getOccurredAt());
        assertEquals("ProductCreated", saved.getEventType());
        assertNull(saved.getPublishedAt());
        assertEquals(event, mapper.readValue(saved.getPayload(), ProductCreatedEvent.class));
    }

    @Test
    void shouldPropagateSerializationFailureWithoutSaving() {
        var repository = mock(OutboxEventRepository.class);
        var mapper = mock(JsonMapper.class);
        var event = ProductCreatedEvent.create(UUID.randomUUID());
        when(mapper.writeValueAsString(event)).thenThrow(new IllegalStateException("serialization failed"));
        assertThrows(IllegalStateException.class,
                () -> new ProductEventOutboxAdapter(repository, mapper).append(event));
        verifyNoInteractions(repository);
    }
}
