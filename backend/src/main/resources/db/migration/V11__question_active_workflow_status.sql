ALTER TABLE question
    DROP CONSTRAINT IF EXISTS question_workflow_status_check;

ALTER TABLE question
    ADD CONSTRAINT question_workflow_status_check CHECK (workflow_status IN
        ('DRAFT', 'ACTIVE', 'MISSING_ANSWER', 'MISSING_EXPLANATION', 'AI_GENERATED',
         'PENDING_REVIEW', 'APPROVED', 'READY_FOR_TEST', 'ARCHIVED', 'REJECTED'));

INSERT INTO lookup (id, lookup_type, lookup_code, lookup_meaning, lookup_description, sort_order, active)
VALUES ('00000000-0000-0000-0003-000000000010', 'QUESTION_WORKFLOW_STATUS', 'ACTIVE', 'Active', 'Question is active for use', 2, TRUE)
ON CONFLICT (lookup_type, lookup_code) DO UPDATE
SET lookup_meaning = EXCLUDED.lookup_meaning,
    lookup_description = EXCLUDED.lookup_description,
    active = TRUE;

UPDATE lookup
SET sort_order = sort_order + 1
WHERE lookup_type = 'QUESTION_WORKFLOW_STATUS'
  AND lookup_code NOT IN ('DRAFT', 'ACTIVE')
  AND sort_order >= 2;
