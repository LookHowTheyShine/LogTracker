package com.warehouse.alertprocessor.consumer;

import com.warehouse.alertprocessor.model.AlertStatus;
import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import com.warehouse.alertprocessor.service.AlertPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumes from warehouse.errors.dlq and persists each event as FAILED.
 *
 * This is the terminal handler for events that exhausted all DB retries in
 * RetryablePersistService. Two safety behaviours:
 *
 *   Idempotency  — if the event is already in the DB (unique eventId constraint),
 *                  the DataIntegrityViolationException is caught and the message
 *                  is acked without a second insert.
 *
 *   Poison pills — if the record value is null (deserialisation failure),
 *                  the message is acked immediately to unblock the partition.
 *                  All other exceptions leave the message un-acked so Kafka
 *                  redelivers it once the transient fault clears.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqConsumer {

    private final AlertPersistenceService alertPersistenceService;

    @KafkaListener(
        topics      = "${warehouse.kafka.topics.dlq}",
        groupId     = "${spring.kafka.consumer.group-id}-dlq",
        concurrency = "2"          // matches the 2 DLQ partitions
    )
    public void consume(ConsumerRecord<String, WarehouseErrorEvent> record, Acknowledgment ack) {
        WarehouseErrorEvent event = record.value();

        // Poison pill — null value means the producer sent something we can't deserialise.
        // Ack and skip; leaving it unacked would stall the partition forever.
        if (event == null) {
            log.error("DLQ poison pill at partition={} offset={} key={} — acking to unblock partition",
                    record.partition(), record.offset(), record.key());
            ack.acknowledge();
            return;
        }

        log.warn("DLQ consuming: eventId={} deviceId={} errorCode={} | reason='{}' failedAt={}",
                event.getEventId(), event.getDeviceId(), event.getErrorCode(),
                extractHeader(record, "X-Failure-Reason"),
                extractHeader(record, "X-Failed-At"));

        try {
            alertPersistenceService.persist(event, AlertStatus.FAILED);
            ack.acknowledge();
            log.info("DLQ event persisted as FAILED: eventId={} deviceId={}",
                    event.getEventId(), event.getDeviceId());

        } catch (DataIntegrityViolationException e) {
            // eventId already exists — a previous DLQ consumer run already persisted this.
            // Safe to ack; the existing row is the authoritative record.
            log.info("DLQ event already persisted (idempotent skip): eventId={}", event.getEventId());
            ack.acknowledge();

        } catch (Exception e) {
            // Transient failure (DB down, connection pool exhausted, etc.).
            // Do NOT ack — Kafka will redeliver after max.poll.interval.ms expires.
            log.error("Failed to persist DLQ event eventId={}: {} — not acking (will redeliver)",
                    event.getEventId(), e.getMessage(), e);
        }
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "unknown" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
