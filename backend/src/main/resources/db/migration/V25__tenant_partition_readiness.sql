ALTER TABLE ai_source_chunk
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE ai_source_chunk chunk
SET tenant_id = job.tenant_id
FROM ai_generation_job job
WHERE chunk.job_id = job.id
  AND chunk.tenant_id IS NULL;

ALTER TABLE ai_source_chunk
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE ai_source_chunk
    ADD CONSTRAINT fk_ai_source_chunk_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id);

ALTER TABLE ai_generated_question
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE ai_generated_question generated
SET tenant_id = job.tenant_id
FROM ai_generation_job job
WHERE generated.job_id = job.id
  AND generated.tenant_id IS NULL;

ALTER TABLE ai_generated_question
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE ai_generated_question
    ADD CONSTRAINT fk_ai_generated_question_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(id);

CREATE INDEX IF NOT EXISTS idx_ai_source_chunk_tenant_job
    ON ai_source_chunk (tenant_id, job_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_ai_generated_question_tenant_job
    ON ai_generated_question (tenant_id, job_id, review_status, status);

ALTER TABLE admin_test
    DROP CONSTRAINT IF EXISTS admin_test_public_key_key;

ALTER TABLE admin_test
    ADD CONSTRAINT uq_admin_test_tenant_public_key
        UNIQUE (tenant_id, public_key);

ALTER TABLE admin_test_version
    DROP CONSTRAINT IF EXISTS admin_test_version_test_id_version_number_key;

ALTER TABLE admin_test_version
    ADD CONSTRAINT uq_admin_test_version_tenant_test_version
        UNIQUE (tenant_id, test_id, version_number);

ALTER TABLE admin_test_version_question
    DROP CONSTRAINT IF EXISTS admin_test_version_question_version_id_question_order_key;

ALTER TABLE admin_test_version_question
    DROP CONSTRAINT IF EXISTS admin_test_version_question_version_id_question_id_key;

ALTER TABLE admin_test_version_question
    ADD CONSTRAINT uq_admin_test_version_question_tenant_order
        UNIQUE (tenant_id, version_id, question_order);

ALTER TABLE admin_test_version_question
    ADD CONSTRAINT uq_admin_test_version_question_tenant_question
        UNIQUE (tenant_id, version_id, question_id);

ALTER TABLE ai_source_chunk
    DROP CONSTRAINT IF EXISTS ai_source_chunk_job_id_chunk_index_key;

ALTER TABLE ai_source_chunk
    ADD CONSTRAINT uq_ai_source_chunk_tenant_job_chunk
        UNIQUE (tenant_id, job_id, chunk_index);

ALTER TABLE lookup
    DROP CONSTRAINT IF EXISTS lookup_lookup_type_lookup_code_key;

ALTER TABLE lookup
    ADD CONSTRAINT uq_lookup_tenant_type_code
        UNIQUE (tenant_id, lookup_type, lookup_code);

ALTER TABLE question_answer
    DROP CONSTRAINT IF EXISTS question_answer_question_id_sort_order_key;

ALTER TABLE question_answer
    ADD CONSTRAINT uq_question_answer_tenant_question_sort
        UNIQUE (tenant_id, question_id, sort_order);

ALTER TABLE question_option
    DROP CONSTRAINT IF EXISTS question_option_question_id_option_key_key;

ALTER TABLE question_option
    ADD CONSTRAINT uq_question_option_tenant_question_key
        UNIQUE (tenant_id, question_id, option_key);

ALTER TABLE taxonomy_node
    DROP CONSTRAINT IF EXISTS taxonomy_node_parent_id_level_type_id_node_key_key;

ALTER TABLE taxonomy_node
    ADD CONSTRAINT uq_taxonomy_node_tenant_parent_level_key
        UNIQUE (tenant_id, parent_id, level_type_id, node_key);

DROP INDEX IF EXISTS uq_question_primary_taxonomy;

CREATE UNIQUE INDEX uq_question_primary_taxonomy_tenant
    ON question_taxonomy_node (tenant_id, question_id)
    WHERE is_primary = TRUE;

DROP INDEX IF EXISTS uq_assigned_test_assignment_open;

CREATE UNIQUE INDEX uq_assigned_test_assignment_open_tenant
    ON assigned_test_assignment (tenant_id, version_id, lower(student_subject))
    WHERE status IN ('ASSIGNED', 'STARTED');

DROP INDEX IF EXISTS uq_test_attempt_assignment_active;

CREATE UNIQUE INDEX uq_test_attempt_assignment_active_tenant
    ON test_attempt (tenant_id, assignment_id)
    WHERE assignment_id IS NOT NULL;

COMMENT ON TABLE question IS
    'Tenant partition readiness: tenant_id is explicit. Full partitioning still requires converting primary keys and foreign keys to tenant-aware composite keys.';

COMMENT ON TABLE admin_test IS
    'Tenant partition readiness: natural uniqueness is tenant-aware. Full partitioning still requires primary key and foreign key conversion.';

COMMENT ON TABLE test_attempt IS
    'Tenant partition readiness: tenant_id is explicit. Full partitioning still requires primary key and foreign key conversion.';
