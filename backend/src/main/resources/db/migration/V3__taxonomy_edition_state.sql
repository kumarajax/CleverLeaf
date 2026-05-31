CREATE TABLE taxonomy_edition_state (
    curriculum_id UUID PRIMARY KEY REFERENCES taxonomy_node(id),
    active_edition_id UUID NOT NULL REFERENCES taxonomy_node(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
