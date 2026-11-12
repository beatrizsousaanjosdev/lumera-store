package com.lumera.store.lumera_store_api.catalog.adapter.out.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxSchedulingTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxSchedulingConfiguration.class)
            .withBean(OutboxEventPublisher.class, () -> mock(OutboxEventPublisher.class))
            .withPropertyValues("app.outbox.initial-delay-ms=600000");

    @Test
    void shouldDisableSchedulerByDefault() {
        runner.run(context -> assertEquals(0, context.getBeansOfType(OutboxPublisherScheduler.class).size()));
    }

    @Test
    void shouldEnableSchedulerExplicitly() {
        runner.withPropertyValues("app.outbox.enabled=true")
                .run(context -> assertEquals(1, context.getBeansOfType(OutboxPublisherScheduler.class).size()));
    }

    @Test
    void shouldAllowNextInvocationAfterFailure() {
        var publisher = mock(OutboxEventPublisher.class);
        doThrow(new IllegalStateException("temporary failure")).doNothing().when(publisher).publishPending();
        var scheduler = new OutboxPublisherScheduler(publisher);
        assertDoesNotThrow(scheduler::publish);
        assertDoesNotThrow(scheduler::publish);
        verify(publisher, times(2)).publishPending();
    }
}
