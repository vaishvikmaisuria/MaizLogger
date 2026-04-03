package com.mobobs.api.alerts;

import java.time.OffsetDateTime;
import java.util.List;

/** DTOs for the {@code /v1/alerts} endpoints. */
public class AlertDtos {

    public record AlertRuleDto(
            long           id,
            long           appId,
            String         name,
            String         metricType,
            double         threshold,
            int            windowMinutes,
            String         env,
            String         release,
            boolean        enabled,
            OffsetDateTime createdAt
    ) {}

    public record AlertFiringDto(
            long           id,
            long           ruleId,
            String         ruleName,
            String         metricType,
            double         threshold,
            double         valueObserved,
            String         status,
            OffsetDateTime firedAt,
            OffsetDateTime resolvedAt   // nullable
    ) {}

    public record AlertFeedResponse(int count, List<AlertFiringDto> firings) {}
}
