ALTER TABLE assigned_test_assignment
    DROP CONSTRAINT IF EXISTS assigned_test_assignment_version_id_student_subject_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_assigned_test_assignment_open
    ON assigned_test_assignment(version_id, lower(student_subject))
    WHERE status IN ('ASSIGNED', 'STARTED');
