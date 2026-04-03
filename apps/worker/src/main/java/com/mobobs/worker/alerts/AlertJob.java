package com.mobobs.worker.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

/**
 * Periodic alert evaluation loop.
 *
 * <p>On each tick the job:
 * <ol>
 *   <li>Loads all enabled {@link AlertRule}s from Postgres.</li>
 *   <li>Evaluates each rule against ClickHouse data via {@link AlertEvaluator}.</li>
 *   <li>If the observed value &ge; threshold <em>and</em> no active firing exists in
 *       the last {@code 2 × windowMinutes}, inserts a new firing record.</li>
 *   <li>Logs and continues on per-rule errors so a bad rule cannot stall evaluation.</li>
 * </ol>
 *
 * <p>The 2× suppression window prevents alert storms while still detecting recurring
 * issues after the original window has elapsed.
 */
@Component
@ConditionalOnProperty(name = "mobobs.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class AlertJob {

    private static final Logger log = LoggerFactory.getLogger(AlertJob.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertEvaluator      evaluator;

    public AlertJob(AlertRuleRepository ruleRepository, AlertEvaluator evaluator) {
        this.ruleRepository = ruleRepository;
        this.evaluator      = evaluator;
    }

    @Scheduled(
            fixedDelayString   = "${mobobs.alerts.eval-interval-ms:60000}",
            initialDelayString = "10000")
    public void evaluate() {
        List<AlertRule> rules = ruleRepository.findAllEnabled();
        if (rules.isEmpty()) {
            return;
        }

        log.debug("Evaluating {} alert rule(s)", rules.size());

        for (AlertRule rule : rules) {
            try {
                double value = evaluator.evaluate(rule);
                log.debug("Rule '{}' ({}): observed={} threshold={}",
                        rule.name(), rule.metricType(), value, rule.threshold());

                if (value >= rule.threshold()) {
                    Duration suppressWindow = Duration.ofMinutes(rule.windowMinutes() * 2L);
                    if (!ruleRepository.hasActiveFiring(rule.id(), suppressWindow)) {
                        ruleRepository.insertFiring(rule.id(), rule.appId(), value);
                        log.warn("ALERT FIRED — '{}' ({}): observed={} >= threshold={}",
                                rule.name(), rule.metricType(), value, rule.threshold());
                    } else {
                        log.debug("Rule '{}' still firing — suppressing duplicate", rule.name());
                    }
                }
            } catch (SQLException e) {
                log.error("ClickHouse query failed for rule '{}' (id={}): {}",
                        rule.name(), rule.id(), e.getMessage());
            } catch (IllegalArgumentException e) {
                log.error("Invalid metric type for rule '{}': {}", rule.name(), e.getMessage());
            }
        }
    }
}
