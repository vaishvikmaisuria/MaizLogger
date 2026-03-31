package com.mobobs.api.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ApiKeyRepository {

    private final JdbcTemplate jdbc;

    public ApiKeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ApiKeyRecord(long id, long appId, String keyPrefix, String keyHash) {}

    public List<ApiKeyRecord> findActiveByPrefix(String prefix) {
        return jdbc.query(
            "SELECT id, app_id, key_prefix, key_hash FROM api_keys WHERE key_prefix = ? AND active = true",
            (rs, rowNum) -> new ApiKeyRecord(
                rs.getLong("id"),
                rs.getLong("app_id"),
                rs.getString("key_prefix"),
                rs.getString("key_hash")
            ),
            prefix
        );
    }

    public long createApp(String name) {
        return jdbc.queryForObject(
            "INSERT INTO apps (name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
            Long.class,
            name
        );
    }

    public void createApiKey(long appId, String prefix, String hash, String label) {
        jdbc.update(
            "INSERT INTO api_keys (app_id, key_prefix, key_hash, label) VALUES (?, ?, ?, ?)",
            appId, prefix, hash, label
        );
    }
}
