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
 * Pure persistence layer — owns the @Transactional boundary.
 *
 * Kept separate from RetryablePersistService so @Retryable (which wraps the
 * method call) and @Transactional (which wraps the DB work) live on different
 * Spring proxy beans. If both annotations were on the same method, the retry
 * would fire inside an already-rolled-back transaction and always fail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertPersistenceService {

    private final AlertRepository alertRepository;
    private final IncidentRepository incidentRepository;

    @Transactional
    public void persist(WarehouseErrorEvent event, AlertStatus status) {
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
