package com.lumera.store.lumera_store_api.inventory.adapter.in.messaging;

import com.lumera.store.lumera_store_api.inventory.application.port.in.InitializeInventoryUsecase;
import org.junit.jupiter.api.Test;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.EventRoutes;
import com.lumera.store.lumera_store_api.shared.infrastructure.messaging.KafkaConsumerErrorConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryKafkaConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaConsumerErrorConfiguration.class, ProductCreatedConsumer.class)
            .withBean(InitializeInventoryUsecase.class, () -> mock(InitializeInventoryUsecase.class))
            .withBean(EventRoutes.class, () -> new EventRoutes("products", "products.DLT", "orders", "orders.DLT", "results", "results.DLT", "payments", "payments.DLT", "paymentRequests", "paymentRequests.DLT", "finalizationRequests", "finalizationRequests.DLT", "finalized", "finalized.DLT", "completed", "completed.DLT"))
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "app.notification.kafka.enabled=true", "app.payment.kafka.enabled=true", "app.order.kafka.enabled=true", "app.inventory.order-kafka.enabled=true"
    })
    void shouldEnableErrorHandlerForEachOtherConsumerFlag(String property) {
        runner.withPropertyValues(property).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(DefaultErrorHandler.class).size());
            assertTrue(context.getBeansOfType(ProductCreatedConsumer.class).isEmpty());
        });
    }

}
