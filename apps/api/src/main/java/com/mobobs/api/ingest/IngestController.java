package com.mobobs.api.ingest;

import com.mobobs.api.telemetry.TelemetryDtos.TelemetryBatch;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @Valid @RequestBody TelemetryBatch batch,
            HttpServletRequest request) {

        long appId = (long) request.getAttribute("appId");
        String sourceIp = request.getRemoteAddr();

        int accepted = ingestService.processBatch(batch, appId, sourceIp);
        return ResponseEntity.accepted().body(Map.of("accepted", accepted));
    }
}
