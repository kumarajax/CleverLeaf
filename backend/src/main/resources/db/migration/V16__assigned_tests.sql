CREATE TABLE admin_test (
    id UUID PRIMARY KEY,
    public_key VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    creator_subject VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_test_version (
    id UUID PRIMARY KEY,
    test_id UUID NOT NULL REFERENCES admin_test(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    time_allowed_seconds INTEGER NOT NULL,
    available_from TIMESTAMPTZ,
    available_until TIMESTAMPTZ,
    frozen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    results_published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (test_id, version_number)
);

CREATE TABLE admin_test_version_question (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES admin_test_version(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES question(id),
    question_order INTEGER NOT NULL,
    UNIQUE (version_id, question_order),
    UNIQUE (version_id, question_id)
);

CREATE TABLE assigned_test_import_job (
    id UUID PRIMARY KEY,
    object_key TEXT NOT NULL,
    actor_subject VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    failed_rows INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE assigned_test_assignment (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES admin_test_version(id) ON DELETE CASCADE,
    student_subject VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    import_job_id UUID REFERENCES assigned_test_import_job(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    reset_at TIMESTAMPTZ,
    UNIQUE (version_id, student_subject)
);

CREATE TABLE assigned_test_import_row (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES assigned_test_import_job(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    test_public_key VARCHAR(128),
    student_subject VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    message TEXT
);

ALTER TABLE test_attempt ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'RANDOM';
ALTER TABLE test_attempt ADD COLUMN assignment_id UUID REFERENCES assigned_test_assignment(id);
CREATE UNIQUE INDEX uq_test_attempt_assignment_active ON test_attempt(assignment_id) WHERE assignment_id IS NOT NULL;

CREATE INDEX idx_admin_test_creator ON admin_test(creator_subject, created_at DESC);
CREATE INDEX idx_assigned_test_student ON assigned_test_assignment(student_subject, assigned_at DESC);
CREATE INDEX idx_assigned_test_version ON assigned_test_assignment(version_id, status);
CREATE INDEX idx_assigned_import_job_actor ON assigned_test_import_job(actor_subject, created_at DESC);
