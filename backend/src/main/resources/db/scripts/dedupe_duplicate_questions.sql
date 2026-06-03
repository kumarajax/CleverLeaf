-- Retains the oldest question for each root taxonomy + child taxonomy + normalized question text.
-- Deletes duplicate questions and dependent workflow events. Other dependent tables cascade from question.

WITH RECURSIVE taxonomy_ancestors AS (
    SELECT id AS descendant_id, id, parent_id
    FROM taxonomy_node
    UNION ALL
    SELECT ancestors.descendant_id, parent.id, parent.parent_id
    FROM taxonomy_ancestors ancestors
    JOIN taxonomy_node parent ON parent.id = ancestors.parent_id
),
taxonomy_roots AS (
    SELECT descendant_id, id AS root_id
    FROM taxonomy_ancestors
    WHERE parent_id IS NULL
)
UPDATE question question_row
SET root_taxonomy_node_id = taxonomy_roots.root_id,
    child_taxonomy_node_id = primary_assignment.taxonomy_node_id,
    normalized_question_text = lower(regexp_replace(btrim(question_row.question_text), '\s+', ' ', 'g'))
FROM question_taxonomy_node primary_assignment
JOIN taxonomy_roots ON taxonomy_roots.descendant_id = primary_assignment.taxonomy_node_id
WHERE primary_assignment.question_id = question_row.id
  AND primary_assignment.is_primary = TRUE;

WITH duplicate_questions AS (
    SELECT id
    FROM (
        SELECT question_row.id,
               row_number() OVER (
                   PARTITION BY root_taxonomy_node_id, child_taxonomy_node_id, normalized_question_text
                   ORDER BY created_at ASC, id ASC
               ) AS duplicate_rank
        FROM question question_row
        WHERE root_taxonomy_node_id IS NOT NULL
          AND child_taxonomy_node_id IS NOT NULL
          AND normalized_question_text IS NOT NULL
    ) ranked
    WHERE duplicate_rank > 1
)
DELETE FROM question_workflow_event
WHERE question_id IN (SELECT id FROM duplicate_questions);

WITH duplicate_questions AS (
    SELECT id
    FROM (
        SELECT question_row.id,
               row_number() OVER (
                   PARTITION BY root_taxonomy_node_id, child_taxonomy_node_id, normalized_question_text
                   ORDER BY created_at ASC, id ASC
               ) AS duplicate_rank
        FROM question question_row
        WHERE root_taxonomy_node_id IS NOT NULL
          AND child_taxonomy_node_id IS NOT NULL
          AND normalized_question_text IS NOT NULL
    ) ranked
    WHERE duplicate_rank > 1
)
DELETE FROM question
WHERE id IN (SELECT id FROM duplicate_questions);
