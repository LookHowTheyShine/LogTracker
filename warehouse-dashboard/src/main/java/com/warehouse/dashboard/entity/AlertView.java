package com.warehouse.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Immutable
@Getter
public class AlertView {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "warehouse_zone")
    private String warehouseZone;

    @Column(name = "error_code")
    private String errorCode;

    private String severity;

    private String status;

    @Column(name = "processed_at")
    private Instant processedAt;
}
