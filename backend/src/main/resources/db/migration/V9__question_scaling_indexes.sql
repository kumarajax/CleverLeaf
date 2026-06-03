CREATE INDEX idx_question_created_at_id_desc
    ON question (created_at DESC, id DESC);

CREATE INDEX idx_question_workflow_created_at_id_desc
    ON question (workflow_status, created_at DESC, id DESC);

CREATE INDEX idx_question_type_created_at_id_desc
    ON question (question_type, created_at DESC, id DESC);

CREATE INDEX idx_question_difficulty_created_at_id_desc
    ON question (difficulty, created_at DESC, id DESC);

CREATE INDEX idx_question_type_difficulty_workflow_created_at_id_desc
    ON question (question_type, difficulty, workflow_status, created_at DESC, id DESC);

CREATE INDEX idx_question_taxonomy_node_question
    ON question_taxonomy_node (taxonomy_node_id, question_id);

CREATE INDEX idx_question_option_question_sort
    ON question_option (question_id, sort_order);
