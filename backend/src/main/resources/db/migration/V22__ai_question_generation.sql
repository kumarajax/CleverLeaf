CREATE TABLE ai_generation_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    taxonomy_node_id UUID NOT NULL REFERENCES taxonomy_node(id),
    taxonomy_key TEXT NOT NULL,
    child_node_key TEXT NOT NULL,
    taxonomy_path TEXT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_object_key TEXT,
    source_filename TEXT,
    topic TEXT NOT NULL,
    instructions TEXT,
    question_count INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_generation_job_tenant_created
    ON ai_generation_job (tenant_id, created_at DESC);

CREATE TABLE ai_source_chunk (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_generation_job(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    page_start INTEGER,
    page_end INTEGER,
    source_reference TEXT NOT NULL,
    chunk_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (job_id, chunk_index)
);

CREATE TABLE ai_generated_question (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_generation_job(id) ON DELETE CASCADE,
    chunk_id UUID REFERENCES ai_source_chunk(id) ON DELETE SET NULL,
    taxonomy_key TEXT NOT NULL,
    child_node_key TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    question_text TEXT NOT NULL,
    explanation TEXT NOT NULL,
    source_reference TEXT NOT NULL,
    options_json JSONB NOT NULL,
    correct_option_keys_json JSONB NOT NULL,
    validation_errors_json JSONB NOT NULL,
    created_question_id UUID REFERENCES question(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_generated_question_job_status
    ON ai_generated_question (job_id, review_status, status);
