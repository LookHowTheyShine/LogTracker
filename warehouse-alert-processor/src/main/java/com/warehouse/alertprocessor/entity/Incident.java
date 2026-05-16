package com.warehouse.alertprocessor.entity;

import com.warehouse.alertprocessor.model.IncidentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "incidents",
    indexes = {
        @Index(name = "idx_incidents_device_error", columnList = "device_id,error_code"),
        @Index(name = "idx_incidents_status",        columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "alerts")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "error_code", nullable = false)
    private String errorCode;

    @Column(name = "alert_count", nullable = false)
    private int alertCount = 0;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentStatus status;

    @OneToMany(mappedBy = "incident", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Alert> alerts = new ArrayList<>();
}
