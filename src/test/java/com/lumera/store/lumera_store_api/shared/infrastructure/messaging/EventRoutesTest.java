package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventRoutesTest {
    @Test
    void shouldResolveIndependentDeadLetterTopicsAndRejectUnknownSources() {
        var routes = new EventRoutes("products", "p.dlt", "orders", "o.dlt", "results", "r.dlt", "paymentProcess","paymentProcess.DLT","paymentRequests", "paymentRequests.DLT", "finalizationRequests", "finalizationRequests.DLT", "finalized", "finalized.DLT", "completed", "completed.DLT");
        assertEquals(8, routes.all().size());
        assertEquals("o.dlt", routes.deadLetterTopicFor("orders"));
        assertEquals("r.dlt", routes.deadLetterTopicFor("results"));
        assertThrows(IllegalArgumentException.class, () -> routes.deadLetterTopicFor("unknown"));
        assertThrows(UnsupportedOperationException.class, () -> routes.all().clear());
    }

    @Test
    void shouldRejectDuplicateOrBlankTopics() {
        assertThrows(IllegalArgumentException.class, () -> new EventRoutes("same", "a", "same", "b", "results", "c", "", "", "", "", "finalizationRequests", "finalizationRequests.DLT", "finalized", "finalized.DLT", "completed", "completed.DLT"));
        assertThrows(IllegalArgumentException.class, () -> new EventRoutes("", "a", "orders", "b", "results", "c", "", "", "", "", "finalizationRequests", "finalizationRequests.DLT", "finalized", "finalized.DLT", "completed", "completed.DLT"));
    }
}
