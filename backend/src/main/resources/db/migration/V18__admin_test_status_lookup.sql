INSERT INTO lookup (id, lookup_type, lookup_code, lookup_meaning, lookup_description, sort_order, active)
VALUES
    ('00000000-0000-0000-0004-000000000001', 'ADMIN_TEST_STATUS', 'DRAFT', 'Draft', 'Admin test is being prepared', 1, TRUE),
    ('00000000-0000-0000-0004-000000000002', 'ADMIN_TEST_STATUS', 'ACTIVE', 'Active', 'Admin test is finalized and ready for assignment', 2, TRUE),
    ('00000000-0000-0000-0004-000000000003', 'ADMIN_TEST_STATUS', 'PUBLISHED', 'Published', 'Admin test has been assigned to students', 3, TRUE),
    ('00000000-0000-0000-0004-000000000004', 'ADMIN_TEST_STATUS', 'COMPLETED', 'Completed', 'All assigned students have submitted the test', 4, TRUE),
    ('00000000-0000-0000-0004-000000000005', 'ADMIN_TEST_STATUS', 'EXPIRED', 'Expired', 'Admin test availability window has ended', 5, TRUE)
ON CONFLICT (lookup_type, lookup_code) DO UPDATE
SET lookup_meaning = EXCLUDED.lookup_meaning,
    lookup_description = EXCLUDED.lookup_description,
    sort_order = EXCLUDED.sort_order,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;

UPDATE admin_test test
SET status = 'PUBLISHED',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM admin_test_version version
      JOIN assigned_test_assignment assignment ON assignment.version_id = version.id
      WHERE version.test_id = test.id
  );
