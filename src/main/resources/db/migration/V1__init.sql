-- ModVault MVP schema (v1)
-- See PRD v0.2 section 6 "数据模型".

CREATE TABLE upstreams (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(200) NOT NULL UNIQUE,
    base_url            TEXT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    connect_timeout_ms  INT NOT NULL DEFAULT 10000,
    read_timeout_ms     INT NOT NULL DEFAULT 120000,
    extra_headers       JSONB,
    max_tokens_field    VARCHAR(64) NOT NULL DEFAULT 'max_tokens',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE upstream_keys (
    id              UUID PRIMARY KEY,
    upstream_id     UUID NOT NULL REFERENCES upstreams(id) ON DELETE CASCADE,
    key_ciphertext  TEXT NOT NULL,
    key_masked      VARCHAR(32) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at    TIMESTAMPTZ,
    status          VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_upstream_keys_upstream ON upstream_keys(upstream_id);

CREATE TABLE model_mappings (
    id                UUID PRIMARY KEY,
    anthropic_model   VARCHAR(200) NOT NULL,
    upstream_id       UUID NOT NULL REFERENCES upstreams(id) ON DELETE CASCADE,
    upstream_model    VARCHAR(200) NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- One enabled mapping per anthropic model (A-18)
CREATE UNIQUE INDEX uq_model_mappings_enabled ON model_mappings(anthropic_model) WHERE enabled;

CREATE TABLE api_keys (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    key_hash    VARCHAR(64) NOT NULL UNIQUE,
    key_prefix  VARCHAR(8) NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_records (
    id              UUID PRIMARY KEY,
    request_id      VARCHAR(64) NOT NULL,
    api_key_id      UUID,
    anthropic_model VARCHAR(200),
    upstream_id     UUID,
    upstream_model  VARCHAR(200),
    status          VARCHAR(16) NOT NULL,
    input_tokens    INT,
    output_tokens   INT,
    stream          BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms      BIGINT,
    first_token_ms  BIGINT,
    error_type      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_usage_created ON usage_records(created_at);
CREATE INDEX idx_usage_api_key ON usage_records(api_key_id, created_at);
