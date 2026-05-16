package com.warehouse.alertprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Inbound event schema — matches the JSON produced by warehouse-producer.
 *
 * Example:
 * {
 *   "eventId": "uuid",
 *   "deviceId": "WH-RACK-042",
 *   "warehouseZone": "ZONE-B",
 *   "errorCode": "TEMP_THRESHOLD_EXCEEDED",
 *   "severity": "HIGH",
 *   "payload": { "temperature": 38.2, "threshold": 35.0 },
 *   "timestamp": "2024-01-15T10:30:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WarehouseErrorEvent {

    private String eventId;
    private String deviceId;
    private String warehouseZone;
    private String errorCode;
    private String severity;
    private Map<String, Object> payload;
    private Instant timestamp;

    /** Composite key used for deduplication and rate-limiting (Step 5, 6). */
    public String dedupKey() {
        return deviceId + ":" + errorCode;
    }
}
