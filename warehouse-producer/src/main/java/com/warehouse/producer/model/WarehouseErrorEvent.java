package com.warehouse.producer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Outbound event schema published to warehouse.errors.
 *
 * eventId and timestamp are optional in the request — the producer
 * generates them automatically if absent, ensuring every Kafka message
 * has a stable, unique ID without requiring the caller to supply one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WarehouseErrorEvent {

    /** Auto-generated (UUID) if not supplied by the caller. */
    private String eventId;

    @NotBlank(message = "deviceId is required")
    private String deviceId;

    @NotBlank(message = "warehouseZone is required")
    private String warehouseZone;

    @NotBlank(message = "errorCode is required")
    private String errorCode;

    @NotBlank(message = "severity is required")
    @Pattern(
        regexp = "CRITICAL|HIGH|MEDIUM|LOW",
        message = "severity must be one of: CRITICAL, HIGH, MEDIUM, LOW"
    )
    private String severity;

    /** Arbitrary sensor payload — e.g. {"temperature": 38.2, "threshold": 35.0} */
    private Map<String, Object> payload;

    /** Auto-set to Instant.now() if not supplied by the caller. */
    private Instant timestamp;
}
