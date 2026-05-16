package com.warehouse.producer.service;

import com.warehouse.producer.model.EventResponse;
import com.warehouse.producer.model.WarehouseErrorEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class EventProducerService {

    private final KafkaTemplate<String, WarehouseErrorEvent> kafkaTemplate;
    private final String errorsTopic;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public EventProducerService(
            KafkaTemplate<String, WarehouseErrorEvent> kafkaTemplate,
            @Value("${warehouse.kafka.topics.errors}") String errorsTopic,
            MeterRegistry meterRegistry) {

        this.kafkaTemplate = kafkaTemplate;
        this.errorsTopic   = errorsTopic;

        this.publishedCounter = Counter.builder("producer.events.published.total")
                .description("Events successfully sent to Kafka")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("producer.events.failed.total")
                .description("Events that failed to publish to Kafka")
                .register(meterRegistry);
    }

    /**
     * Enriches the event (fills eventId and timestamp if absent) then
     * publishes asynchronously to warehouse.errors.
     *
     * Uses deviceId as the Kafka message key — events from the same device
     * always land on the same partition, preserving per-device ordering.
     *
     * Returns immediately with 202 Accepted; the async callback logs
     * broker confirmation or failure without blocking the HTTP response.
     */
    public EventResponse publish(WarehouseErrorEvent event) {
        enrich(event);

        kafkaTemplate.send(errorsTopic, event.getDeviceId(), event)
                .thenAccept(result -> {
                    log.info("Published: eventId={} deviceId={} topic={} partition={} offset={}",
                            event.getEventId(), event.getDeviceId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    publishedCounter.increment();
                })
                .exceptionally(ex -> {
                    log.error("Publish failed: eventId={} deviceId={} cause={}",
                            event.getEventId(), event.getDeviceId(), ex.getMessage(), ex);
                    failedCounter.increment();
                    return null;
                });

        return new EventResponse(event.getEventId(), errorsTopic, event.getTimestamp());
    }

    private void enrich(WarehouseErrorEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
    }
}
