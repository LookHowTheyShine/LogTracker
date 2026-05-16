package com.warehouse.dashboard.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardStats {

    private final long totalSaved;
    private final long totalDeduped;
    private final long totalRateLimited;
    private final long totalFailed;
    private final long openIncidents;
    private final long consumerLag;
    private final boolean redisUp;
    private final boolean kafkaUp;

    private final List<AlertDto>    recentAlerts;
    private final List<LabelCount>  bySeverity;
    private final List<LabelCount>  byZone;
    private final List<LabelCount>  byErrorCode;

    public long totalProcessed() {
        return totalSaved + totalDeduped + totalRateLimited + totalFailed;
    }
}
