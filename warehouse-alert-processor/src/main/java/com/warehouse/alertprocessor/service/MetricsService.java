package com.warehouse.alertprocessor.service;

import com.warehouse.alertprocessor.model.AlertStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Single source of truth for all custom Micrometer metrics.
 *
 * Metrics exposed (all visible at /actuator/prometheus):
 *
 *   alerts_processed_total{outcome="saved|deduped|rate_limited|failed"}
 *   alerts_processing_latency (histogram — p50/p95/p99, SLO buckets at 10/25/45/100/250ms)
 *   dlq_events_total
 *   rate_limit_hits_total
 *   kafka_consumer_* (auto-bound by Spring Kafka + Micrometer — no code needed here)
 */
@Component
public class MetricsService {

    // ── alerts.processed.total (one counter per outcome tag value) ────────────

    private final Counter savedCounter;
    private final Counter dedupedCounter;
    private final Counter rateLimitedCounter;
    private final Counter failedCounter;

    // ── alerts.processing.latency ─────────────────────────────────────────────

    private final Timer processingLatencyTimer;

    // ── dlq.events.total ──────────────────────────────────────────────────────

    private final Counter dlqEventsCounter;

    // ── rate.limit.hits.total ─────────────────────────────────────────────────

    private final Counter rateLimitHitsCounter;

    public MetricsService(MeterRegistry registry) {
        this.savedCounter        = outcomeCounter(registry, "saved");
        this.dedupedCounter      = outcomeCounter(registry, "deduped");
        this.rateLimitedCounter  = outcomeCounter(registry, "rate_limited");
        this.failedCounter       = outcomeCounter(registry, "failed");

        this.processingLatencyTimer = Timer.builder("alerts.processing.latency")
                .description("End-to-end event processing latency (dedup + rate-limit + DB write)")
                .publishPercentileHistogram(true)
                .serviceLevelObjectives(
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(45),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250))
                .register(registry);

        this.dlqEventsCounter = Counter.builder("dlq.events.total")
                .description("Total events successfully published to the DLQ topic")
                .register(registry);

        this.rateLimitHitsCounter = Counter.builder("rate.limit.hits.total")
                .description("Total events suppressed by per-device rate limiting")
                .register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call at the start of process() to capture a timestamp. */
    public Timer.Sample startTimer() {
        return Timer.start();
    }

    /** Call in a finally block to record the full processing duration. */
    public void stopTimer(Timer.Sample sample) {
        sample.stop(processingLatencyTimer);
    }

    public void recordOutcome(AlertStatus outcome) {
        switch (outcome) {
            case SAVED        -> savedCounter.increment();
            case DEDUPED      -> dedupedCounter.increment();
            case RATE_LIMITED -> rateLimitedCounter.increment();
            case FAILED       -> failedCounter.increment();
        }
    }

    public void recordDlqEvent() {
        dlqEventsCounter.increment();
    }

    public void recordRateLimitHit() {
        rateLimitHitsCounter.increment();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Counter outcomeCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("alerts.processed.total")
                .tag("outcome", outcome)
                .description("Total events processed, tagged by outcome")
                .register(registry);
    }
}
