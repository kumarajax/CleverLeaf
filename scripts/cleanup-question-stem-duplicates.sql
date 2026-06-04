-- One-time cleanup only. Do not place this file under Flyway migrations.
-- It removes generated near-duplicates by matching normalized question text
-- before the first "(", such as:
--   Area of a rectangle 5x4?
--   Area of a rectangle 5x4? (Question 67)
--   Simplify ratio 4:8
--   Simplify ratio 4:8 (Question 69)
--
-- Normal application duplicate checks must continue to use exact normalized
-- question text.

BEGIN;

CREATE TEMP TABLE duplicate_question_cleanup_ids ON COMMIT DROP AS
WITH question_stems AS (
    SELECT id,
           root_taxonomy_node_id,
           child_taxonomy_node_id,
           lower(regexp_replace(
               btrim(split_part(question_text, '(', 1)),
               '\s+',
               ' ',
               'g')) AS question_stem,
           position('(' in question_text) > 0 AS has_generated_suffix,
           created_at
    FROM question
    WHERE root_taxonomy_node_id IS NOT NULL
      AND child_taxonomy_node_id IS NOT NULL
),
ranked_questions AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY root_taxonomy_node_id, child_taxonomy_node_id, question_stem
               ORDER BY has_generated_suffix ASC, created_at ASC, id ASC
           ) AS duplicate_rank
    FROM question_stems
    WHERE question_stem IS NOT NULL
      AND question_stem <> ''
)
SELECT id
FROM ranked_questions
WHERE duplicate_rank > 1;

CREATE TEMP TABLE duplicate_question_attempt_ids ON COMMIT DROP AS
SELECT DISTINCT attempt_id AS id
FROM test_attempt_question
WHERE question_id IN (SELECT id FROM duplicate_question_cleanup_ids);

SELECT count(*) AS duplicate_questions_to_delete
FROM duplicate_question_cleanup_ids;

SELECT count(*) AS test_attempts_to_delete
FROM duplicate_question_attempt_ids;

DELETE FROM test_attempt
WHERE id IN (SELECT id FROM duplicate_question_attempt_ids);

DELETE FROM question_workflow_event
WHERE question_id IN (SELECT id FROM duplicate_question_cleanup_ids);

DELETE FROM question
WHERE id IN (SELECT id FROM duplicate_question_cleanup_ids);

UPDATE question
SET normalized_question_text = lower(regexp_replace(btrim(question_text), '\s+', ' ', 'g'))
WHERE question_text IS NOT NULL;

COMMIT;
