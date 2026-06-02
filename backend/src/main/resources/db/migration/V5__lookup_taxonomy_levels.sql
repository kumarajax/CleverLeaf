CREATE TABLE lookup (
    id UUID PRIMARY KEY,
    lookup_type VARCHAR(64) NOT NULL,
    lookup_code VARCHAR(64) NOT NULL,
    lookup_meaning VARCHAR(128) NOT NULL,
    lookup_description VARCHAR(256) NOT NULL,
    sort_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (lookup_type, lookup_code)
);

INSERT INTO lookup (id, lookup_type, lookup_code, lookup_meaning, lookup_description, sort_order, active) VALUES
('00000000-0000-0000-0000-000000000001', 'TAXONOMY_TYPE', 'CURRICULUM', 'Curriculum', 'Top-level curriculum or board container', 1, TRUE),
('00000000-0000-0000-0000-000000000002', 'TAXONOMY_TYPE', 'EDITION', 'Edition', 'A version of a curriculum that groups the grade structure', 2, TRUE),
('00000000-0000-0000-0000-000000000003', 'TAXONOMY_TYPE', 'GRADE', 'Grade', 'A grade level inside a curriculum edition', 3, TRUE),
('00000000-0000-0000-0000-000000000004', 'TAXONOMY_TYPE', 'SUBJECT', 'Subject', 'A subject inside a grade', 4, TRUE),
('00000000-0000-0000-0000-000000000005', 'TAXONOMY_TYPE', 'CHAPTER', 'Chapter', 'A chapter inside a subject', 5, TRUE),
('00000000-0000-0000-0000-000000000006', 'TAXONOMY_TYPE', 'TOPIC', 'Topic', 'A topic inside a chapter', 6, TRUE);

ALTER TABLE taxonomy_node
    DROP CONSTRAINT IF EXISTS taxonomy_node_level_type_id_fkey;

ALTER TABLE taxonomy_node
    ADD CONSTRAINT fk_taxonomy_node_lookup_level
        FOREIGN KEY (level_type_id) REFERENCES lookup(id);

DROP TABLE taxonomy_level_type;
