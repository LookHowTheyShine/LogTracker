package com.warehouse.alertprocessor.repository;

import com.warehouse.alertprocessor.entity.Incident;
import com.warehouse.alertprocessor.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findByDeviceIdAndErrorCodeAndStatus(
            String deviceId, String errorCode, IncidentStatus status);

    long countByStatus(IncidentStatus status);
}
