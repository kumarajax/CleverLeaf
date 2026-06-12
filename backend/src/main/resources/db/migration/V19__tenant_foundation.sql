CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    tenant_name VARCHAR(256) NOT NULL,
    tenant_key VARCHAR(128) NOT NULL,
    tenant_type VARCHAR(32) NOT NULL CHECK (tenant_type IN ('DEMO', 'CUSTOMER')),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_tenant_name_ci ON tenant (lower(tenant_name));
CREATE UNIQUE INDEX uq_tenant_key_ci ON tenant (lower(tenant_key));

INSERT INTO tenant (id, tenant_name, tenant_key, tenant_type, status)
VALUES ('00000000-0000-0000-0000-000000000100', 'DEMO', 'DEMO', 'DEMO', 'ACTIVE');

CREATE TABLE tenant_user_membership (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    user_subject VARCHAR(256) NOT NULL,
    email TEXT NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('ADMIN', 'STUDENT')),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by_subject VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_tenant_membership_subject
    ON tenant_user_membership (tenant_id, user_subject);

CREATE UNIQUE INDEX uq_tenant_membership_email
    ON tenant_user_membership (tenant_id, lower(email));

CREATE INDEX idx_tenant_membership_subject
    ON tenant_user_membership (user_subject, status);

CREATE TABLE tenant_invitation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    email TEXT NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('ADMIN', 'STUDENT')),
    invite_token_hash TEXT NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by_subject VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_tenant_invitation_pending
    ON tenant_invitation (tenant_id, lower(email), role)
    WHERE status = 'PENDING';

CREATE INDEX idx_tenant_invitation_token
    ON tenant_invitation (invite_token_hash);

ALTER TABLE IF EXISTS signup_requests ADD COLUMN account_type VARCHAR(32) NOT NULL DEFAULT 'STUDENT'
    CHECK (account_type IN ('ADMIN', 'STUDENT'));
ALTER TABLE IF EXISTS signup_requests ADD COLUMN tenant_id UUID REFERENCES tenant(id);
ALTER TABLE IF EXISTS signup_requests ADD COLUMN requested_tenant_name VARCHAR(256);
ALTER TABLE IF EXISTS signup_requests ADD COLUMN join_demo_tenant BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE IF EXISTS signup_requests ADD COLUMN invitation_id UUID REFERENCES tenant_invitation(id);

ALTER TABLE IF EXISTS taxonomy_level_type ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS lookup ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS taxonomy_node ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS taxonomy_edition_state ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS question ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS question_taxonomy_node ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS question_option ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS question_answer ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS question_tag ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS question_workflow_event ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS bulk_import_run ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS bulk_import_step_run ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS test_attempt ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS test_attempt_question ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS admin_test ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS admin_test_version ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS admin_test_version_question ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS assigned_test_import_job ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS assigned_test_assignment ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);
ALTER TABLE IF EXISTS assigned_test_import_row ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000100' REFERENCES tenant(id);

CREATE INDEX idx_taxonomy_node_tenant ON taxonomy_node (tenant_id, parent_id, status);
CREATE INDEX idx_question_tenant ON question (tenant_id, workflow_status, created_at DESC, id DESC);
CREATE INDEX idx_test_attempt_tenant_student ON test_attempt (tenant_id, student_subject, started_at DESC);
CREATE INDEX idx_admin_test_tenant_creator ON admin_test (tenant_id, creator_subject, created_at DESC);
