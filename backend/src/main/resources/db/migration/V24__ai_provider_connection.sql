CREATE TABLE ai_provider_connection (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    api_key_nonce TEXT NOT NULL,
    masked_api_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_verified_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider)
);

CREATE INDEX idx_ai_provider_connection_tenant
    ON ai_provider_connection (tenant_id, provider);
