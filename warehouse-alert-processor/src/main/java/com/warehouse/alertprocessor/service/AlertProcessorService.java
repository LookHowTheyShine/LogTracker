package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.AlertStatus;
import com.warehouse.alertprocessor.model.WarehouseErrorEvent;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Central orchestrator for inbound warehouse error events.
 *
 * Responsibilities:
 *   - Guard chain: dedup → rate-limit → retryable persist
 *   - Latency timing (wraps the entire pipeline via try-finally)
 *   - Outcome metrics for deduped + rate_limited paths
 *     (saved/failed outcomes are recorded by RetryablePersistService where
 *      the result is definitively known, avoiding double-counting)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertProcessorService {

    private final DeduplicationService    deduplicationService;
    private final RateLimitingService     rateLimitingService;
    private final RetryablePersistService retryablePersistService;
    private final MetricsService          metricsService;

    public void process(WarehouseErrorEvent event) {
        Timer.Sample sample = metricsService.startTimer();
        try {
            log.info("Processing event: eventId={} deviceId={} zone={} errorCode={} severity={}",
                    event.getEventId(), event.getDeviceId(), event.getWarehouseZone(),
                    event.getErrorCode(), event.getSeverity());

            // Guard 1 — Redis deduplication (Step 5)
            if (deduplicationService.isDuplicate(event)) {
                log.info("Suppressed duplicate: dedupKey={} ttlRemaining={}s",
                        event.dedupKey(), deduplicationService.getTtlSeconds(event));
                metricsService.recordOutcome(AlertStatus.DEDUPED);
                return;
            }

            // Guard 2 — Bucket4j rate limiting (Step 6)
            if (rateLimitingService.isRateLimited(event)) {
                log.info("Rate-limited: deviceId={} availableTokens={}",
                        event.getDeviceId(), rateLimitingService.availableTokens(event.getDeviceId()));
                metricsService.recordOutcome(AlertStatus.RATE_LIMITED);
                metricsService.recordRateLimitHit();
                return;
            }

            // Persist with retry; recover() routes to DLQ on exhaustion (Step 7)
            // saved/failed outcome metrics are recorded inside RetryablePersistService
            retryablePersistService.persistWithRetry(event);

        } finally {
            // Always record latency regardless of outcome or exception
            metricsService.stopTimer(sample);
        }
    }
}
