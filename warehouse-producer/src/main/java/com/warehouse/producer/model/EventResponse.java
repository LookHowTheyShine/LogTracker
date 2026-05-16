package com.warehouse.producer.model;

import java.time.Instant;

/**
 * Response body for POST /api/events.
 * Returns the eventId (generated or provided) so callers can correlate
 * their request with downstream Kafka/alert-processor logs.
 */
public record EventResponse(
        String eventId,
        String topic,
        Instant acceptedAt
) {}
