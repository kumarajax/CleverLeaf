package com.clearleaf.api;

import com.clearleaf.api.entity.QuestionAnswerEntity;
import com.clearleaf.api.entity.QuestionEntity;
import com.clearleaf.api.entity.QuestionOptionEntity;
import com.clearleaf.api.entity.QuestionTagEntity;
import com.clearleaf.api.entity.QuestionTagId;
import com.clearleaf.api.entity.QuestionTaxonomyNodeEntity;
import com.clearleaf.api.entity.QuestionTaxonomyNodeId;
import com.clearleaf.api.entity.QuestionWorkflowEventEntity;
import com.clearleaf.api.entity.TaxonomyNodeEntity;
import com.clearleaf.api.repository.QuestionRepository;
import com.clearleaf.api.repository.QuestionSpecifications;
import com.clearleaf.api.repository.TaxonomyNodeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class QuestionAuthoringService {
    private final QuestionRepository questions;
    private final TaxonomyNodeRepository taxonomyNodes;
    private final QuestionValidator validator = new QuestionValidator();

    @PersistenceContext
    private EntityManager entityManager;

    public QuestionAuthoringService(QuestionRepository questions, TaxonomyNodeRepository taxonomyNodes) {
        this.questions = questions;
        this.taxonomyNodes = taxonomyNodes;
    }

    @Transactional(readOnly = true)
    public Page<QuestionAdminRecord> list(QuestionSearchCriteria criteria, Pageable pageable) {
        Specification<QuestionEntity> specification = specification(criteria);
        if (specification == null) return Page.empty(pageable);
        return questions.findAll(specification, pageable).map(this::toAdminRecord);
    }

    @Transactional(readOnly = true)
    public QuestionCursorPage listCursor(QuestionSearchCriteria criteria, String cursor, int requestedSize) {
        int size = Math.clamp(requestedSize, 1, 100);
        Specification<QuestionEntity> specification = specification(criteria);
        if (specification == null) {
            return new QuestionCursorPage(List.of(), null, false, 0);
        }
        List<QuestionEntity> entities = findCursorPage(specification, parseCursor(cursor), size + 1);
        boolean hasNext = entities.size() > size;
        List<QuestionEntity> visible = hasNext ? entities.subList(0, size) : entities;
        String nextCursor = hasNext && !visible.isEmpty()
                ? encodeCursor(visible.getLast().getCreatedAt(), visible.getLast().getId())
                : null;
        return new QuestionCursorPage(visible.stream().map(this::toAdminRecord).toList(),
                nextCursor, hasNext, visible.size());
    }

    @Transactional(readOnly = true)
    public QuestionAdminRecord get(UUID id) {
        return toAdminRecord(findQuestion(requireUuid(id, "id")));
    }

    @Transactional
    public CreatedQuestionResponse create(CreateQuestionRequest request) {
        QuestionEntity question = new QuestionEntity();
        question.setId(UUID.randomUUID());
        save(question, request, null);
        return new CreatedQuestionResponse(question.getId(), request.question().workflowStatus());
    }

    @Transactional
    public QuestionAdminRecord update(UUID id, CreateQuestionRequest request) {
        QuestionEntity question = findQuestion(requireUuid(id, "id"));
        String previousWorkflowStatus = question.getWorkflowStatus();
        save(question, request, previousWorkflowStatus);
        return toAdminRecord(question);
    }

    @Transactional
    public void delete(UUID id) {
        questions.findById(requireUuid(id, "id")).ifPresent(questions::delete);
    }

    private Specification<QuestionEntity> specification(QuestionSearchCriteria criteria) {
        Specification<QuestionEntity> specification = Specification
                .where(QuestionSpecifications.questionType(criteria.questionType()))
                .and(QuestionSpecifications.difficulty(criteria.difficulty()))
                .and(QuestionSpecifications.workflowStatuses(criteria.normalizedWorkflowStatuses()))
                .and(QuestionSpecifications.textSearch(criteria.search()));
        Set<UUID> eligibleNodes = eligibleTaxonomyNodes(criteria);
        if (eligibleNodes != null) {
            if (eligibleNodes.isEmpty()) return null;
            specification = specification.and(QuestionSpecifications.assignedToAny(eligibleNodes));
        }
        return specification;
    }

    private List<QuestionEntity> findCursorPage(
            Specification<QuestionEntity> specification,
            CursorPosition cursor,
            int limit) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<QuestionEntity> query = criteriaBuilder.createQuery(QuestionEntity.class);
        Root<QuestionEntity> root = query.from(QuestionEntity.class);
        Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        if (cursor != null) {
            Predicate olderCreatedAt = criteriaBuilder.lessThan(root.get("createdAt"), cursor.createdAt());
            Predicate sameCreatedAtLowerId = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("createdAt"), cursor.createdAt()),
                    criteriaBuilder.lessThan(root.get("id"), cursor.id()));
            predicate = criteriaBuilder.and(predicate, criteriaBuilder.or(olderCreatedAt, sameCreatedAtLowerId));
        }
        query.select(root)
                .where(predicate)
                .orderBy(criteriaBuilder.desc(root.get("createdAt")), criteriaBuilder.desc(root.get("id")))
                .distinct(true);
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    private void save(QuestionEntity question, CreateQuestionRequest request, String previousWorkflowStatus) {
        if (request == null || request.question() == null) {
            throw new IllegalArgumentException("question is required");
        }
        List<String> errors = validator.validate(request.question());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Question is invalid: " + String.join("; ", errors));
        }
        String actor = requireText(request.actor(), "actor");
        QuestionDraft draft = request.question();
        List<QuestionTaxonomyAssignment> assignments = assignments(request);
        List<QuestionAnswer> answers = request.answers() == null ? List.of() : request.answers();
        validateAnswers(draft.type(), draft.workflowStatus(), answers);

        question.setQuestionType(draft.type().name());
        question.setDifficulty(draft.difficulty().name());
        question.setWorkflowStatus(draft.workflowStatus().name());
        question.setQuestionText(requireText(draft.questionText(), "question.questionText"));
        question.setExplanation(trimToNull(draft.explanation()));
        question.setSourceReference(trimToNull(draft.sourceReference()));
        question.setLicenseCategory(trimToNull(draft.licenseCategory()));
        if (question.getCreatedBy() == null) question.setCreatedBy(actor);
        question.setUpdatedBy(actor);

        clearReplaceableContent(question);
        if (previousWorkflowStatus != null) {
            questions.flush();
        }
        replaceOptions(question, draft.options());
        replaceAnswers(question, answers);
        replaceAssignments(question, assignments);
        setQuestionUniquenessScope(question);
        replaceTags(question, request.tags());
        addWorkflowEvent(question, previousWorkflowStatus, draft.workflowStatus().name(), actor);
        questions.save(question);
    }

    private List<QuestionTaxonomyAssignment> assignments(CreateQuestionRequest request) {
        List<QuestionTaxonomyAssignment> assignments = request.taxonomyAssignments();
        if (assignments == null || assignments.isEmpty()) {
            return List.of(new QuestionTaxonomyAssignment(
                    requireUuid(request.taxonomyNodeId(), "taxonomyNodeId"), true));
        }
        long primaryCount = assignments.stream().filter(QuestionTaxonomyAssignment::primary).count();
        if (primaryCount != 1) {
            throw new IllegalArgumentException("Exactly one primary taxonomy assignment is required");
        }
        return assignments;
    }

    private void clearReplaceableContent(QuestionEntity question) {
        question.getOptions().clear();
        question.getAnswers().clear();
        question.getTaxonomyAssignments().clear();
        question.getTags().clear();
    }

    private void replaceOptions(QuestionEntity question, List<QuestionOption> requested) {
        List<QuestionOption> options = requested == null ? List.of() : requested;
        for (int index = 0; index < options.size(); index++) {
            QuestionOption option = options.get(index);
            QuestionOptionEntity entity = new QuestionOptionEntity();
            entity.setId(UUID.randomUUID());
            entity.setQuestion(question);
            entity.setOptionKey(requireText(option.key(), "option.key"));
            entity.setOptionText(requireText(option.text(), "option.text"));
            entity.setCorrect(option.correct());
            entity.setSortOrder(index);
            question.getOptions().add(entity);
        }
    }

    private void replaceAnswers(QuestionEntity question, List<QuestionAnswer> answers) {
        for (int index = 0; index < answers.size(); index++) {
            QuestionAnswer answer = answers.get(index);
            QuestionAnswerEntity entity = new QuestionAnswerEntity();
            entity.setId(UUID.randomUUID());
            entity.setQuestion(question);
            entity.setAnswerValue(requireText(answer.answerValue(), "answer.answerValue"));
            entity.setAnswerType(requireText(answer.answerType(), "answer.answerType").toUpperCase());
            entity.setToleranceValue(answer.toleranceValue());
            entity.setCaseSensitive(answer.caseSensitive());
            entity.setSortOrder(index);
            question.getAnswers().add(entity);
        }
    }

    private void replaceAssignments(QuestionEntity question, List<QuestionTaxonomyAssignment> assignments) {
        Set<UUID> assignedNodeIds = new HashSet<>();
        for (QuestionTaxonomyAssignment assignment : assignments) {
            UUID taxonomyNodeId = requireUuid(assignment.taxonomyNodeId(), "taxonomyAssignments.taxonomyNodeId");
            if (!assignedNodeIds.add(taxonomyNodeId)) {
                throw new IllegalArgumentException("Duplicate taxonomy assignment: " + taxonomyNodeId);
            }
            TaxonomyNodeEntity node = activeLeafNode(taxonomyNodeId);
            QuestionTaxonomyNodeEntity entity = new QuestionTaxonomyNodeEntity();
            entity.setId(new QuestionTaxonomyNodeId(question.getId(), taxonomyNodeId));
            entity.setQuestion(question);
            entity.setTaxonomyNode(node);
            entity.setPrimary(assignment.primary());
            question.getTaxonomyAssignments().add(entity);
        }
    }

    private void replaceTags(QuestionEntity question, List<String> requested) {
        if (requested == null) return;
        Set<String> tags = new LinkedHashSet<>();
        for (String value : requested) tags.add(requireText(value, "tag").toUpperCase());
        for (String tag : tags) {
            QuestionTagEntity entity = new QuestionTagEntity();
            entity.setId(new QuestionTagId(question.getId(), tag));
            entity.setQuestion(question);
            question.getTags().add(entity);
        }
    }

    private void addWorkflowEvent(QuestionEntity question, String previousStatus, String nextStatus, String actor) {
        QuestionWorkflowEventEntity event = new QuestionWorkflowEventEntity();
        event.setId(UUID.randomUUID());
        event.setQuestion(question);
        event.setFromStatus(previousStatus);
        event.setToStatus(nextStatus);
        event.setActor(actor);
        event.setNotes(previousStatus == null ? "Question created" : "Question updated");
        question.getWorkflowEvents().add(event);
    }

    private void setQuestionUniquenessScope(QuestionEntity question) {
        QuestionTaxonomyNodeEntity primary = question.getTaxonomyAssignments().stream()
                .filter(QuestionTaxonomyNodeEntity::isPrimary)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Exactly one primary taxonomy assignment is required"));
        TaxonomyNodeEntity child = primary.getTaxonomyNode();
        question.setChildTaxonomyNode(child);
        question.setRootTaxonomyNode(rootTaxonomyNode(child));
        question.setNormalizedQuestionText(normalizeQuestionText(question.getQuestionText()));
    }

    private TaxonomyNodeEntity rootTaxonomyNode(TaxonomyNodeEntity node) {
        TaxonomyNodeEntity current = node;
        java.util.HashSet<UUID> visited = new java.util.HashSet<>();
        while (current.getParentNode() != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Taxonomy contains a cycle");
            }
            current = current.getParentNode();
        }
        return current;
    }

    private String normalizeQuestionText(String value) {
        return requireText(value, "question.questionText")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private void validateAnswers(QuestionType type, WorkflowStatus workflowStatus, List<QuestionAnswer> answers) {
        if (workflowStatus == WorkflowStatus.DRAFT || workflowStatus == WorkflowStatus.MISSING_ANSWER) {
            return;
        }
        if ((type == QuestionType.FILL_BLANK || type == QuestionType.NUMERICAL) && answers.isEmpty()) {
            throw new IllegalArgumentException("Text and numerical questions require at least one accepted answer");
        }
    }

    private TaxonomyNodeEntity activeLeafNode(UUID id) {
        TaxonomyNodeEntity node = taxonomyNodes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question taxonomy node is missing"));
        if (!"ACTIVE".equals(node.getStatus())) {
            throw new IllegalArgumentException("Question taxonomy node is inactive");
        }
        if (taxonomyNodes.existsByParentNode_Id(id)) {
            throw new IllegalArgumentException("Questions must be assigned to taxonomy leaf nodes");
        }
        return node;
    }

    private Set<UUID> eligibleTaxonomyNodes(QuestionSearchCriteria criteria) {
        Set<UUID> eligible = null;
        if (criteria.taxonomyNodeId() != null) {
            eligible = criteria.includeDescendants()
                    ? descendantsIncluding(criteria.taxonomyNodeId())
                    : Set.of(criteria.taxonomyNodeId());
        }
        for (UUID pedigreeNodeId : criteria.pedigreeNodeIds()) {
            Set<UUID> descendants = descendantsIncluding(pedigreeNodeId);
            if (eligible == null) eligible = descendants;
            else eligible.retainAll(descendants);
        }
        return eligible;
    }

    private Set<UUID> descendantsIncluding(UUID rootId) {
        taxonomyNodes.findById(rootId)
                .orElseThrow(() -> new IllegalArgumentException("Taxonomy node was not found: " + rootId));
        Set<UUID> result = new LinkedHashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        pending.add(rootId);
        while (!pending.isEmpty()) {
            UUID id = pending.removeFirst();
            if (!result.add(id)) continue;
            taxonomyNodes.findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(id)
                    .forEach(child -> pending.addLast(child.getId()));
        }
        return result;
    }

    private CursorPosition parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid question cursor");
            }
            return new CursorPosition(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid question cursor");
        }
    }

    private String encodeCursor(Instant createdAt, UUID id) {
        String value = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private QuestionEntity findQuestion(UUID id) {
        return questions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question was not found: " + id));
    }

    private QuestionAdminRecord toAdminRecord(QuestionEntity question) {
        QuestionTaxonomyNodeEntity primary = question.getTaxonomyAssignments().stream()
                .filter(QuestionTaxonomyNodeEntity::isPrimary)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Question has no primary taxonomy assignment"));
        TaxonomyNodeEntity primaryNode = primary.getTaxonomyNode();
        return new QuestionAdminRecord(
                question.getId(),
                primaryNode.getId(),
                primaryNode.getDisplayName(),
                primaryNode.getStatus(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getWorkflowStatus(),
                question.getQuestionText(),
                question.getExplanation(),
                question.getSourceReference(),
                question.getLicenseCategory(),
                question.getOptions().stream()
                        .map(option -> new QuestionOption(option.getOptionKey(), option.getOptionText(), option.isCorrect()))
                        .toList(),
                question.getTaxonomyAssignments().stream()
                        .map(assignment -> new QuestionTaxonomyAssignment(
                                assignment.getTaxonomyNode().getId(), assignment.isPrimary()))
                        .toList(),
                question.getAnswers().stream()
                        .map(answer -> new QuestionAnswer(answer.getAnswerValue(), answer.getAnswerType(),
                                answer.getToleranceValue(), answer.getCaseSensitive()))
                        .toList(),
                question.getTags().stream().map(tag -> tag.getId().getTagCode()).sorted().toList());
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CursorPosition(Instant createdAt, UUID id) {
    }
}
