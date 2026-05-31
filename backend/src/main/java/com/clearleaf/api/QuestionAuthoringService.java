package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.sql.ResultSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionAuthoringService {
    private final JdbcClient jdbc;
    private final QuestionValidator validator = new QuestionValidator();

    public QuestionAuthoringService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<QuestionAdminRecord> list() {
        List<QuestionRow> rows = jdbc.sql("""
                SELECT q.id, q.taxonomy_node_id, n.display_name AS taxonomy_display_name, n.node_key AS taxonomy_node_key,
                       n.status AS taxonomy_status, q.question_type, q.difficulty, q.workflow_status,
                       q.question_text, q.explanation, q.source_reference, q.license_category
                FROM question q
                JOIN taxonomy_node n ON n.id = q.taxonomy_node_id
                ORDER BY q.created_at DESC
                """)
                .query((rs, rowNum) -> new QuestionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("taxonomy_node_id", UUID.class),
                        rs.getString("taxonomy_display_name"),
                        rs.getString("taxonomy_node_key"),
                        rs.getString("taxonomy_status"),
                        rs.getString("question_type"),
                        rs.getString("difficulty"),
                        rs.getString("workflow_status"),
                        rs.getString("question_text"),
                        rs.getString("explanation"),
                        rs.getString("source_reference"),
                        rs.getString("license_category")))
                .list();
        List<QuestionAdminRecord> result = new ArrayList<>();
        for (QuestionRow row : rows) {
            result.add(toAdminRecord(row));
        }
        return result;
    }

    @Transactional
    public CreatedQuestionResponse create(CreateQuestionRequest request) {
        UUID questionId = UUID.randomUUID();
        save(questionId, request, null);
        return new CreatedQuestionResponse(questionId, request.question().workflowStatus());
    }

    @Transactional
    public QuestionAdminRecord update(UUID id, CreateQuestionRequest request) {
        QuestionRow current = findQuestionRow(requireUuid(id, "id"));
        save(current.id, request, current.workflowStatus);
        return toAdminRecord(findQuestionRow(current.id));
    }

    @Transactional
    public void delete(UUID id) {
        UUID questionId = requireUuid(id, "id");
        if (jdbc.sql("SELECT COUNT(*) > 0 FROM question WHERE id = :id")
                .param("id", questionId)
                .query(Boolean.class)
                .single()) {
            jdbc.sql("DELETE FROM question_option WHERE question_id = :id")
                    .param("id", questionId)
                    .update();
            jdbc.sql("DELETE FROM question_workflow_event WHERE question_id = :id")
                    .param("id", questionId)
                    .update();
            jdbc.sql("DELETE FROM question WHERE id = :id")
                    .param("id", questionId)
                    .update();
        }
    }

    private void ensureActiveTaxonomyNode(UUID taxonomyNodeId) {
        boolean exists = jdbc.sql("SELECT COUNT(*) > 0 FROM taxonomy_node WHERE id = :id AND status = 'ACTIVE'")
                .param("id", taxonomyNodeId)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new IllegalArgumentException("Question taxonomy node is missing or inactive");
        }
    }

    private void save(UUID questionId, CreateQuestionRequest request, String previousWorkflowStatus) {
        if (request.taxonomyNodeId() == null) {
            throw new IllegalArgumentException("taxonomyNodeId is required");
        }
        if (request.question() == null) {
            throw new IllegalArgumentException("question is required");
        }
        List<String> errors = validator.validate(request.question());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Question is invalid: " + String.join("; ", errors));
        }
        ensureActiveTaxonomyNode(request.taxonomyNodeId());
        QuestionDraft question = request.question();
        jdbc.sql("""
                INSERT INTO question (
                    id, taxonomy_node_id, question_type, difficulty, workflow_status,
                    question_text, explanation, source_reference, license_category
                ) VALUES (
                    :id, :taxonomyNodeId, :questionType, :difficulty, :workflowStatus,
                    :questionText, :explanation, :sourceReference, :licenseCategory
                )
                ON CONFLICT (id) DO UPDATE SET
                    taxonomy_node_id = EXCLUDED.taxonomy_node_id,
                    question_type = EXCLUDED.question_type,
                    difficulty = EXCLUDED.difficulty,
                    workflow_status = EXCLUDED.workflow_status,
                    question_text = EXCLUDED.question_text,
                    explanation = EXCLUDED.explanation,
                    source_reference = EXCLUDED.source_reference,
                    license_category = EXCLUDED.license_category,
                    updated_at = CURRENT_TIMESTAMP,
                    version_number = question.version_number + 1
                """)
                .param("id", questionId)
                .param("taxonomyNodeId", request.taxonomyNodeId())
                .param("questionType", question.type().name())
                .param("difficulty", question.difficulty().name())
                .param("workflowStatus", question.workflowStatus().name())
                .param("questionText", question.questionText())
                .param("explanation", question.explanation())
                .param("sourceReference", question.sourceReference())
                .param("licenseCategory", question.licenseCategory())
                .update();
        jdbc.sql("DELETE FROM question_option WHERE question_id = :id")
                .param("id", questionId)
                .update();
        List<QuestionOption> options = question.options() == null ? List.of() : question.options();
        for (int index = 0; index < options.size(); index++) {
            QuestionOption option = options.get(index);
            jdbc.sql("""
                    INSERT INTO question_option
                        (id, question_id, option_key, option_text, correct, sort_order)
                    VALUES (:id, :questionId, :optionKey, :optionText, :correct, :sortOrder)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("questionId", questionId)
                    .param("optionKey", option.key())
                    .param("optionText", option.text())
                    .param("correct", option.correct())
                    .param("sortOrder", index)
                    .update();
        }
        jdbc.sql("""
                INSERT INTO question_workflow_event
                    (id, question_id, from_status, to_status, actor, notes)
                VALUES (:id, :questionId, :fromStatus, :toStatus, :actor, :notes)
                """)
                .param("id", UUID.randomUUID())
                .param("questionId", questionId)
                .param("fromStatus", previousWorkflowStatus)
                .param("toStatus", question.workflowStatus().name())
                .param("actor", requireText(request.actor(), "actor"))
                .param("notes", previousWorkflowStatus == null ? "Question created" : "Question updated")
                .update();
    }

    private QuestionRow findQuestionRow(UUID id) {
        return jdbc.sql("""
                SELECT q.id, q.taxonomy_node_id, n.display_name AS taxonomy_display_name, n.node_key AS taxonomy_node_key,
                       n.status AS taxonomy_status, q.question_type, q.difficulty, q.workflow_status,
                       q.question_text, q.explanation, q.source_reference, q.license_category
                FROM question q
                JOIN taxonomy_node n ON n.id = q.taxonomy_node_id
                WHERE q.id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new QuestionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("taxonomy_node_id", UUID.class),
                        rs.getString("taxonomy_display_name"),
                        rs.getString("taxonomy_node_key"),
                        rs.getString("taxonomy_status"),
                        rs.getString("question_type"),
                        rs.getString("difficulty"),
                        rs.getString("workflow_status"),
                        rs.getString("question_text"),
                        rs.getString("explanation"),
                        rs.getString("source_reference"),
                        rs.getString("license_category")))
                .single();
    }

    private QuestionAdminRecord toAdminRecord(QuestionRow row) {
        List<QuestionOption> options = jdbc.sql("""
                SELECT option_key, option_text, correct
                FROM question_option
                WHERE question_id = :id
                ORDER BY sort_order
                """)
                .param("id", row.id())
                .query((rs, rowNum) -> new QuestionOption(
                        rs.getString("option_key"),
                        rs.getString("option_text"),
                        rs.getBoolean("correct")))
                .list();
        return new QuestionAdminRecord(
                row.id(),
                row.taxonomyNodeId(),
                row.taxonomyNodeDisplayName() + " (" + row.taxonomyNodeKey() + ")",
                row.taxonomyNodeStatus(),
                row.questionType(),
                row.difficulty(),
                row.workflowStatus(),
                row.questionText(),
                row.explanation(),
                row.sourceReference(),
                row.licenseCategory(),
                options);
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private record QuestionRow(
            UUID id,
            UUID taxonomyNodeId,
            String taxonomyNodeDisplayName,
            String taxonomyNodeKey,
            String taxonomyNodeStatus,
            String questionType,
            String difficulty,
            String workflowStatus,
            String questionText,
            String explanation,
            String sourceReference,
            String licenseCategory) {
    }
}
