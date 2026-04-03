package com.mobobs.api.dashboard;

import com.mobobs.api.clickhouse.ClickHouseClient;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DashboardController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = IngestApiKeyFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClickHouseClient clickHouse;

    // ── /v1/metrics/overview ───────────────────────────────────────────────

    @Test
    void overview_noParams_returns200WithJson() throws Exception {
        when(clickHouse.queryOverview(isNull(), isNull(), isNull(),
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new Dtos.OverviewResponse(1000L, 200L, 15L, 500L, 20L, 1234.5));

        mockMvc.perform(get("/v1/metrics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1000))
                .andExpect(jsonPath("$.uniqueSessions").value(200))
                .andExpect(jsonPath("$.totalErrors").value(15))
                .andExpect(jsonPath("$.totalApiCalls").value(500))
                .andExpect(jsonPath("$.apiErrors").value(20))
                .andExpect(jsonPath("$.avgAppStartMs").value(1234.5));
    }

    @Test
    void overview_withFilters_returns200() throws Exception {
        when(clickHouse.queryOverview(any(), any(), any(),
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new Dtos.OverviewResponse(50L, 10L, 2L, 30L, 1L, 800.0));

        mockMvc.perform(get("/v1/metrics/overview")
                        .param("app", "demo")
                        .param("env", "prod")
                        .param("release", "v1.2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(50));
    }

    // ── /v1/errors/feed ────────────────────────────────────────────────────

    @Test
    void errorFeed_noParams_returns200WithRows() throws Exception {
        OffsetDateTime ts = OffsetDateTime.parse("2025-06-01T12:00:00Z");
        List<Dtos.ErrorRow> errorRows = List.of(
                new Dtos.ErrorRow("evt-1", "NullPointerException",
                        "message one", "v1.0.0", "ios", "HomeScreen", ts),
                new Dtos.ErrorRow("evt-2", "IOException",
                        "message two", "v1.0.1", "android", "CheckoutScreen", ts)
        );
        when(clickHouse.queryErrorFeed(isNull(), isNull(), isNull(),
                any(OffsetDateTime.class), any(OffsetDateTime.class), anyInt()))
                .thenReturn(errorRows);

        mockMvc.perform(get("/v1/errors/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.errors[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$.errors[0].errorClass").value("NullPointerException"))
                .andExpect(jsonPath("$.errors[0].platform").value("ios"))
                .andExpect(jsonPath("$.errors[1].eventId").value("evt-2"));
    }

    @Test
    void errorFeed_customLimit_passesLimitToClient() throws Exception {
        when(clickHouse.queryErrorFeed(isNull(), isNull(), isNull(),
                any(OffsetDateTime.class), any(OffsetDateTime.class), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/errors/feed").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ── /v1/api/latency ────────────────────────────────────────────────────

    @Test
    void apiLatency_noParams_returns200WithBuckets() throws Exception {
        OffsetDateTime bucket = OffsetDateTime.parse("2025-06-01T11:00:00Z");
        List<Dtos.LatencyBucket> buckets = List.of(
                new Dtos.LatencyBucket("/checkout", "POST", "v1.0.0",
                        bucket, 300L, 5L, 120.0, 340.0, 810.0)
        );
        when(clickHouse.queryApiLatency(isNull(), isNull(), isNull(),
                isNull(), isNull(),
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(buckets);

        mockMvc.perform(get("/v1/api/latency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[0].path").value("/checkout"))
                .andExpect(jsonPath("$.buckets[0].method").value("POST"))
                .andExpect(jsonPath("$.buckets[0].requestCount").value(300))
                .andExpect(jsonPath("$.buckets[0].errorCount").value(5))
                .andExpect(jsonPath("$.buckets[0].p50Ms").value(120.0))
                .andExpect(jsonPath("$.buckets[0].p95Ms").value(340.0))
                .andExpect(jsonPath("$.buckets[0].p99Ms").value(810.0));
    }

    @Test
    void apiLatency_withPathAndMethod_returns200() throws Exception {
        when(clickHouse.queryApiLatency(any(), any(), any(),
                any(), any(),
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/api/latency")
                        .param("path", "/checkout")
                        .param("method", "POST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets").isArray());
    }
}
