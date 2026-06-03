package com.clearleaf.api;

import com.clearleaf.api.entity.LookupEntity;
import com.clearleaf.api.entity.QuestionAnswerEntity;
import com.clearleaf.api.entity.QuestionEntity;
import com.clearleaf.api.entity.QuestionOptionEntity;
import com.clearleaf.api.entity.TaxonomyNodeEntity;
import com.clearleaf.api.repository.LookupRepository;
import com.clearleaf.api.repository.QuestionRepository;
import com.clearleaf.api.repository.TaxonomyNodeRepository;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BulkImportService {
    private final MinioStorageService storage;
    private final LookupRepository lookups;
    private final TaxonomyNodeRepository taxonomyNodes;
    private final QuestionRepository questions;
    private final QuestionAuthoringService authoring;
    private final JdbcTemplate jdbcTemplate;

    public BulkImportService(
            MinioStorageService storage,
            LookupRepository lookups,
            TaxonomyNodeRepository taxonomyNodes,
            QuestionRepository questions,
            QuestionAuthoringService authoring,
            JdbcTemplate jdbcTemplate) {
        this.storage = storage;
        this.lookups = lookups;
        this.taxonomyNodes = taxonomyNodes;
        this.questions = questions;
        this.authoring = authoring;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<BulkImportStepMetadata> metadata() {
        return Arrays.stream(BulkImportStep.values())
                .map(step -> new BulkImportStepMetadata(step.sequence(), step.name(), step.label(), step.columns()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BulkImportPreviewResponse preview(BulkImportStep step, String objectKey) {
        List<BulkImportRowResult> rows = parseRows(step, objectKey);
        long valid = rows.stream().filter(BulkImportRowResult::valid).count();
        return new BulkImportPreviewResponse(objectKey, step.name(), rows.size(), (int) valid, rows.size() - (int) valid, rows);
    }

    @Transactional
    public BulkImportSummary importStep(BulkImportStep step, String objectKey, String actor) {
        List<BulkImportRowResult> previewRows = parseRows(step, objectKey);
        List<BulkImportRowResult> results = new ArrayList<>();
        int imported = 0;
        for (BulkImportRowResult row : previewRows) {
            if (!row.valid()) {
                results.add(row);
                continue;
            }
            List<String> errors = new ArrayList<>();
            try {
                switch (step) {
                    case TAXONOMIES -> importTaxonomy(row.values());
                    case QUESTIONS -> importQuestion(row.values(), actor);
                    case QUESTION_OPTIONS -> importQuestionOption(row.values());
                    case CORRECT_ANSWERS -> importCorrectAnswer(row.values());
                }
                imported++;
            } catch (DuplicateQuestionImportException ex) {
                results.add(new BulkImportRowResult(row.lineNumber(), row.values(), List.of(), List.of(ex.getMessage()), false));
                continue;
            } catch (RuntimeException ex) {
                errors.add(rootMessage(ex));
            }
            results.add(new BulkImportRowResult(row.lineNumber(), row.values(), errors, errors.isEmpty()));
        }
        int failed = previewRows.size() - imported;
        recordStepRun(step, objectKey, previewRows.size(), imported, failed, results);
        return new BulkImportSummary(objectKey, step.name(), previewRows.size(), imported, failed, results);
    }

    private void recordStepRun(BulkImportStep step, String objectKey, int totalRows, int imported, int failed, List<BulkImportRowResult> results) {
        int validRows = (int) results.stream().filter(BulkImportRowResult::valid).count();
        String status = failed == 0 ? "IMPORTED" : imported == 0 ? "FAILED" : "PARTIAL";
        String errors = results.stream()
                .filter(row -> !row.errors().isEmpty())
                .map(row -> "line " + row.lineNumber() + ": " + String.join("; ", row.errors()))
                .toList()
                .toString();
        jdbcTemplate.update("""
                INSERT INTO bulk_import_step_run
                    (id, step_code, object_key, status, total_rows, valid_rows, imported_rows, failed_rows, errors_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), step.name(), objectKey, status, totalRows, validRows, imported, failed, errors);
    }

    private List<BulkImportRowResult> parseRows(BulkImportStep step, String objectKey) {
        if (!storage.exists(objectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded import file was not found");
        }
        String csv = storage.readText(objectKey);
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(new StringReader(csv))) {
            List<BulkImportRowResult> rows = new ArrayList<>();
            int lineNumber = 1;
            for (CSVRecord record : parser) {
                Map<String, String> values = values(record);
                List<String> errors = validate(step, values);
                rows.add(new BulkImportRowResult(lineNumber++, values, errors, errors.isEmpty()));
            }
            if (step == BulkImportStep.QUESTIONS) {
                return markDuplicateQuestionRows(rows);
            }
            return rows;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse import CSV", ex);
        }
    }

    private List<BulkImportRowResult> markDuplicateQuestionRows(List<BulkImportRowResult> rows) {
        Map<String, Integer> firstLineByDuplicateKey = new LinkedHashMap<>();
        List<BulkImportRowResult> marked = new ArrayList<>();
        for (BulkImportRowResult row : rows) {
            if (!row.errors().isEmpty()) {
                marked.add(row);
                continue;
            }
            String duplicateKey = duplicateQuestionKey(row.values());
            Integer firstLine = firstLineByDuplicateKey.putIfAbsent(duplicateKey, row.lineNumber());
            if (firstLine == null) {
                marked.add(row);
            } else {
                marked.add(new BulkImportRowResult(
                        row.lineNumber(),
                        row.values(),
                        row.errors(),
                        List.of("Duplicate question in CSV; first occurrence is line " + firstLine + ". Row skipped."),
                        false));
            }
        }
        return marked;
    }

    private Map<String, String> values(CSVRecord record) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String header : record.getParser().getHeaderMap().keySet()) {
            values.put(cleanHeader(header), optionalText(record, header));
        }
        return values;
    }

    private String cleanHeader(String header) {
        return header == null ? null : header.replace("\uFEFF", "").trim();
    }

    private List<String> validate(BulkImportStep step, Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        for (BulkImportColumn column : step.columns()) {
            if (column.required() && blank(values.get(column.name()))) {
                errors.add(column.name() + " is required");
            }
        }
        switch (step) {
            case TAXONOMIES -> validateTaxonomy(values, errors);
            case QUESTIONS -> validateQuestion(values, errors);
            case QUESTION_OPTIONS -> validateQuestionOption(values, errors);
            case CORRECT_ANSWERS -> validateCorrectAnswer(values, errors);
        }
        return errors;
    }

    private void validateTaxonomy(Map<String, String> values, List<String> errors) {
        String levelKey = values.get("levelKey");
        if (!blank(levelKey)) {
            String normalized = normalizeLevelKey(levelKey);
            if (normalized.isBlank()) {
                errors.add("levelKey must include at least one letter or number");
            }
            if (normalized.length() > 64) {
                errors.add("levelKey must be 64 characters or fewer");
            }
        }
        if (!blank(values.get("nodeKey"))) {
            try {
                normalizeNodeKey(values.get("nodeKey"));
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        parseInteger(values.get("sortOrder"), "sortOrder", errors);
    }

    private void validateQuestion(Map<String, String> values, List<String> errors) {
        parseEnum(values.get("questionType"), QuestionType.class, "questionType", errors);
        parseEnum(values.get("difficulty"), Difficulty.class, "difficulty", errors);
        if (!blank(values.get("workflowStatus"))) {
            parseEnum(values.get("workflowStatus"), WorkflowStatus.class, "workflowStatus", errors);
        }
    }

    private void validateQuestionOption(Map<String, String> values, List<String> errors) {
        parseInteger(values.get("sortOrder"), "sortOrder", errors);
    }

    private void validateCorrectAnswer(Map<String, String> values, List<String> errors) {
        parseInteger(values.get("sortOrder"), "sortOrder", errors);
        if (!blank(values.get("toleranceValue"))) {
            try {
                new BigDecimal(values.get("toleranceValue").trim());
            } catch (NumberFormatException ex) {
                errors.add("toleranceValue must be numeric");
            }
        }
    }

    private void importTaxonomy(Map<String, String> values) {
        String externalKey = requireText(value(values, "PublicKey", "externalKey"), "PublicKey");
        String levelKey = normalizeLevelKey(requireText(values.get("levelKey"), "levelKey"));
        LookupEntity level = lookupOrCreateTaxonomyLevel(levelKey);
        TaxonomyNodeEntity parent = null;
        String parentPublicKey = value(values, "ParentPublicKey", "parentExternalKey");
        if (!blank(parentPublicKey)) {
            parent = taxonomyNodes.findByExternalKey(parentPublicKey.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ParentPublicKey: " + parentPublicKey));
        }
        TaxonomyNodeEntity node = taxonomyNodes.findByExternalKey(externalKey).orElseGet(() -> {
            TaxonomyNodeEntity created = new TaxonomyNodeEntity();
            created.setId(UUID.randomUUID());
            created.setExternalKey(externalKey);
            return created;
        });
        node.setLevelType(level);
        node.setParentNode(parent);
        TaxonomyNodeEntity root = parent == null ? node : rootTaxonomyNode(parent);
        String nodeKey = normalizeNodeKey(values.get("nodeKey"));
        node.setRootTaxonomyNode(root);
        node.setNodeKey(nodeKey);
        validateRootNodeKeyAvailable(root, nodeKey, node.getId());
        node.setDisplayName(requireText(values.get("displayName"), "displayName"));
        node.setStatus(blank(values.get("status")) ? "ACTIVE" : values.get("status").trim().toUpperCase(Locale.ROOT));
        node.setSortOrder(parseIntegerOrDefault(values.get("sortOrder"), 0));
        taxonomyNodes.save(node);
    }

    private LookupEntity lookupOrCreateTaxonomyLevel(String levelKey) {
        return lookups.findByLookupTypeAndLookupCodeIgnoreCase(LookupType.TAXONOMY_TYPE, levelKey)
                .orElseGet(() -> lookups.save(new LookupEntity(
                        UUID.randomUUID(),
                        LookupType.TAXONOMY_TYPE,
                        levelKey,
                        displayName(levelKey),
                        "Imported taxonomy level",
                        1000,
                        true)));
    }

    private void importQuestion(Map<String, String> values, String actor) {
        String externalKey = requireText(value(values, "PublicKey", "externalKey"), "PublicKey");
        TaxonomyNodeEntity taxonomy = taxonomyForQuestion(values);
        QuestionType type = parseRequiredEnum(values.get("questionType"), QuestionType.class, "questionType");
        Difficulty difficulty = parseRequiredEnum(values.get("difficulty"), Difficulty.class, "difficulty");
        WorkflowStatus status = blank(values.get("workflowStatus"))
                ? WorkflowStatus.DRAFT
                : parseRequiredEnum(values.get("workflowStatus"), WorkflowStatus.class, "workflowStatus");
        QuestionEntity existing = questions.findByExternalKey(externalKey).orElse(null);
        String normalizedQuestionText = normalizeQuestionText(values.get("questionText"));
        QuestionEntity duplicate = questions.findByRootTaxonomyNode_IdAndChildTaxonomyNode_IdAndNormalizedQuestionText(
                        rootTaxonomyNode(taxonomy).getId(),
                        taxonomy.getId(),
                        normalizedQuestionText)
                .orElse(null);
        if (duplicate != null && (existing == null || !duplicate.getId().equals(existing.getId()))) {
            throw new DuplicateQuestionImportException("Duplicate question already exists in "
                    + taxonomy.getDisplayName() + "; existing PublicKey is " + duplicate.getExternalKey() + ". Row skipped.");
        }
        String rowActor = value(values, "actor", "Actor", "createdBy", "CreatedBy", "importedBy", "ImportedBy", "uploadedBy", "UploadedBy");
        if (blank(rowActor)) {
            rowActor = actor;
        }
        CreateQuestionRequest request = new CreateQuestionRequest(
                taxonomy.getId(),
                requireText(rowActor, "actor"),
                new QuestionDraft(
                        type,
                        difficulty,
                        status,
                        requireText(values.get("questionText"), "questionText"),
                        nullIfBlank(values.get("explanation")),
                        nullIfBlank(values.get("sourceReference")),
                        nullIfBlank(values.get("licenseCategory")),
                        existing == null ? List.of() : existing.getOptions().stream()
                                .map(option -> new QuestionOption(option.getOptionKey(), option.getOptionText(), option.isCorrect()))
                                .toList()),
                null,
                existing == null ? null : existing.getAnswers().stream()
                        .map(answer -> new QuestionAnswer(answer.getAnswerValue(), answer.getAnswerType(), answer.getToleranceValue(), answer.getCaseSensitive()))
                        .toList(),
                splitTags(values.get("tags")));
        UUID questionId = existing == null
                ? authoring.create(request).id()
                : authoring.update(existing.getId(), request).id();
        QuestionEntity question = questions.findById(questionId)
                .orElseThrow(() -> new IllegalStateException("Imported question was not found"));
        question.setExternalKey(externalKey);
        questions.save(question);
    }

    private void importQuestionOption(Map<String, String> values) {
        QuestionEntity question = questionByExternalKey(value(values, "QuestionPublicKey", "questionExternalKey"));
        QuestionOptionEntity option = question.getOptions().stream()
                .filter(current -> current.getOptionKey().equalsIgnoreCase(requireText(values.get("optionKey"), "optionKey")))
                .findFirst()
                .orElseGet(() -> {
                    QuestionOptionEntity created = new QuestionOptionEntity();
                    created.setId(UUID.randomUUID());
                    created.setQuestion(question);
                    question.getOptions().add(created);
                    return created;
                });
        option.setOptionKey(requireText(values.get("optionKey"), "optionKey").toUpperCase(Locale.ROOT));
        option.setOptionText(requireText(values.get("optionText"), "optionText"));
        option.setSortOrder(parseIntegerOrDefault(values.get("sortOrder"), question.getOptions().indexOf(option)));
        option.setCorrect(false);
        questions.save(question);
    }

    private void importCorrectAnswer(Map<String, String> values) {
        QuestionEntity question = questionByExternalKey(value(values, "QuestionPublicKey", "questionExternalKey"));
        QuestionType type = parseRequiredEnum(question.getQuestionType(), QuestionType.class, "questionType");
        if (type == QuestionType.SINGLE_SELECT || type == QuestionType.MULTIPLE_SELECT || type == QuestionType.TRUE_FALSE) {
            String optionKey = requireText(values.get("optionKey"), "optionKey").toUpperCase(Locale.ROOT);
            boolean matched = false;
            for (QuestionOptionEntity option : question.getOptions()) {
                boolean correct = option.getOptionKey().equalsIgnoreCase(optionKey);
                if (correct) matched = true;
                if (type != QuestionType.MULTIPLE_SELECT || correct) {
                    option.setCorrect(correct);
                }
            }
            if (!matched) {
                throw new IllegalArgumentException("Unknown optionKey for question: " + optionKey);
            }
            questions.save(question);
            return;
        }
        QuestionAnswerEntity answer = new QuestionAnswerEntity();
        answer.setId(UUID.randomUUID());
        answer.setQuestion(question);
        answer.setAnswerValue(requireText(values.get("answerValue"), "answerValue"));
        answer.setAnswerType(blank(values.get("answerType")) ? type.name() : values.get("answerType").trim().toUpperCase(Locale.ROOT));
        answer.setToleranceValue(blank(values.get("toleranceValue")) ? null : new BigDecimal(values.get("toleranceValue").trim()));
        answer.setCaseSensitive(blank(values.get("caseSensitive")) ? null : Boolean.parseBoolean(values.get("caseSensitive").trim()));
        answer.setSortOrder(parseIntegerOrDefault(values.get("sortOrder"), question.getAnswers().size()));
        question.getAnswers().add(answer);
        questions.save(question);
    }

    private QuestionEntity questionByExternalKey(String externalKey) {
        return questions.findByExternalKey(requireText(externalKey, "QuestionPublicKey"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown QuestionPublicKey: " + externalKey));
    }

    private TaxonomyNodeEntity taxonomyForQuestion(Map<String, String> values) {
        String legacyKey = value(values, "taxonomyExternalKey");
        if (!blank(legacyKey)) {
            return taxonomyNodes.findByExternalKey(legacyKey.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown taxonomyExternalKey: " + legacyKey));
        }
        String rootTaxonomy = requireText(values.get("RootTaxonomy"), "RootTaxonomy");
        String childTaxonomy = requireText(values.get("ChildTaxonomy"), "ChildTaxonomy");
        List<TaxonomyNodeEntity> all = taxonomyNodes.findAll();
        List<TaxonomyNodeEntity> roots = all.stream()
                .filter(node -> node.getParentNode() == null)
                .filter(node -> matchesTaxonomyKey(node, rootTaxonomy))
                .toList();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Unknown RootTaxonomy: " + rootTaxonomy);
        }
        List<TaxonomyNodeEntity> matches = all.stream()
                .filter(node -> matchesTaxonomyKey(node, childTaxonomy))
                .filter(node -> roots.stream().anyMatch(root -> belongsToRoot(node, root.getId())))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown ChildTaxonomy under " + rootTaxonomy + ": " + childTaxonomy);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("ChildTaxonomy is ambiguous under " + rootTaxonomy + ": " + childTaxonomy);
        }
        return matches.getFirst();
    }

    private boolean belongsToRoot(TaxonomyNodeEntity node, UUID rootId) {
        TaxonomyNodeEntity current = node;
        while (current != null) {
            if (rootId.equals(current.getId())) return true;
            current = current.getParentNode();
        }
        return false;
    }

    private boolean matchesTaxonomyKey(TaxonomyNodeEntity node, String value) {
        String normalized = value.trim();
        return equalsIgnoreCase(node.getExternalKey(), normalized)
                || equalsIgnoreCase(node.getNodeKey(), normalized)
                || equalsIgnoreCase(node.getDisplayName(), normalized);
    }

    private String duplicateQuestionKey(Map<String, String> values) {
        return normalizeKeyPart(value(values, "RootTaxonomy"))
                + "|"
                + normalizeKeyPart(value(values, "ChildTaxonomy"))
                + "|"
                + normalizeQuestionText(values.get("questionText"));
    }

    private TaxonomyNodeEntity rootTaxonomyNode(TaxonomyNodeEntity node) {
        if (node.getRootTaxonomyNode() != null) {
            return node.getRootTaxonomyNode();
        }
        TaxonomyNodeEntity current = node;
        Set<UUID> visited = new java.util.HashSet<>();
        while (current.getParentNode() != null) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("Taxonomy contains a cycle");
            }
            current = current.getParentNode();
        }
        return current;
    }

    private void validateRootNodeKeyAvailable(TaxonomyNodeEntity root, String nodeKey, UUID currentId) {
        if (taxonomyNodes.existsByRootTaxonomyNode_IdAndNodeKeyAndIdNot(root.getId(), nodeKey, currentId)) {
            throw new IllegalArgumentException("nodeKey already exists under root taxonomy: " + root.getNodeKey());
        }
    }

    private String normalizeQuestionText(String value) {
        return requireText(value, "questionText")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyPart(String value) {
        return requireText(value, "taxonomy key").trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNodeKey(String value) {
        String normalized = requireText(value, "nodeKey").trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]+(?:_[A-Z0-9]+)*")) {
            throw new IllegalArgumentException("nodeKey must contain only uppercase letters, numbers, and single underscores between words");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("nodeKey must be 128 characters or fewer");
        }
        return normalized;
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field, List<String> errors) {
        if (blank(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(field + " is invalid");
            return null;
        }
    }

    private <E extends Enum<E>> E parseRequiredEnum(String value, Class<E> type, String field) {
        if (blank(value)) throw new IllegalArgumentException(field + " is required");
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }

    private Integer parseInteger(String value, String field, List<String> errors) {
        if (blank(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            errors.add(field + " must be an integer");
            return null;
        }
    }

    private int parseIntegerOrDefault(String value, int defaultValue) {
        if (blank(value)) return defaultValue;
        return Integer.parseInt(value.trim());
    }

    private List<String> splitTags(String value) {
        if (blank(value)) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private String optionalText(CSVRecord record, String field) {
        return record.isMapped(field) ? record.get(field) : null;
    }

    private String value(Map<String, String> values, String field, String... aliases) {
        String direct = values.get(field);
        if (!blank(direct)) return direct;
        for (String alias : aliases) {
            String aliased = values.get(alias);
            if (!blank(aliased)) return aliased;
        }
        return direct;
    }

    private String requireText(String value, String field) {
        if (blank(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private String nullIfBlank(String value) {
        return blank(value) ? null : value.trim();
    }

    private String normalizeLevelKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private String displayName(String value) {
        String normalized = normalizeLevelKey(value).toLowerCase(Locale.ROOT).replace('_', ' ');
        if (normalized.isBlank()) return value;
        StringBuilder display = new StringBuilder();
        for (String word : normalized.split(" ")) {
            if (word.isBlank()) continue;
            if (!display.isEmpty()) display.append(' ');
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String rootMessage(RuntimeException ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? ex.getClass().getSimpleName() : current.getMessage();
    }

    private static class DuplicateQuestionImportException extends RuntimeException {
        DuplicateQuestionImportException(String message) {
            super(message);
        }
    }
}
