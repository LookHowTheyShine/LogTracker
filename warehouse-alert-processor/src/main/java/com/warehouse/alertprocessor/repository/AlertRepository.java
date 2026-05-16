package com.warehouse.alertprocessor.repository;

import com.warehouse.alertprocessor.entity.Alert;
import com.warehouse.alertprocessor.model.AlertStatus;
import com.warehouse.alertprocessor.model.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    // ── Live feed (dashboard) ─────────────────────────────────────────────────

    Page<Alert> findAllByOrderByProcessedAtDesc(Pageable pageable);

    List<Alert> findTop50ByOrderByProcessedAtDesc();

    // ── Counts by outcome (dashboard stats + Prometheus) ─────────────────────

    long countByStatus(AlertStatus status);

    long countByStatusAndSeverity(AlertStatus status, Severity severity);

    long countByStatusAndWarehouseZone(AlertStatus status, String warehouseZone);

    long countByStatusAndErrorCode(AlertStatus status, String errorCode);

    // ── Aggregate queries used by dashboard ───────────────────────────────────

    @Query("SELECT a.severity, COUNT(a) FROM Alert a WHERE a.status = :status GROUP BY a.severity")
    List<Object[]> countBySeverityForStatus(AlertStatus status);

    @Query("SELECT a.warehouseZone, COUNT(a) FROM Alert a WHERE a.status = :status GROUP BY a.warehouseZone")
    List<Object[]> countByZoneForStatus(AlertStatus status);

    @Query("SELECT a.errorCode, COUNT(a) FROM Alert a WHERE a.status = :status GROUP BY a.errorCode ORDER BY COUNT(a) DESC")
    List<Object[]> countByErrorCodeForStatus(AlertStatus status);

    // ── Duplicate detection (Step 5 fallback) ────────────────────────────────

    boolean existsByEventId(String eventId);

    // ── Time-range queries ────────────────────────────────────────────────────

    long countByProcessedAtAfter(Instant since);
}
