package com.lumera.store.lumera_store_api.shared.infrastructure.outbox.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(
            name = "aggregate_type",
            nullable = false,
            length = 50,
            updatable = false
    )
    private String aggregateType;

    @Column(
            name = "event_type",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEventEntity(
            UUID eventId,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            Instant occurredAt
    ) {
        this.eventId = Objects.requireNonNull(eventId);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.aggregateType = requireText(aggregateType, 50);
        this.eventType = requireText(eventType, 100);
        this.payload = Objects.requireNonNull(payload);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = Objects.requireNonNull(publishedAt);
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Outbox type must be non-blank and at most "
                            + maxLength + " characters"
            );
        }

        return value;
    }
}