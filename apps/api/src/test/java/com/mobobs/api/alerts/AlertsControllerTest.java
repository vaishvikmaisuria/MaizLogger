package com.mobobs.api.alerts;

import com.mobobs.api.security.IngestApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static com.mobobs.api.alerts.AlertDtos.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AlertsController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = IngestApiKeyFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AlertsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRepository alertRepository;

    // ── GET /v1/alerts/rules ───────────────────────────────────────────────

    @Test
    void rules_returnsListOfRules() throws Exception {
        AlertRuleDto rule = new AlertRuleDto(
                1L, 1L, "High Error Rate", "error_rate",
                10.0, 5, "prod", null, true, OffsetDateTime.now());

        when(alertRepository.findRulesForApp(null)).thenReturn(List.of(rule));

        mockMvc.perform(get("/v1/alerts/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("High Error Rate"))
                .andExpect(jsonPath("$[0].metricType").value("error_rate"))
                .andExpect(jsonPath("$[0].threshold").value(10.0));
    }

    @Test
    void rules_withAppFilter_passesAppToRepository() throws Exception {
        when(alertRepository.findRulesForApp("demo-app")).thenReturn(List.of());

        mockMvc.perform(get("/v1/alerts/rules").param("app", "demo-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /v1/alerts/feed ────────────────────────────────────────────────

    @Test
    void feed_returnsFiresWithCountWrapper() throws Exception {
        AlertFiringDto firing = new AlertFiringDto(
                10L, 1L, "High Error Rate", "error_rate",
                10.0, 15.5, "firing",
                OffsetDateTime.parse("2025-01-01T12:00:00Z"), null);

        when(alertRepository.findRecentFirings(isNull(), any(), any(), eq(50)))
                .thenReturn(List.of(firing));

        mockMvc.perform(get("/v1/alerts/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.firings[0].ruleName").value("High Error Rate"))
                .andExpect(jsonPath("$.firings[0].valueObserved").value(15.5))
                .andExpect(jsonPath("$.firings[0].status").value("firing"));
    }

    @Test
    void feed_capsLimitAt500() throws Exception {
        when(alertRepository.findRecentFirings(isNull(), any(), any(), eq(500)))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/alerts/feed").param("limit", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}
