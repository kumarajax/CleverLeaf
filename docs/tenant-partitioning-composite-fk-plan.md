# Tenant Partitioning Composite FK Plan

This project is now prepared for explicit tenant-owned writes, but the final PostgreSQL partition cutover still requires primary key and foreign key conversion.

## Target Key Shape

Tenant-partitioned tables should use tenant-aware keys:

- Parent tables: `PRIMARY KEY (tenant_id, id)`
- Child tables: `FOREIGN KEY (tenant_id, parent_id) REFERENCES parent_table(tenant_id, id)`
- Natural uniqueness: `UNIQUE (tenant_id, ...)`

## First Table Family To Convert

Start with the question family:

1. `question`: `PRIMARY KEY (tenant_id, id)`
2. `question_option`: FK `(tenant_id, question_id)` to `question`
3. `question_answer`: FK `(tenant_id, question_id)` to `question`
4. `question_taxonomy_node`: FK `(tenant_id, question_id)` to `question`, FK `(tenant_id, taxonomy_node_id)` to `taxonomy_node`
5. `question_tag`: FK `(tenant_id, question_id)` to `question`
6. `question_workflow_event`: FK `(tenant_id, question_id)` to `question`

## Migration Pattern

Use a copy/swap migration for each family:

1. Create partitioned replacement tables with composite primary keys.
2. Create tenant partitions.
3. Copy existing rows ordered by `tenant_id`.
4. Recreate composite FKs and tenant-aware unique constraints.
5. Rename old tables to `_old`.
6. Rename replacement tables to the original names.
7. Run validation counts by tenant.
8. Drop `_old` tables only after application smoke tests pass.

Avoid changing primary keys in-place on heavily referenced tables. The current single-column primary keys are still required by existing FKs until the full family is converted together.
