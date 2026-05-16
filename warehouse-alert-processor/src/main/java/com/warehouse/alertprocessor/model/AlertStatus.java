package com.warehouse.alertprocessor.model;

/** Outcome of processing a single inbound event. Drives the alerts.processed.total metric tag. */
public enum AlertStatus {
    SAVED,        // persisted successfully
    DEDUPED,      // suppressed — same deviceId+errorCode seen within 60s (Step 5)
    RATE_LIMITED, // suppressed — device exceeded 10 alerts/min (Step 6)
    FAILED        // all DB retries exhausted, sent to DLQ (Step 7)
}
