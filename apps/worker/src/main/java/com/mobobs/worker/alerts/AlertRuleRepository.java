package com.mobobs.worker.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Postgres repository for alert rules and firings.
 *
 * <p>Conditional on {@code mobobs.alerts.enabled=true} (default) so that worker
 * integration tests can disable it with {@code mobobs.alerts.enabled=false} and
 * avoid needing a Postgres schema in test.
 */
@Repository
@ConditionalOnProperty(name = "mobobs.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class AlertRuleRepository {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleRepository.class);

    private final JdbcTemplate jdbc;

    public AlertRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns all enabled rules, joining {@code apps} to resolve {@code appName}. */
    public List<AlertRule> findAllEnabled() {
        return jdbc.query(
                """
                SELECT r.id, r.app_id, a.name AS app_name, r.name,
                       r.metric_type, r.threshold, r.window_minutes, r.env, r.release
                FROM   alert_rules r
                JOIN   apps        a ON a.id = r.app_id
                WHERE  r.enabled = true
                ORDER  BY r.id
                """,
                (rs, row) -> new AlertRule(
                        rs.getLong("id"),
                        rs.getLong("app_id"),
                        rs.getString("app_name"),
                        rs.getString("name"),
                        rs.getString("metric_type"),
                        rs.getDouble("threshold"),
                        rs.getInt("window_minutes"),
                        rs.getString("env"),
                        rs.getString("release"))
        );
    }

    /**
     * Returns {@code true} if the rule already has an active (status='firing') firing
     * within {@code since} duration, preventing alert storms.
     */
    public boolean hasActiveFiring(long ruleId, Duration since) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(since);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_firings WHERE rule_id = ? AND status = 'firing' AND fired_at >= ?",
                Integer.class,
                ruleId, cutoff);
        return count != null && count > 0;
    }

    /** Inserts a new firing record with status='firing'. */
    public void insertFiring(long ruleId, long appId, double value) {
        jdbc.update(
                "INSERT INTO alert_firings (rule_id, app_id, value_observed, status) VALUES (?, ?, ?, 'firing')",
                ruleId, appId, value);
        log.info("Alert firing inserted: rule_id={} app_id={} value={}", ruleId, appId, value);
    }

    /** Returns the total number of rules configured for an app (used during bootstrap). */
    public int countForApp(long appId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_rules WHERE app_id = ?", Integer.class, appId);
        return n == null ? 0 : n;
    }
}
