package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.command.ReserveOrderStockCommand;
import com.lumera.store.lumera_store_api.inventory.application.port.in.ReserveOrderStockUsecase;
import com.lumera.store.lumera_store_api.inventory.domain.*;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCreatedConsumerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReserveOrderStockUsecase reserve = mock(ReserveOrderStockUsecase.class);
    private final OrderCreatedConsumer consumer = new OrderCreatedConsumer(new IntegrationMessageDecoder(mapper), reserve);

    private OrderCreatedMessage message(UUID id, UUID product) {
        return new OrderCreatedMessage(UUID.randomUUID(), id, Instant.now(), List.of(new OrderCreatedMessage.Item(product, 2)));
    }

    @Test
    void shouldDelegateValidMessageAndDuplicateToIdempotentUsecase() {
        UUID id = UUID.randomUUID(), product = UUID.randomUUID();
        var command = new ReserveOrderStockCommand(id, List.of(new StockReservationItem(product, 2)));
        when(reserve.execute(command)).thenReturn(StockReservation.reserved(id, command.items()));
        var record = new ConsumerRecord<>("orders", 0, 0L, id.toString(), mapper.writeValueAsString(message(id, product)));
        consumer.consume(record); consumer.consume(record);
        verify(reserve, times(2)).execute(command);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{", "null", "{}", "{\"eventId\":\"abc\"}"})
    void shouldRejectInvalidPayload(String body) {
        assertThrows(InvalidIntegrationMessageException.class,
                () -> consumer.consume(new ConsumerRecord<>("orders", 0, 0L, UUID.randomUUID().toString(), body)));
        verifyNoInteractions(reserve);
    }

    @Test
    void shouldRejectMismatchedKeyAndDuplicateProducts() {
        UUID id = UUID.randomUUID(), product = UUID.randomUUID();
        var message = message(id, product);
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(new ConsumerRecord<>(
                "orders", 0, 0L, UUID.randomUUID().toString(), mapper.writeValueAsString(message))));
        var duplicates = new OrderCreatedMessage(message.eventId(), id, message.occurredAt(),
                List.of(new OrderCreatedMessage.Item(product, 1), new OrderCreatedMessage.Item(product, 2)));
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(new ConsumerRecord<>(
                "orders", 0, 0L, id.toString(), mapper.writeValueAsString(duplicates))));
        verifyNoInteractions(reserve);
    }

    @Test
    void shouldPropagateTransientFailures() {
        UUID id = UUID.randomUUID(), product = UUID.randomUUID();
        when(reserve.execute(any())).thenThrow(new DataAccessResourceFailureException("temporary failure"));
        assertThrows(DataAccessResourceFailureException.class, () -> consumer.consume(new ConsumerRecord<>(
                "orders", 0, 0L, id.toString(), mapper.writeValueAsString(message(id, product)))));
    }
}
