package com.warehouse.alertprocessor.entity;

import com.warehouse.alertprocessor.converter.JsonMapConverter;
import com.warehouse.alertprocessor.model.AlertStatus;
import com.warehouse.alertprocessor.model.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "alerts",
    indexes = {
        @Index(name = "idx_alerts_device_id",      columnList = "device_id"),
        @Index(name = "idx_alerts_error_code",      columnList = "error_code"),
        @Index(name = "idx_alerts_status",          columnList = "status"),
        @Index(name = "idx_alerts_processed_at",    columnList = "processed_at"),
        @Index(name = "idx_alerts_warehouse_zone",  columnList = "warehouse_zone")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "incident")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    /** Original eventId from the inbound WarehouseErrorEvent. */
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "warehouse_zone", length = 32)
    private String warehouseZone;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    /** Raw event payload serialised to JSON text. */
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> payload;

    /** Processing outcome — drives the alerts.processed.total metric tag. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    /** Original timestamp from the event producer. */
    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    /** Wall-clock time this service finished processing the event. */
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Incident this alert belongs to (null for DEDUPED / RATE_LIMITED). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;
}
