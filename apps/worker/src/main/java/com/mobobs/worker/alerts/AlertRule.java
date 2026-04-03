package com.mobobs.worker.alerts;

/**
 * Domain model for a configured alert rule (read from Postgres {@code alert_rules}).
 *
 * @param id            primary key
 * @param appId         FK to {@code apps.id}
 * @param appName       resolved app name (used to filter ClickHouse {@code app} column)
 * @param name          human-readable rule name
 * @param metricType    one of: {@code error_rate}, {@code p95_latency_ms}, {@code failed_requests}
 * @param threshold     numeric threshold; alert fires when observed value &ge; threshold
 * @param windowMinutes look-back window for the evaluation query
 * @param env           environment filter (null = all environments)
 * @param release       release filter (null = all releases)
 */
public record AlertRule(
        long   id,
        long   appId,
        String appName,
        String name,
        String metricType,
        double threshold,
        int    windowMinutes,
        String env,
        String release
) {}
