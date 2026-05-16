package com.warehouse.dashboard.repository;

import com.warehouse.dashboard.entity.IncidentView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DashboardIncidentRepository extends JpaRepository<IncidentView, UUID> {

    long countByStatus(String status);
}
