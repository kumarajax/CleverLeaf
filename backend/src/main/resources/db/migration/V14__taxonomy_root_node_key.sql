ALTER TABLE taxonomy_node ADD COLUMN root_taxonomy_node_id UUID REFERENCES taxonomy_node(id);

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
UPDATE taxonomy_node node
SET root_taxonomy_node_id = taxonomy_roots.root_id,
    node_key = upper(btrim(node.node_key))
FROM taxonomy_roots
WHERE taxonomy_roots.descendant_id = node.id;

WITH duplicate_nodes AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY root_taxonomy_node_id, node_key
               ORDER BY created_at ASC, id ASC
           ) AS duplicate_rank
    FROM taxonomy_node
),
renamed_nodes AS (
    SELECT node.id,
           left(regexp_replace(upper(btrim(node.node_key || '_' || coalesce(parent.node_key, 'ROOT'))), '[^A-Z0-9]+', '_', 'g'), 119)
               || '_'
               || substring(replace(node.id::text, '-', '') FROM 1 FOR 8) AS resolved_node_key
    FROM taxonomy_node node
    JOIN duplicate_nodes duplicate ON duplicate.id = node.id
    LEFT JOIN taxonomy_node parent ON parent.id = node.parent_id
    WHERE duplicate.duplicate_rank > 1
)
UPDATE taxonomy_node node
SET node_key = renamed_nodes.resolved_node_key
FROM renamed_nodes
WHERE renamed_nodes.id = node.id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM taxonomy_node
        WHERE root_taxonomy_node_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Unable to backfill root_taxonomy_node_id for all taxonomy nodes';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM taxonomy_node
        GROUP BY root_taxonomy_node_id, node_key
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate taxonomy node_key exists under the same root taxonomy';
    END IF;
END $$;

ALTER TABLE taxonomy_node
    ALTER COLUMN root_taxonomy_node_id SET NOT NULL;

CREATE UNIQUE INDEX uq_taxonomy_root_node_key
    ON taxonomy_node(root_taxonomy_node_id, node_key);
