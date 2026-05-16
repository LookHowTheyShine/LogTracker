package com.warehouse.alertprocessor.consumer;

import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import com.warehouse.alertprocessor.service.AlertProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseErrorEventConsumer {

    private final AlertProcessorService alertProcessorService;

    @KafkaListener(
        topics = "${warehouse.kafka.topics.errors}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, WarehouseErrorEvent> record, Acknowledgment ack) {
        log.debug("Received record: topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());
        try {
            alertProcessorService.process(record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Unhandled error processing record partition={} offset={} — will NOT ack (Step 7 adds retry+DLQ): {}",
                    record.partition(), record.offset(), e.getMessage(), e);
            // Deliberately not acking: Kafka will redeliver.
            // Spring Retry + DLQ routing wired in Step 7.
        }
    }
}
