ALTER TABLE taxonomy_node ADD COLUMN external_key VARCHAR(128);
ALTER TABLE question ADD COLUMN external_key VARCHAR(128);

CREATE UNIQUE INDEX uq_taxonomy_node_external_key
    ON taxonomy_node(external_key)
    WHERE external_key IS NOT NULL;

CREATE UNIQUE INDEX uq_question_external_key
    ON question(external_key)
    WHERE external_key IS NOT NULL;

CREATE TABLE bulk_import_run (
    id UUID PRIMARY KEY,
    import_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bulk_import_step_run (
    id UUID PRIMARY KEY,
    run_id UUID REFERENCES bulk_import_run(id) ON DELETE CASCADE,
    step_code VARCHAR(64) NOT NULL,
    object_key TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    failed_rows INTEGER NOT NULL DEFAULT 0,
    errors_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bulk_import_step_run_step_created
    ON bulk_import_step_run(step_code, created_at DESC);
