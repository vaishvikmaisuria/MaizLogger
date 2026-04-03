-- V2__alerts.sql — Alert rules and firing history

CREATE TABLE IF NOT EXISTS alert_rules (
    id             BIGSERIAL     PRIMARY KEY,
    app_id         BIGINT        NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    name           VARCHAR(255)  NOT NULL,
    metric_type    VARCHAR(50)   NOT NULL
                     CHECK (metric_type IN ('error_rate','p95_latency_ms','failed_requests')),
    threshold      NUMERIC(14,4) NOT NULL,
    window_minutes INT           NOT NULL DEFAULT 5,
    env            VARCHAR(100),   -- NULL = any environment
    release        VARCHAR(100),   -- NULL = any release
    enabled        BOOLEAN       NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS alert_firings (
    id             BIGSERIAL     PRIMARY KEY,
    rule_id        BIGINT        NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    app_id         BIGINT        NOT NULL,
    fired_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ,
    value_observed NUMERIC(14,4) NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'firing'
                     CHECK (status IN ('firing','resolved'))
);

CREATE INDEX IF NOT EXISTS idx_alert_firings_rule ON alert_firings(rule_id, fired_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_firings_app  ON alert_firings(app_id,  fired_at DESC);
