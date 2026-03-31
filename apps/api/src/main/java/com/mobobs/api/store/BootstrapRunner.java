package com.mobobs.api.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder encoder;

    public BootstrapRunner(ApiKeyRepository apiKeyRepository, BCryptPasswordEncoder encoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.encoder = encoder;
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
        String hash = encoder.encode(rawKey);

        apiKeyRepository.createApiKey(appId, prefix, hash, "bootstrap-demo-key");

        log.info("==========================================================");
        log.info("  DEMO INGEST API KEY (store this, shown once only):");
        log.info("  {}", rawKey);
        log.info("==========================================================");
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
