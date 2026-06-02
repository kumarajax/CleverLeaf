CREATE TABLE question_taxonomy_node (
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    taxonomy_node_id UUID NOT NULL REFERENCES taxonomy_node(id),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (question_id, taxonomy_node_id)
);

INSERT INTO question_taxonomy_node (question_id, taxonomy_node_id, is_primary)
SELECT id, taxonomy_node_id, TRUE
FROM question;

CREATE UNIQUE INDEX uq_question_primary_taxonomy
    ON question_taxonomy_node(question_id)
    WHERE is_primary = TRUE;

CREATE INDEX idx_question_taxonomy_node
    ON question_taxonomy_node(taxonomy_node_id);

ALTER TABLE question DROP COLUMN taxonomy_node_id;

ALTER TABLE question ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL';
ALTER TABLE question ADD COLUMN created_by VARCHAR(256) NOT NULL DEFAULT 'migration';
ALTER TABLE question ADD COLUMN updated_by VARCHAR(256);

CREATE TABLE question_answer (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    answer_value TEXT NOT NULL,
    answer_type VARCHAR(32) NOT NULL,
    tolerance_value NUMERIC,
    case_sensitive BOOLEAN,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (question_id, sort_order)
);

CREATE TABLE question_tag (
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    tag_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (question_id, tag_code)
);
