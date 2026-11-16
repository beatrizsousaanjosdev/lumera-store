package com.lumera.store.lumera_store_api.shared.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import java.util.List;

@Component
public class EventRoutes {
    public record Route(String aggregateType, String eventType, String topic, String deadLetterTopic) {
        public Route {
            Assert.hasText(aggregateType, "Aggregate type is required");
            Assert.hasText(eventType, "Event type is required");
            Assert.hasText(topic, "Event topic is required");
            Assert.hasText(deadLetterTopic, "Dead-letter topic is required");
        }
    }
    private final List<Route> routes;

    public EventRoutes(
            @Value("${app.kafka.topics.product-created:lumera.product.created.v1}")
            String products,

            @Value("${app.kafka.topics.product-created-dlt:lumera.product.created.v1.DLT}")
            String productsDlt,

            @Value("${app.kafka.topics.order-created:lumera.order.created.v1}")
            String orders,

            @Value("${app.kafka.topics.order-created-dlt:lumera.order.created.v1.DLT}")
            String ordersDlt,

            @Value("${app.kafka.topics.stock-reservation-result:lumera.stock.reservation.result.v1}")
            String results,

            @Value("${app.kafka.topics.stock-reservation-result-dlt:lumera.stock.reservation.result.v1.DLT}")
            String resultsDlt,

            @Value("${app.kafka.topics.payment-processed:lumera.payment.processed.v1}")
            String payments,

            @Value("${app.kafka.topics.payment-processed-dlt:lumera.payment.processed.v1.DLT}")
            String paymentsDlt,

            @Value("${app.kafka.topics.payment-requested:lumera.payment.requested.v1}")
            String paymentRequests,

            @Value("${app.kafka.topics.payment-requested-dlt:lumera.payment.requested.v1.DLT}")
            String paymentRequestsDlt,
            @Value("${app.kafka.topics.stock-finalization-requested:lumera.stock.finalization.requested.v1}")
            String finalizationRequests,

            @Value("${app.kafka.topics.stock-finalization-requested-dlt:lumera.stock.finalization.requested.v1.DLT}")
            String finalizationRequestsDlt,

            @Value("${app.kafka.topics.stock-finalized:lumera.stock.finalized.v1}")
            String finalized,

            @Value("${app.kafka.topics.stock-finalized-dlt:lumera.stock.finalized.v1.DLT}")
            String finalizedDlt,
            @Value("${app.kafka.topics.order-completed:lumera.order.completed.v1}") String completed,
            @Value("${app.kafka.topics.order-completed-dlt:lumera.order.completed.v1.DLT}") String completedDlt
    ) {
        routes = List.of(
                new Route(
                        "Product", "ProductCreated",
                        products, productsDlt
                ),
                new Route(
                        "Order", "OrderCreated",
                        orders, ordersDlt
                ),
                new Route(
                        "StockReservation", "StockReservationResult",
                        results, resultsDlt
                ),
                new Route(
                        "Payment", "PaymentProcessed",
                        payments, paymentsDlt
                ),
                new Route(
                        "Order", "PaymentRequested",
                        paymentRequests, paymentRequestsDlt
                ),
                new Route(
                        "Order", "StockFinalizationRequested",
                        finalizationRequests, finalizationRequestsDlt
                ),
                new Route(
                        "StockReservation", "StockFinalized",
                        finalized, finalizedDlt
                ),
                new Route("Order", "OrderCompleted", completed, completedDlt)
        );

        Assert.isTrue(
                routes.stream()
                        .map(Route::topic)
                        .distinct()
                        .count() == routes.size(),
                "Each event route must have its own topic"
        );
        Assert.isTrue(routes.stream()
                        .flatMap(route -> java.util.stream.Stream.of(route.topic(), route.deadLetterTopic()))
                        .distinct().count() == routes.size() * 2L,
                "Event topics and DLTs must all be distinct");
    }

    public List<Route> all() { return routes; }

    public String deadLetterTopicFor(String topic) {
        return routes.stream().filter(route -> route.topic().equals(topic)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event topic: " + topic))
                .deadLetterTopic();
    }
}
