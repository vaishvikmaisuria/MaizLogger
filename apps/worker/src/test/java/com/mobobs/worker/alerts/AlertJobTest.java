package com.mobobs.worker.alerts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link AlertJob}.
 *
 * All dependencies are mocked — no Spring context, no Kafka, no database.
 */
@ExtendWith(MockitoExtension.class)
class AlertJobTest {

    @Mock AlertRuleRepository ruleRepository;
    @Mock AlertEvaluator      evaluator;

    @InjectMocks AlertJob job;

    // ── Helpers ────────────────────────────────────────────────────────────

    private static AlertRule rule(String metricType, double threshold) {
        return new AlertRule(42L, 1L, "demo-app", "Test Rule", metricType, threshold, 5, "prod", null);
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void firesAlertWhenObservedValueExceedsThreshold() throws SQLException {
        AlertRule r = rule("error_rate", 10.0);
        when(ruleRepository.findAllEnabled()).thenReturn(List.of(r));
        when(evaluator.evaluate(r)).thenReturn(15.5);
        when(ruleRepository.hasActiveFiring(eq(42L), any(Duration.class))).thenReturn(false);

        job.evaluate();

        verify(ruleRepository).insertFiring(42L, 1L, 15.5);
    }

    @Test
    void doesNotFireWhenValueBelowThreshold() throws SQLException {
        AlertRule r = rule("error_rate", 10.0);
        when(ruleRepository.findAllEnabled()).thenReturn(List.of(r));
        when(evaluator.evaluate(r)).thenReturn(5.0);

        job.evaluate();

        verify(ruleRepository, never()).insertFiring(anyLong(), anyLong(), anyDouble());
    }

    @Test
    void doesNotFireWhenValueEqualsThresholdAndAlreadyFiring() throws SQLException {
        AlertRule r = rule("p95_latency_ms", 2000.0);
        when(ruleRepository.findAllEnabled()).thenReturn(List.of(r));
        when(evaluator.evaluate(r)).thenReturn(2500.0);
        when(ruleRepository.hasActiveFiring(eq(42L), any(Duration.class))).thenReturn(true);

        job.evaluate();

        verify(ruleRepository, never()).insertFiring(anyLong(), anyLong(), anyDouble());
    }

    @Test
    void firesWhenValueEqualsThresholdExactly() throws SQLException {
        AlertRule r = rule("failed_requests", 50.0);
        when(ruleRepository.findAllEnabled()).thenReturn(List.of(r));
        when(evaluator.evaluate(r)).thenReturn(50.0);
        when(ruleRepository.hasActiveFiring(eq(42L), any(Duration.class))).thenReturn(false);

        job.evaluate();

        verify(ruleRepository).insertFiring(42L, 1L, 50.0);
    }

    @Test
    void continuesEvaluatingRemainingRulesAfterSqlException() throws SQLException {
        AlertRule r1 = new AlertRule(1L, 1L, "demo-app", "Rule A", "error_rate",      10.0, 5, null, null);
        AlertRule r2 = new AlertRule(2L, 1L, "demo-app", "Rule B", "failed_requests", 50.0, 5, null, null);

        when(ruleRepository.findAllEnabled()).thenReturn(List.of(r1, r2));
        when(evaluator.evaluate(r1)).thenThrow(new SQLException("ClickHouse timeout"));
        when(evaluator.evaluate(r2)).thenReturn(75.0);
        when(ruleRepository.hasActiveFiring(eq(2L), any(Duration.class))).thenReturn(false);

        // Must not throw; r2 should still be evaluated and fire
        job.evaluate();

        verify(ruleRepository, never()).insertFiring(eq(1L), anyLong(), anyDouble());
        verify(ruleRepository).insertFiring(2L, 1L, 75.0);
    }

    @Test
    void skipsEvaluationWhenNoRulesConfigured() throws SQLException {
        when(ruleRepository.findAllEnabled()).thenReturn(List.of());

        job.evaluate();

        verify(evaluator, never()).evaluate(any());
        verify(ruleRepository, never()).insertFiring(anyLong(), anyLong(), anyDouble());
    }

    @Test
    void suppressWindowIsDoubleTheRuleWindow() throws SQLException {
        AlertRule r = rule("error_rate", 10.0);
        when(ruleRepository.findAllEnabled()).thenReturn(List.of(r));
        when(evaluator.evaluate(r)).thenReturn(20.0);
        when(ruleRepository.hasActiveFiring(eq(42L), any(Duration.class))).thenReturn(false);

        job.evaluate();

        // Verify the suppress window passed to hasActiveFiring is 2 × windowMinutes = 10 min
        verify(ruleRepository).hasActiveFiring(42L, Duration.ofMinutes(10));
    }
}
