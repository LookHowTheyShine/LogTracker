package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Central orchestrator for inbound warehouse error events.
 *
 * Processing order (built up step by step):
 *   Step 3  → log event (current)
 *   Step 4  → persist to PostgreSQL
 *   Step 5  → Redis deduplication (suppress same deviceId+errorCode within 60s)
 *   Step 6  → Bucket4j rate limiting (max 10/min per deviceId)
 *   Step 7  → Spring Retry on DB failure + DLQ producer
 *   Step 9  → Micrometer metrics
 */
@Slf4j
@Service
public class AlertProcessorService {

    public void process(WarehouseErrorEvent event) {
        log.info("Processing event: eventId={} deviceId={} zone={} errorCode={} severity={}",
                event.getEventId(),
                event.getDeviceId(),
                event.getWarehouseZone(),
                event.getErrorCode(),
                event.getSeverity());

        // Placeholder — business logic added in Steps 4-9
    }
}
