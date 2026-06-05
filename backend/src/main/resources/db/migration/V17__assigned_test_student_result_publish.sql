ALTER TABLE assigned_test_assignment ADD COLUMN results_published_at TIMESTAMPTZ;

UPDATE assigned_test_assignment assignment
SET results_published_at = version.results_published_at
FROM admin_test_version version
WHERE assignment.version_id = version.id
  AND version.results_published_at IS NOT NULL;

CREATE INDEX idx_assigned_test_results_published ON assigned_test_assignment(version_id, results_published_at);
