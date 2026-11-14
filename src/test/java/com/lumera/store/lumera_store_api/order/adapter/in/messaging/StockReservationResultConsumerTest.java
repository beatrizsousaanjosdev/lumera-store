package com.lumera.store.lumera_store_api.order.adapter.in.messaging;

import com.lumera.store.lumera_store_api.order.application.command.StockReservationResultCommand;
import com.lumera.store.lumera_store_api.order.application.exception.*;
import com.lumera.store.lumera_store_api.order.application.port.in.ApplyStockReservationResultUsecase;
import com.lumera.store.lumera_store_api.order.domain.*;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockReservationResultConsumerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ApplyStockReservationResultUsecase apply = mock(ApplyStockReservationResultUsecase.class);
    private final StockReservationResultConsumer consumer = new StockReservationResultConsumer(new IntegrationMessageDecoder(mapper), apply);

    private ConsumerRecord<String, String> record(UUID id, boolean reserved) {
        var message = new StockReservationResultMessage(UUID.randomUUID(), id, Instant.now(), reserved, reserved ? null : "No stock");
        return new ConsumerRecord<>("results", 0, 0L, id.toString(), mapper.writeValueAsString(message));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldDelegateResult(boolean reserved) {
        UUID id = UUID.randomUUID();
        var order = new Order(List.of(new OrderItem(UUID.randomUUID(), "Mouse", 1, BigDecimal.TEN)));
        when(apply.execute(any())).thenReturn(order);
        consumer.consume(record(id, reserved));
        verify(apply).execute(new StockReservationResultCommand(id, reserved, reserved ? null : "No stock"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}"})
    void shouldRejectInvalidResult(String body) {
        assertThrows(InvalidIntegrationMessageException.class,
                () -> consumer.consume(new ConsumerRecord<>("results", 0, 0L, UUID.randomUUID().toString(), body)));
        verifyNoInteractions(apply);
    }

    @Test
    void shouldRejectMissingBooleanAndInvalidReason() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new StockReservationResultMessage(id,id,Instant.now(),null,null));
        assertThrows(IllegalArgumentException.class, () -> new StockReservationResultMessage(id,id,Instant.now(),false,null));
        assertThrows(IllegalArgumentException.class, () -> new StockReservationResultMessage(id,id,Instant.now(),true,"reason"));
    }

    @Test
    void shouldClassifyConflictingResultAsPermanentButMissingOrderAsRetryable() {
        UUID id = UUID.randomUUID();
        when(apply.execute(any())).thenThrow(new ConflictingStockReservationResultException(id, OrderStatus.CANCELLED, true));
        assertThrows(InvalidIntegrationMessageException.class, () -> consumer.consume(record(id, true)));
        doThrow(new OrderNotFoundException(id)).when(apply).execute(any());
        assertThrows(OrderNotFoundException.class, () -> consumer.consume(record(id, true)));
    }
}
