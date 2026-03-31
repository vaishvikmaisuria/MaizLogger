package com.mobobs.api.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobobs.api.security.IngestApiKeyFilter;
import com.mobobs.api.store.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = IngestController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = IngestApiKeyFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class IngestControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngestService ingestService;

    @Test
    void missingRequiredFields_returns400() throws Exception {
        // event_id is missing (null)
        String payload = """
            {
              "events": [{
                "event_type": "app_start",
                "session_id": "sess-1",
                "app_name": "demo",
                "app_version": "1.0.0",
                "platform": "ios",
                "timestamp": "2025-01-01T00:00:00Z"
              }]
            }
            """;

        mockMvc.perform(post("/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("appId", 1L)
                .content(payload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEventType_returns400() throws Exception {
        String payload = """
            {
              "events": [{
                "event_id": "evt-1",
                "event_type": "unknown_type",
                "session_id": "sess-1",
                "app_name": "demo",
                "app_version": "1.0.0",
                "platform": "ios",
                "timestamp": "2025-01-01T00:00:00Z"
              }]
            }
            """;

        mockMvc.perform(post("/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("appId", 1L)
                .content(payload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void emptyEventsList_returns400() throws Exception {
        String payload = """
            { "events": [] }
            """;

        mockMvc.perform(post("/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("appId", 1L)
                .content(payload))
            .andExpect(status().isBadRequest());
    }

    @Test
    void validBatch_returns202() throws Exception {
        when(ingestService.processBatch(any(), anyLong(), anyString())).thenReturn(1);

        String payload = """
            {
              "events": [{
                "event_id": "evt-1",
                "event_type": "screen_view",
                "session_id": "sess-1",
                "app_name": "demo",
                "app_version": "1.0.0",
                "platform": "ios",
                "timestamp": "2025-01-01T00:00:00Z",
                "screen_name": "HomeScreen"
              }]
            }
            """;

        mockMvc.perform(post("/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("appId", 1L)
                .content(payload))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.accepted").value(1));
    }
}
