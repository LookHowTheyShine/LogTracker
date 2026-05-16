package com.warehouse.dashboard.service;

import com.warehouse.dashboard.model.AlertDto;
import com.warehouse.dashboard.model.DashboardStats;
import com.warehouse.dashboard.model.LabelCount;
import com.warehouse.dashboard.repository.DashboardAlertRepository;
import com.warehouse.dashboard.repository.DashboardIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertStatsService {

    private final DashboardAlertRepository    alertRepo;
    private final DashboardIncidentRepository incidentRepo;
    private final RedisStatsService           redisStats;
    private final ConsumerLagService          lagService;

    @Transactional(readOnly = true)
    public DashboardStats buildStats() {
        List<AlertDto>   recent      = alertRepo.findAllByOrderByProcessedAtDesc(PageRequest.of(0, 50))
                                                .stream().map(AlertDto::new).toList();
        List<LabelCount> bySeverity  = alertRepo.countBySeverity();
        List<LabelCount> byZone      = alertRepo.countByZone();
        List<LabelCount> byErrorCode = alertRepo.countByErrorCode(PageRequest.of(0, 10));

        return DashboardStats.builder()
                .totalSaved(alertRepo.countByStatus("SAVED"))
                .totalDeduped(redisStats.getDedupedCount())
                .totalRateLimited(redisStats.getRateLimitedCount())
                .totalFailed(alertRepo.countByStatus("FAILED"))
                .openIncidents(incidentRepo.countByStatus("OPEN"))
                .consumerLag(lagService.getTotalLag())
                .redisUp(redisStats.isRedisUp())
                .kafkaUp(lagService.isKafkaUp())
                .recentAlerts(recent)
                .bySeverity(bySeverity)
                .byZone(byZone)
                .byErrorCode(byErrorCode)
                .build();
    }
}
