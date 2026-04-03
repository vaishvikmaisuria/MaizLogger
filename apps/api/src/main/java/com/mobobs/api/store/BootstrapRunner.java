package com.mobobs.api.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final ApiKeyRepository      apiKeyRepository;
    private final BCryptPasswordEncoder encoder;
    private final JdbcTemplate          jdbc;

    public BootstrapRunner(ApiKeyRepository apiKeyRepository,
                           BCryptPasswordEncoder encoder,
                           JdbcTemplate jdbc) {
        this.apiKeyRepository = apiKeyRepository;
        this.encoder          = encoder;
        this.jdbc             = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        long appId = apiKeyRepository.createApp("demo-app");

        // Check if keys already exist for this app
        String testPrefix = "demo0000";
        if (!apiKeyRepository.findActiveByPrefix(testPrefix.substring(0, 8)).isEmpty()) {
            log.info("Demo API key already exists — skipping bootstrap");
            return;
        }

        String rawKey = generateApiKey();
        String prefix = rawKey.substring(0, 8);
        String hash   = encoder.encode(rawKey);

        apiKeyRepository.createApiKey(appId, prefix, hash, "bootstrap-demo-key");

        log.info("==========================================================");
        log.info("  DEMO INGEST API KEY (store this, shown once only):");
        log.info("  {}", rawKey);
        log.info("==========================================================");

        seedDemoAlertRules(appId);
    }

    // ── Demo alert rules ───────────────────────────────────────────────────

    private void seedDemoAlertRules(long appId) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_rules WHERE app_id = ?", Integer.class, appId);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO alert_rules (app_id, name, metric_type, threshold, window_minutes, env) VALUES (?,?,?,?,?,?)",
                appId, "High Error Rate",      "error_rate",      10.0, 5, "prod");
        jdbc.update(
                "INSERT INTO alert_rules (app_id, name, metric_type, threshold, window_minutes, env) VALUES (?,?,?,?,?,?)",
                appId, "Slow p95 API Latency", "p95_latency_ms", 2000.0, 5, null);
        jdbc.update(
                "INSERT INTO alert_rules (app_id, name, metric_type, threshold, window_minutes, env) VALUES (?,?,?,?,?,?)",
                appId, "High Failed Requests", "failed_requests",  50.0, 5, null);
        log.info("Seeded 3 demo alert rules (error_rate, p95_latency_ms, failed_requests)");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
