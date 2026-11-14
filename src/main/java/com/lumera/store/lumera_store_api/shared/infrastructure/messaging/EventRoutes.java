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
            @Value("${app.kafka.topics.product-created:lumera.product.created.v1}") String products,
            @Value("${app.kafka.topics.product-created-dlt:lumera.product.created.v1.DLT}") String productsDlt,
            @Value("${app.kafka.topics.order-created:lumera.order.created.v1}") String orders,
            @Value("${app.kafka.topics.order-created-dlt:lumera.order.created.v1.DLT}") String ordersDlt,
            @Value("${app.kafka.topics.stock-reservation-result:lumera.stock.reservation.result.v1}") String results,
            @Value("${app.kafka.topics.stock-reservation-result-dlt:lumera.stock.reservation.result.v1.DLT}") String resultsDlt) {
        routes = List.of(new Route("Product", "ProductCreated", products, productsDlt),
                new Route("Order", "OrderCreated", orders, ordersDlt),
                new Route("StockReservation", "StockReservationResult", results, resultsDlt));
        Assert.isTrue(routes.stream().map(Route::topic).distinct().count() == routes.size(),
                "Each event route must have its own topic");
    }

    public List<Route> all() { return routes; }

    public String deadLetterTopicFor(String topic) {
        return routes.stream().filter(route -> route.topic().equals(topic)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event topic: " + topic))
                .deadLetterTopic();
    }
}
