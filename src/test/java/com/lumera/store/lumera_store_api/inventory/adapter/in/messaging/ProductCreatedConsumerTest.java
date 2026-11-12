package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.port.in.InitializeInventoryUsecase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCreatedConsumerTest {
    private final InitializeInventoryUsecase initialize = mock(InitializeInventoryUsecase.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProductCreatedConsumer consumer = new ProductCreatedConsumer(initialize, mapper);

    @Test
    void shouldInitializeProductFromMessage() {
        UUID id = UUID.randomUUID();
        consumer.consume(record(id.toString(), payload(id)));
        verify(initialize).execute(id);
        verifyNoMoreInteractions(initialize);
    }

    @Test
    void shouldDelegateRedeliveriesToIdempotentUsecase() {
        UUID id = UUID.randomUUID();
        var record = record(id.toString(), payload(id));
        consumer.consume(record);
        consumer.consume(record);
        verify(initialize, times(2)).execute(id);
    }

    @Test
    void shouldPropagateProcessingFailureForRetry() {
        UUID id = UUID.randomUUID();
        var failure = new DataAccessResourceFailureException("temporary failure");
        when(initialize.execute(id)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> consumer.consume(record(id.toString(), payload(id)))));
    }

    static Stream<String> invalidPayloads() {
        String id = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        return Stream.of(null, "null", "{", "[]", "{}",
                "{\"productId\":\"" + id + "\",\"occurredAt\":\"" + timestamp + "\"}",
                "{\"eventId\":\"" + id + "\",\"occurredAt\":\"" + timestamp + "\"}",
                "{\"eventId\":\"" + id + "\",\"productId\":\"" + id + "\"}",
                "{\"eventId\":\"abc\",\"productId\":\"" + id + "\",\"occurredAt\":\"" + timestamp + "\"}",
                "{\"eventId\":\"" + id + "\",\"productId\":\"" + id + "\",\"occurredAt\":\"invalid\"}");
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void shouldRejectInvalidPayloadBeforeInitializing(String payload) {
        assertThrows(InvalidProductCreatedMessageException.class,
                () -> consumer.consume(record(UUID.randomUUID().toString(), payload)));
        verifyNoInteractions(initialize);
    }

    @Test
    void shouldRejectMissingOrMismatchedKey() {
        UUID id = UUID.randomUUID();
        String payload = payload(id);
        assertThrows(InvalidProductCreatedMessageException.class,
                () -> consumer.consume(record(null, payload)));
        assertThrows(InvalidProductCreatedMessageException.class,
                () -> consumer.consume(record(UUID.randomUUID().toString(), payload)));
        verifyNoInteractions(initialize);
    }

    private String payload(UUID id) {
        return mapper.writeValueAsString(new ProductCreatedMessage(UUID.randomUUID(), id, Instant.now()));
    }

    private ConsumerRecord<String, String> record(String key, String payload) {
        return new ConsumerRecord<>("products", 0, 1L, key, payload);
    }
}
