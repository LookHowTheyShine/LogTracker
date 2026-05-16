package com.warehouse.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Immutable
@Getter
public class IncidentView {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "warehouse_zone")
    private String warehouseZone;

    @Column(name = "error_code")
    private String errorCode;

    private String status;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "alert_count")
    private int alertCount;
}
