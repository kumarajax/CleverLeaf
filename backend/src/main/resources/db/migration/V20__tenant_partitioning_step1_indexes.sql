CREATE INDEX IF NOT EXISTS idx_lookup_tenant_type_code
    ON lookup (tenant_id, lookup_type, lookup_code);

CREATE INDEX IF NOT EXISTS idx_taxonomy_edition_state_tenant_curriculum
    ON taxonomy_edition_state (tenant_id, curriculum_id);

CREATE INDEX IF NOT EXISTS idx_question_tenant_type_created
    ON question (tenant_id, question_type, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_question_tenant_difficulty_created
    ON question (tenant_id, difficulty, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_question_tenant_type_difficulty_workflow_created
    ON question (tenant_id, question_type, difficulty, workflow_status, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_question_tenant_root_child_text
    ON question (tenant_id, root_taxonomy_node_id, child_taxonomy_node_id, normalized_question_text)
    WHERE root_taxonomy_node_id IS NOT NULL
      AND child_taxonomy_node_id IS NOT NULL
      AND normalized_question_text IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_question_taxonomy_node_tenant_node_question
    ON question_taxonomy_node (tenant_id, taxonomy_node_id, question_id);

CREATE INDEX IF NOT EXISTS idx_question_taxonomy_node_tenant_question
    ON question_taxonomy_node (tenant_id, question_id);

CREATE INDEX IF NOT EXISTS idx_question_option_tenant_question_sort
    ON question_option (tenant_id, question_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_question_answer_tenant_question_sort
    ON question_answer (tenant_id, question_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_question_tag_tenant_question
    ON question_tag (tenant_id, question_id);

CREATE INDEX IF NOT EXISTS idx_question_workflow_event_tenant_question_created
    ON question_workflow_event (tenant_id, question_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bulk_import_run_tenant_created_by
    ON bulk_import_run (tenant_id, created_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bulk_import_step_run_tenant_run
    ON bulk_import_step_run (tenant_id, run_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_test_attempt_tenant_assignment
    ON test_attempt (tenant_id, assignment_id)
    WHERE assignment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_test_attempt_tenant_taxonomy
    ON test_attempt (tenant_id, taxonomy_node_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_test_attempt_question_tenant_attempt_order
    ON test_attempt_question (tenant_id, attempt_id, question_order);

CREATE INDEX IF NOT EXISTS idx_test_attempt_question_tenant_question
    ON test_attempt_question (tenant_id, question_id);

CREATE INDEX IF NOT EXISTS idx_admin_test_tenant_public_key
    ON admin_test (tenant_id, public_key);

CREATE INDEX IF NOT EXISTS idx_admin_test_version_tenant_test
    ON admin_test_version (tenant_id, test_id, version_number);

CREATE INDEX IF NOT EXISTS idx_admin_test_version_question_tenant_version_order
    ON admin_test_version_question (tenant_id, version_id, question_order);

CREATE INDEX IF NOT EXISTS idx_admin_test_version_question_tenant_question
    ON admin_test_version_question (tenant_id, question_id);

CREATE INDEX IF NOT EXISTS idx_assigned_import_job_tenant_actor
    ON assigned_test_import_job (tenant_id, actor_subject, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assigned_assignment_tenant_student
    ON assigned_test_assignment (tenant_id, student_subject, assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_assigned_assignment_tenant_version_status
    ON assigned_test_assignment (tenant_id, version_id, status);

CREATE INDEX IF NOT EXISTS idx_assigned_assignment_tenant_results
    ON assigned_test_assignment (tenant_id, version_id, results_published_at);

CREATE INDEX IF NOT EXISTS idx_assigned_import_row_tenant_job_line
    ON assigned_test_import_row (tenant_id, job_id, line_number);

CREATE INDEX IF NOT EXISTS idx_tenant_invitation_email_status
    ON tenant_invitation (lower(email), status, created_at DESC);
