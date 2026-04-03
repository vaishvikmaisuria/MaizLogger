package com.mobobs.api.alerts;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

import static com.mobobs.api.alerts.AlertDtos.*;

/** Postgres repository for the alert read endpoints. */
@Repository
public class AlertRepository {

    private final JdbcTemplate jdbc;

    public AlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lists configured rules. Pass {@code null} or empty string for {@code appName}
     * to return rules for all apps.
     */
    public List<AlertRuleDto> findRulesForApp(String appName) {
        boolean filterApp = appName != null && !appName.isBlank();
        String sql = """
                SELECT r.id, r.app_id, r.name, r.metric_type, r.threshold,
                       r.window_minutes, r.env, r.release, r.enabled, r.created_at
                FROM   alert_rules r
                JOIN   apps        a ON a.id = r.app_id
                """ + (filterApp ? "WHERE a.name = ?\n" : "") + "ORDER BY r.created_at";

        return filterApp
                ? jdbc.query(sql, this::mapRule, appName)
                : jdbc.query(sql, this::mapRule);
    }

    /**
     * Returns recent alert firings between {@code from} and {@code to}, newest first.
     * Pass {@code null} or empty {@code appName} to return firings for all apps.
     */
    public List<AlertFiringDto> findRecentFirings(
            String appName, OffsetDateTime from, OffsetDateTime to, int limit) {
        boolean filterApp = appName != null && !appName.isBlank();
        String sql = """
                SELECT f.id, f.rule_id, r.name AS rule_name, r.metric_type,
                       r.threshold, f.value_observed, f.status, f.fired_at, f.resolved_at
                FROM   alert_firings f
                JOIN   alert_rules   r ON r.id  = f.rule_id
                JOIN   apps          a ON a.id  = f.app_id
                WHERE  f.fired_at BETWEEN ? AND ?
                """ + (filterApp ? "  AND a.name = ?\n" : "") +
                "ORDER BY f.fired_at DESC LIMIT ?";

        if (filterApp) {
            return jdbc.query(sql, this::mapFiring, from, to, appName, limit);
        } else {
            return jdbc.query(sql, this::mapFiring, from, to, limit);
        }
    }

    // ── Row mappers ────────────────────────────────────────────────────────

    private AlertRuleDto mapRule(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AlertRuleDto(
                rs.getLong("id"),
                rs.getLong("app_id"),
                rs.getString("name"),
                rs.getString("metric_type"),
                rs.getDouble("threshold"),
                rs.getInt("window_minutes"),
                rs.getString("env"),
                rs.getString("release"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private AlertFiringDto mapFiring(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AlertFiringDto(
                rs.getLong("id"),
                rs.getLong("rule_id"),
                rs.getString("rule_name"),
                rs.getString("metric_type"),
                rs.getDouble("threshold"),
                rs.getDouble("value_observed"),
                rs.getString("status"),
                rs.getObject("fired_at",    OffsetDateTime.class),
                rs.getObject("resolved_at", OffsetDateTime.class));
    }
}
