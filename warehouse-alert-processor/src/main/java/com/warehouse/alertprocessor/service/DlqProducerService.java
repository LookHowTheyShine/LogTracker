package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqProducerService {

    private final KafkaTemplate<String, WarehouseErrorEvent> kafkaTemplate;
    private final MetricsService metricsService;

    @Value("${warehouse.kafka.topics.dlq}")
    private String dlqTopic;

    /**
     * Publishes a failed event to the DLQ topic.
     *
     * Uses deviceId as the Kafka message key so all failures from the same
     * device land on the same DLQ partition (preserves ordering for debugging).
     * Failure metadata is carried as Kafka headers so the DLQ consumer can
     * log/store it without needing a wrapper type.
     *
     * dlq.events.total is incremented only on confirmed send (in the async
     * callback) to avoid counting events that fail to even reach the broker.
     */
    public void sendToDlq(WarehouseErrorEvent event, String failureReason) {
        ProducerRecord<String, WarehouseErrorEvent> record =
                new ProducerRecord<>(dlqTopic, event.getDeviceId(), event);

        record.headers()
                .add("X-Failure-Reason",
                        truncate(failureReason, 500).getBytes(StandardCharsets.UTF_8))
                .add("X-Failed-At",
                        Instant.now().toString().getBytes(StandardCharsets.UTF_8))
                .add("X-Original-Topic",
                        "warehouse.errors".getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record)
                .thenAccept(result -> {
                    log.warn("DLQ published: eventId={} deviceId={} topic={} partition={} offset={}",
                            event.getEventId(), event.getDeviceId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    metricsService.recordDlqEvent();
                })
                .exceptionally(ex -> {
                    // DLQ publish failed — event is effectively lost.
                    // outcome=failed counter was already incremented in recover();
                    // dlq.events.total is intentionally NOT incremented here since
                    // the message never reached the broker.
                    log.error("CRITICAL: DLQ publish failed for eventId={} deviceId={}: {}",
                            event.getEventId(), event.getDeviceId(), ex.getMessage(), ex);
                    return null;
                });
    }

    private String truncate(String s, int max) {
        if (s == null) return "unknown";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
