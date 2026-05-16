package com.warehouse.dashboard.repository;

import com.warehouse.dashboard.entity.AlertView;
import com.warehouse.dashboard.model.LabelCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DashboardAlertRepository extends JpaRepository<AlertView, UUID> {

    List<AlertView> findAllByOrderByProcessedAtDesc(Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT new com.warehouse.dashboard.model.LabelCount(a.severity, COUNT(a)) " +
           "FROM AlertView a GROUP BY a.severity ORDER BY COUNT(a) DESC")
    List<LabelCount> countBySeverity();

    @Query("SELECT new com.warehouse.dashboard.model.LabelCount(a.warehouseZone, COUNT(a)) " +
           "FROM AlertView a GROUP BY a.warehouseZone ORDER BY COUNT(a) DESC")
    List<LabelCount> countByZone();

    @Query("SELECT new com.warehouse.dashboard.model.LabelCount(a.errorCode, COUNT(a)) " +
           "FROM AlertView a GROUP BY a.errorCode ORDER BY COUNT(a) DESC")
    List<LabelCount> countByErrorCode(Pageable pageable);
}
