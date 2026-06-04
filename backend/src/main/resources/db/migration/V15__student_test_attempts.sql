CREATE TABLE test_attempt (
    id UUID PRIMARY KEY,
    student_subject VARCHAR(256) NOT NULL,
    test_name VARCHAR(256) NOT NULL,
    taxonomy_node_id UUID NOT NULL REFERENCES taxonomy_node(id),
    difficulty VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    score_points INTEGER,
    max_points INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_attempt_question (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES test_attempt(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES question(id),
    question_order INTEGER NOT NULL,
    submitted_answer TEXT,
    correct BOOLEAN,
    answered_at TIMESTAMPTZ,
    points_awarded INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (attempt_id, question_order),
    UNIQUE (attempt_id, question_id)
);

CREATE INDEX idx_test_attempt_student_started
    ON test_attempt(student_subject, started_at DESC);

CREATE INDEX idx_test_attempt_question_order
    ON test_attempt_question(attempt_id, question_order);

CREATE INDEX idx_test_attempt_question_question
    ON test_attempt_question(question_id);
