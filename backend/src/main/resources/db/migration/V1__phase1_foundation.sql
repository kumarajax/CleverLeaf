CREATE TABLE taxonomy_level_type (
    id UUID PRIMARY KEY,
    level_key VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    allowed_parent_key VARCHAR(64),
    sort_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_taxonomy_level_parent FOREIGN KEY (allowed_parent_key)
        REFERENCES taxonomy_level_type(level_key)
);

INSERT INTO taxonomy_level_type (id, level_key, display_name, allowed_parent_key, sort_order) VALUES
('00000000-0000-0000-0000-000000000001', 'CURRICULUM', 'Curriculum', NULL, 1),
('00000000-0000-0000-0000-000000000002', 'EDITION', 'Edition', 'CURRICULUM', 2),
('00000000-0000-0000-0000-000000000003', 'GRADE', 'Grade', 'EDITION', 3),
('00000000-0000-0000-0000-000000000004', 'SUBJECT', 'Subject', 'GRADE', 4),
('00000000-0000-0000-0000-000000000005', 'CHAPTER', 'Chapter', 'SUBJECT', 5),
('00000000-0000-0000-0000-000000000006', 'TOPIC', 'Topic', 'CHAPTER', 6);

CREATE TABLE taxonomy_node (
    id UUID PRIMARY KEY,
    level_type_id UUID NOT NULL REFERENCES taxonomy_level_type(id),
    parent_id UUID REFERENCES taxonomy_node(id),
    node_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED', 'ARCHIVED')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    cloned_from_id UUID REFERENCES taxonomy_node(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (parent_id, level_type_id, node_key)
);

CREATE TABLE question (
    id UUID PRIMARY KEY,
    taxonomy_node_id UUID NOT NULL REFERENCES taxonomy_node(id),
    question_type VARCHAR(32) NOT NULL CHECK (question_type IN
        ('SINGLE_SELECT', 'MULTIPLE_SELECT', 'TRUE_FALSE', 'FILL_BLANK', 'NUMERICAL')),
    difficulty VARCHAR(16) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    workflow_status VARCHAR(32) NOT NULL CHECK (workflow_status IN
        ('DRAFT', 'MISSING_ANSWER', 'MISSING_EXPLANATION', 'AI_GENERATED',
         'PENDING_REVIEW', 'APPROVED', 'READY_FOR_TEST', 'ARCHIVED', 'REJECTED')),
    language VARCHAR(32) NOT NULL DEFAULT 'English',
    question_text TEXT NOT NULL,
    explanation TEXT,
    source_reference TEXT,
    source_url TEXT,
    license_category VARCHAR(64),
    learning_objective TEXT,
    ai_model_identifier VARCHAR(128),
    prompt_version VARCHAR(64),
    version_number INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE question_option (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    option_key VARCHAR(16) NOT NULL,
    option_text TEXT NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL,
    UNIQUE (question_id, option_key)
);

CREATE TABLE question_workflow_event (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES question(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor VARCHAR(256) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_taxonomy_node_parent ON taxonomy_node(parent_id);
CREATE INDEX idx_question_taxonomy ON question(taxonomy_node_id);
CREATE INDEX idx_question_status ON question(workflow_status);
