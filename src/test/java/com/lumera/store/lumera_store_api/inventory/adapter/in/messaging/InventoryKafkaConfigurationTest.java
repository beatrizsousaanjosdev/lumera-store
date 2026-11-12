package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.port.in.InitializeInventoryUsecase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryKafkaConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(InventoryKafkaConfiguration.class, ProductCreatedConsumer.class)
            .withBean(InitializeInventoryUsecase.class, () -> mock(InitializeInventoryUsecase.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
            .withPropertyValues("app.kafka.topics.product-created-dlt=products.DLT");

    @Test
    void shouldDisableConsumerAndErrorHandlerByDefault() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(ProductCreatedConsumer.class).isEmpty());
            assertTrue(context.getBeansOfType(DefaultErrorHandler.class).isEmpty());
        });
    }

    @Test
    void shouldEnableConsumerAndErrorHandlerTogether() {
        runner.withPropertyValues("app.inventory.kafka.enabled=true").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(ProductCreatedConsumer.class).size());
            assertEquals(1, context.getBeansOfType(DefaultErrorHandler.class).size());
        });
    }
}
