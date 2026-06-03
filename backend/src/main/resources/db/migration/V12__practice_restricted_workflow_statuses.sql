ALTER TABLE question
    DROP CONSTRAINT IF EXISTS question_workflow_status_check;

ALTER TABLE question
    ADD CONSTRAINT question_workflow_status_check CHECK (workflow_status IN
        ('DRAFT', 'ACTIVE', 'PRACTICE', 'RESTRICTED', 'MISSING_ANSWER', 'MISSING_EXPLANATION',
         'AI_GENERATED', 'PENDING_REVIEW', 'APPROVED', 'READY_FOR_TEST', 'ARCHIVED', 'REJECTED'));

INSERT INTO lookup (id, lookup_type, lookup_code, lookup_meaning, lookup_description, sort_order, active)
VALUES
    ('00000000-0000-0000-0003-000000000011', 'QUESTION_WORKFLOW_STATUS', 'PRACTICE', 'Practice', 'Question is available for practice', 3, TRUE),
    ('00000000-0000-0000-0003-000000000012', 'QUESTION_WORKFLOW_STATUS', 'RESTRICTED', 'Restricted', 'Question is restricted from general use', 4, TRUE)
ON CONFLICT (lookup_type, lookup_code) DO UPDATE
SET lookup_meaning = EXCLUDED.lookup_meaning,
    lookup_description = EXCLUDED.lookup_description,
    sort_order = EXCLUDED.sort_order,
    active = TRUE;

UPDATE lookup
SET sort_order = CASE lookup_code
    WHEN 'DRAFT' THEN 1
    WHEN 'ACTIVE' THEN 2
    WHEN 'PRACTICE' THEN 3
    WHEN 'RESTRICTED' THEN 4
    WHEN 'MISSING_ANSWER' THEN 5
    WHEN 'MISSING_EXPLANATION' THEN 6
    WHEN 'AI_GENERATED' THEN 7
    WHEN 'PENDING_REVIEW' THEN 8
    WHEN 'APPROVED' THEN 9
    WHEN 'READY_FOR_TEST' THEN 10
    WHEN 'ARCHIVED' THEN 11
    WHEN 'REJECTED' THEN 12
    ELSE sort_order
END
WHERE lookup_type = 'QUESTION_WORKFLOW_STATUS';
