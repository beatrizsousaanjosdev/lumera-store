package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IntegrationMessageDecoder {
    private final JsonMapper mapper;

    public <T> T decode(String payload, Class<T> type) {
        try {
            T message = mapper.readValue(payload, type);
            if (message == null) throw new InvalidIntegrationMessageException("Event payload is required");
            return message;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new InvalidIntegrationMessageException("Invalid " + type.getSimpleName() + " payload", exception);
        }
    }

    public void requireKey(String key, UUID aggregateId) {
        if (aggregateId == null || !aggregateId.toString().equals(key)) {
            throw new InvalidIntegrationMessageException("Message key must match aggregate ID");
        }
    }
}
