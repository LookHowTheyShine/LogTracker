package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.AlertStatus;
import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Wraps AlertPersistenceService with Spring Retry.
 *
 * Retry policy: 3 total attempts, exponential backoff 500ms → 1s → 2s.
 *   Attempt 1 — immediate
 *   Attempt 2 — after 500 ms
 *   Attempt 3 — after 1 000 ms
 *   Recover    — after 2 000 ms (would-be 4th attempt) → DLQ
 *
 * DataIntegrityViolationException (duplicate key, constraint) is excluded —
 * retrying a constraint violation will never succeed.
 *
 * @Retryable and @Transactional intentionally live on different beans so that
 * each retry starts a fresh transaction rather than retrying inside an already-
 * rolled-back one.
 *
 * Metrics: records outcome=saved on success, outcome=failed in recover().
 * The Kafka consumer acks in both cases (recover() does NOT rethrow).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryablePersistService {

    private final AlertPersistenceService alertPersistenceService;
    private final DlqProducerService      dlqProducerService;
    private final MetricsService          metricsService;

    @Retryable(
        retryFor   = DataAccessException.class,
        noRetryFor = DataIntegrityViolationException.class,
        maxAttempts = 3,
        backoff    = @Backoff(delay = 500, multiplier = 2.0)
    )
    public void persistWithRetry(WarehouseErrorEvent event) {
        alertPersistenceService.persist(event, AlertStatus.SAVED);
        metricsService.recordOutcome(AlertStatus.SAVED);
    }

    /**
     * Called automatically after all retry attempts fail.
     * Does NOT rethrow — allows the Kafka consumer to ack the message.
     * Rethrowing would cause infinite Kafka-level redelivery on top of
     * Spring Retry's already-exhausted attempts.
     */
    @Recover
    public void recover(DataAccessException ex, WarehouseErrorEvent event) {
        log.error("All DB write attempts exhausted for eventId={} deviceId={} — routing to DLQ. Cause: {}",
                event.getEventId(), event.getDeviceId(), ex.getMessage());
        dlqProducerService.sendToDlq(event, ex.getMessage());
        metricsService.recordOutcome(AlertStatus.FAILED);
    }
}
