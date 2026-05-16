package com.warehouse.dashboard.model;

import com.warehouse.dashboard.entity.AlertView;
import lombok.Getter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
public class AlertDto {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String deviceId;
    private final String warehouseZone;
    private final String errorCode;
    private final String severity;
    private final String status;
    private final String processedAt;
    private final String severityClass;
    private final String statusClass;

    public AlertDto(AlertView a) {
        this.deviceId      = a.getDeviceId();
        this.warehouseZone = a.getWarehouseZone();
        this.errorCode     = a.getErrorCode();
        this.severity      = a.getSeverity();
        this.status        = a.getStatus();
        this.processedAt   = a.getProcessedAt() != null ? FMT.format(a.getProcessedAt()) : "—";
        this.severityClass = switch (a.getSeverity()) {
            case "CRITICAL" -> "danger";
            case "HIGH"     -> "warning";
            case "MEDIUM"   -> "info";
            default         -> "secondary";
        };
        this.statusClass   = switch (a.getStatus()) {
            case "SAVED"         -> "success";
            case "DEDUPED"       -> "info";
            case "RATE_LIMITED"  -> "warning";
            case "FAILED"        -> "danger";
            default              -> "secondary";
        };
    }
}
