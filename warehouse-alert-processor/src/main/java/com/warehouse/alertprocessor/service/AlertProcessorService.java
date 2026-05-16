package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.entity.Alert;
import com.warehouse.alertprocessor.entity.Incident;
import com.warehouse.alertprocessor.model.AlertStatus;
import com.warehouse.alertprocessor.model.IncidentStatus;
import com.warehouse.alertprocessor.model.Severity;
import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import com.warehouse.alertprocessor.repository.AlertRepository;
import com.warehouse.alertprocessor.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Central orchestrator for inbound warehouse error events.
 *
 * Processing order (built up step by step):
 *   Step 3  → log event
 *   Step 4  → persist to PostgreSQL          ← current
 *   Step 5  → Redis deduplication
 *   Step 6  → Bucket4j rate limiting
 *   Step 7  → Spring Retry on DB failure + DLQ producer
 *   Step 9  → Micrometer metrics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertProcessorService {

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;

    @Transactional
    public void process(WarehouseErrorEvent event) {
        log.info("Processing event: eventId={} deviceId={} zone={} errorCode={} severity={}",
                event.getEventId(), event.getDeviceId(), event.getWarehouseZone(),
                event.getErrorCode(), event.getSeverity());

        // Step 5 — Redis dedup check goes here
        // Step 6 — Bucket4j rate limit check goes here

        persist(event, AlertStatus.SAVED);
    }

    /**
     * Maps the inbound event to an Alert, links it to an open Incident
     * (creating one if none exists), then saves both.
     *
     * Step 7 wraps this method with @Retryable so any DB failure retries
     * the entire transaction from scratch.
     */
    void persist(WarehouseErrorEvent event, AlertStatus status) {
        Alert alert = Alert.builder()
                .eventId(event.getEventId())
                .deviceId(event.getDeviceId())
                .warehouseZone(event.getWarehouseZone())
                .errorCode(event.getErrorCode())
                .severity(Severity.from(event.getSeverity()))
                .payload(event.getPayload())
                .status(status)
                .eventTimestamp(event.getTimestamp())
                .processedAt(Instant.now())
                .build();

        // Only link to an incident for events that were actually saved
        if (status == AlertStatus.SAVED) {
            Incident incident = resolveIncident(event);
            incident.setAlertCount(incident.getAlertCount() + 1);
            incidentRepository.save(incident);
            alert.setIncident(incident);
        }

        alertRepository.save(alert);
        log.debug("Persisted alert id={} status={}", alert.getId(), status);
    }

    private Incident resolveIncident(WarehouseErrorEvent event) {
        return incidentRepository
                .findByDeviceIdAndErrorCodeAndStatus(
                        event.getDeviceId(), event.getErrorCode(), IncidentStatus.OPEN)
                .orElseGet(() -> incidentRepository.save(
                        Incident.builder()
                                .deviceId(event.getDeviceId())
                                .errorCode(event.getErrorCode())
                                .alertCount(0)
                                .openedAt(Instant.now())
                                .status(IncidentStatus.OPEN)
                                .build()));
    }
}
