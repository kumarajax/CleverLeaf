package com.clearleaf.api;

import com.clearleaf.api.entity.QuestionEntity;
import com.clearleaf.api.entity.TaxonomyNodeEntity;
import com.clearleaf.api.repository.QuestionRepository;
import com.clearleaf.api.repository.TaxonomyNodeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AiQuestionGenerationService {
    private static final int TARGET_CHUNK_WORDS = 1800;
    private static final int CHUNK_OVERLAP_WORDS = 180;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MinioStorageService storage;
    private final TaxonomyNodeRepository taxonomyNodes;
    private final QuestionRepository questions;
    private final QuestionAuthoringService authoring;
    private final QuestionGenerationClient generationClient;
    private final AiProviderConnectionService connections;

    public AiQuestionGenerationService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            MinioStorageService storage,
            TaxonomyNodeRepository taxonomyNodes,
            QuestionRepository questions,
            QuestionAuthoringService authoring,
            QuestionGenerationClient generationClient,
            AiProviderConnectionService connections) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.taxonomyNodes = taxonomyNodes;
        this.questions = questions;
        this.authoring = authoring;
        this.generationClient = generationClient;
        this.connections = connections;
    }

    @Transactional
    public AiGenerationJobResponse createJob(UUID tenantId, AiGenerationJobRequest request, String actor) {
        TaxonomyNodeEntity node = activeLeaf(tenantId, requireUuid(request.taxonomyNodeId(), "taxonomyNodeId"));
        TaxonomyNodeEntity root = rootTaxonomyNode(node);
        UUID id = UUID.randomUUID();
        String sourceType = normalizeSourceType(request.sourceType());
        if ("PDF".equals(sourceType) && isBlank(request.sourceObjectKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceObjectKey is required for PDF jobs");
        }
        if ("TEXT".equals(sourceType) && isBlank(request.sourceText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceText is required for text jobs");
        }
        String taxonomyKey = taxonomyImportKey(root);
        String childNodeKey = node.getNodeKey();
        jdbcTemplate.update("""
                INSERT INTO ai_generation_job
                    (id, tenant_id, taxonomy_node_id, taxonomy_key, child_node_key, taxonomy_path, source_type,
                     source_object_key, source_filename, topic, instructions, question_count, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, tenantId, node.getId(), taxonomyKey, childNodeKey, taxonomyPath(node), sourceType,
                trimToNull(request.sourceObjectKey()), trimToNull(request.sourceFilename()), requireText(request.topic(), "topic"),
                trimToNull(request.instructions()), Math.clamp(request.questionCount(), 1, 100), "CREATED", requireText(actor, "actor"));
        if ("TEXT".equals(sourceType)) {
            createChunks(tenantId, id, normalizeText(request.sourceText()), null);
        }
        return getJob(tenantId, id);
    }

    @Transactional
    public AiGenerationJobResponse generate(UUID tenantId, UUID jobId, AiGenerationJobRequest settings) {
        AiGenerationJobRow job = requireJob(tenantId, jobId);
        try {
            if ("PDF".equals(job.sourceType()) && chunkCount(tenantId, job.id()) == 0) {
                createChunks(tenantId, job.id(), extractPdfText(job.sourceObjectKey()), "Page");
            }
            List<AiChunkRow> chunks = chunks(tenantId, job.id());
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No usable source text was found");
            }
            AiProviderCredentials credentials = connections.resolveCredentials(tenantId);
            int remaining = job.questionCount();
            for (AiChunkRow chunk : chunks) {
                if (remaining <= 0) break;
                int perChunk = Math.min(3, remaining);
                GeneratedQuestionBatch batch = generationClient.generate(new QuestionGenerationRequest(
                        job.taxonomyKey(),
                        job.childNodeKey(),
                        job.taxonomyPath(),
                        job.topic(),
                        job.instructions(),
                        perChunk,
                        allowedTypes(settings),
                        difficultyMix(settings),
                        chunk.sourceReference(),
                        chunk.chunkText()), credentials);
                int accepted = storeBatch(job, chunk, batch);
                remaining -= accepted;
            }
            updateJobStatus(job.id(), "GENERATED", null);
        } catch (RuntimeException ex) {
            updateJobStatus(job.id(), "FAILED", rootMessage(ex));
            throw ex;
        }
        return getJob(tenantId, job.id());
    }

    @Transactional(readOnly = true)
    public List<AiGenerationJobResponse> listJobs(UUID tenantId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_generation_job
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT 50
                """, (rs, rowNum) -> toJobResponse(toJobRow(rs), false), tenantId);
    }

    @Transactional(readOnly = true)
    public AiGenerationJobResponse getJob(UUID tenantId, UUID jobId) {
        return toJobResponse(requireJob(tenantId, jobId), true);
    }

    @Transactional
    public AiGeneratedQuestionResponse updateGeneratedQuestion(UUID tenantId, UUID id, UpdateAiGeneratedQuestionRequest request) {
        AiGeneratedQuestionResponse current = requireGenerated(tenantId, id);
        List<QuestionOption> options = request.options() == null ? List.of() : request.options();
        List<String> correctKeys = request.correctOptionKeys() == null ? List.of() : request.correctOptionKeys();
        List<String> errors = validateGenerated(
                current.taxonomyKey(),
                current.childNodeKey(),
                current.taxonomyKey(),
                current.childNodeKey(),
                request.questionType(),
                request.difficulty(),
                request.questionText(),
                options,
                correctKeys,
                request.explanation(),
                request.sourceReference());
        jdbcTemplate.update("""
                UPDATE ai_generated_question
                SET status = ?, review_status = ?, question_type = ?, difficulty = ?, question_text = ?,
                    explanation = ?, source_reference = ?, options_json = ?::jsonb,
                    correct_option_keys_json = ?::jsonb, validation_errors_json = ?::jsonb, updated_at = now()
                WHERE id = ? AND tenant_id = ?
                """,
                errors.isEmpty() ? "VALID" : "INVALID",
                "PENDING",
                requireText(request.questionType(), "questionType").toUpperCase(Locale.ROOT),
                requireText(request.difficulty(), "difficulty").toUpperCase(Locale.ROOT),
                requireText(request.questionText(), "questionText"),
                requireText(request.explanation(), "explanation"),
                requireText(request.sourceReference(), "sourceReference"),
                writeJson(options),
                writeJson(correctKeys),
                writeJson(errors),
                id,
                tenantId);
        return requireGenerated(tenantId, id);
    }

    @Transactional
    public AiGeneratedQuestionResponse approve(UUID tenantId, UUID id, String actor) {
        AiGeneratedQuestionResponse generated = requireGenerated(tenantId, id);
        if (!"VALID".equals(generated.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only valid generated questions can be approved");
        }
        if ("APPROVED".equals(generated.reviewStatus())) {
            return generated;
        }
        AiGenerationJobRow job = requireJob(tenantId, generated.jobId());
        TaxonomyNodeEntity node = activeLeaf(tenantId, job.taxonomyNodeId());
        List<QuestionOption> options = generated.options().stream()
                .map(option -> new QuestionOption(option.key(), option.text(), option.mediaObjectKey(), option.mediaContentType(),
                        generated.correctOptionKeys().stream().anyMatch(key -> key.equalsIgnoreCase(option.key()))))
                .toList();
        CreatedQuestionResponse created = authoring.create(tenantId, new CreateQuestionRequest(
                node.getId(),
                actor,
                new QuestionDraft(
                        QuestionType.valueOf(generated.questionType()),
                        Difficulty.valueOf(generated.difficulty()),
                        WorkflowStatus.DRAFT,
                        generated.questionText(),
                        null,
                        null,
                        generated.explanation(),
                        generated.sourceReference(),
                        "AI_GENERATED",
                        options),
                List.of(new QuestionTaxonomyAssignment(node.getId(), true)),
                List.of(),
                List.of(job.childNodeKey(), "AI_GENERATED"),
                true));
        jdbcTemplate.update("""
                UPDATE ai_generated_question
                SET review_status = 'APPROVED', created_question_id = ?, updated_at = now()
                WHERE id = ? AND tenant_id = ?
                """, created.id(), id, tenantId);
        jdbcTemplate.update("""
                UPDATE ai_generation_job
                SET updated_at = now()
                WHERE id = ? AND tenant_id = ?
                """, job.id(), tenantId);
        return requireGenerated(tenantId, id);
    }

    @Transactional
    public AiGeneratedQuestionResponse reject(UUID tenantId, UUID id, AiRejectRequest request) {
        requireGenerated(tenantId, id);
        List<String> errors = request == null || isBlank(request.reason()) ? List.of("Rejected by reviewer") : List.of(request.reason().trim());
        jdbcTemplate.update("""
                UPDATE ai_generated_question
                SET review_status = 'REJECTED', validation_errors_json = ?::jsonb, updated_at = now()
                WHERE id = ? AND tenant_id = ?
                """, writeJson(errors), id, tenantId);
        return requireGenerated(tenantId, id);
    }

    @Transactional
    public AiGenerationJobResponse bulkApprove(UUID tenantId, UUID jobId, String actor) {
        AiGenerationJobResponse job = getJob(tenantId, jobId);
        for (AiGeneratedQuestionResponse question : job.questions()) {
            if ("VALID".equals(question.status()) && "PENDING".equals(question.reviewStatus())) {
                approve(tenantId, question.id(), actor);
            }
        }
        return getJob(tenantId, jobId);
    }

    private int storeBatch(AiGenerationJobRow job, AiChunkRow chunk, GeneratedQuestionBatch batch) {
        if (batch == null || batch.questions() == null || batch.questions().isEmpty()) return 0;
        int count = 0;
        for (GeneratedQuestionDraft question : batch.questions()) {
            List<QuestionOption> options = normalizeOptions(question.options());
            List<String> correctKeys = normalizeKeys(question.correctOptionKeys());
            List<String> errors = validateGenerated(job.taxonomyKey(), job.childNodeKey(),
                    question.taxonomyKey(), question.childNodeKey(), question.questionType(), question.difficulty(),
                    question.questionText(), options, correctKeys, question.explanation(), question.sourceReference());
            if (errors.isEmpty() && duplicateQuestion(job, question.questionText())) {
                errors.add("Duplicate question already exists under the selected taxonomy node");
            }
            jdbcTemplate.update("""
                    INSERT INTO ai_generated_question
                        (id, tenant_id, job_id, chunk_id, taxonomy_key, child_node_key, status, review_status, question_type,
                         difficulty, question_text, explanation, source_reference, options_json,
                         correct_option_keys_json, validation_errors_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                    """,
                    UUID.randomUUID(),
                    job.tenantId(),
                    job.id(),
                    chunk.id(),
                    nullToExpected(question.taxonomyKey(), job.taxonomyKey()),
                    nullToExpected(question.childNodeKey(), job.childNodeKey()),
                    errors.isEmpty() ? "VALID" : "INVALID",
                    "PENDING",
                    requireText(question.questionType(), "questionType").toUpperCase(Locale.ROOT),
                    requireText(question.difficulty(), "difficulty").toUpperCase(Locale.ROOT),
                    requireText(question.questionText(), "questionText"),
                    requireText(question.explanation(), "explanation"),
                    isBlank(question.sourceReference()) ? chunk.sourceReference() : question.sourceReference().trim(),
                    writeJson(options),
                    writeJson(correctKeys),
                    writeJson(errors));
            count++;
        }
        return count;
    }

    private List<String> validateGenerated(
            String expectedTaxonomyKey,
            String expectedChildNodeKey,
            String taxonomyKey,
            String childNodeKey,
            String questionType,
            String difficulty,
            String questionText,
            List<QuestionOption> options,
            List<String> correctKeys,
            String explanation,
            String sourceReference) {
        List<String> errors = new ArrayList<>();
        if (!expectedTaxonomyKey.equals(taxonomyKey)) errors.add("taxonomyKey does not match selected taxonomy");
        if (!expectedChildNodeKey.equals(childNodeKey)) errors.add("childNodeKey does not match selected taxonomy node");
        if (!Set.of("SINGLE_SELECT", "MULTIPLE_SELECT").contains(upper(questionType))) errors.add("Unsupported questionType");
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(upper(difficulty))) errors.add("Unsupported difficulty");
        if (isBlank(questionText)) errors.add("questionText is required");
        if (isBlank(explanation)) errors.add("explanation is required");
        if (isBlank(sourceReference)) errors.add("sourceReference is required");
        if (options.size() < 4 || options.size() > 6) errors.add("Question must have 4 to 6 options");
        Set<String> optionKeys = new LinkedHashSet<>();
        for (QuestionOption option : options) {
            if (isBlank(option.key()) || !optionKeys.add(option.key().trim().toUpperCase(Locale.ROOT))) {
                errors.add("Option keys must be present and unique");
                break;
            }
            if (isBlank(option.text()) && isBlank(option.mediaObjectKey())) {
                errors.add("Option text or image is required");
                break;
            }
        }
        long correctCount = correctKeys.stream().filter(key -> optionKeys.contains(key.toUpperCase(Locale.ROOT))).count();
        if ("SINGLE_SELECT".equals(upper(questionType)) && correctCount != 1) {
            errors.add("SINGLE_SELECT requires exactly one correct option");
        }
        if ("MULTIPLE_SELECT".equals(upper(questionType)) && (correctCount < 2 || correctCount == options.size())) {
            errors.add("MULTIPLE_SELECT requires at least two correct options and one incorrect option");
        }
        return errors;
    }

    private boolean duplicateQuestion(AiGenerationJobRow job, String questionText) {
        TaxonomyNodeEntity node = taxonomyNodes.findByIdAndTenantId(job.taxonomyNodeId(), job.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taxonomy node was not found"));
        return questions.findByRootTaxonomyNode_IdAndChildTaxonomyNode_IdAndNormalizedQuestionTextAndTenantId(
                        rootTaxonomyNode(node).getId(),
                        node.getId(),
                        normalizeQuestionText(questionText),
                        job.tenantId())
                .isPresent();
    }

    private String normalizeQuestionText(String value) {
        return requireText(value, "questionText")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private void createChunks(UUID tenantId, UUID jobId, String text, String referencePrefix) {
        List<String> words = List.of(text.split("\\s+"));
        int index = 0;
        int start = 0;
        while (start < words.size()) {
            int end = Math.min(words.size(), start + TARGET_CHUNK_WORDS);
            String chunk = String.join(" ", words.subList(start, end)).trim();
            if (!chunk.isBlank()) {
                String reference = (referencePrefix == null ? "Text" : referencePrefix) + " chunk " + (index + 1);
                jdbcTemplate.update("""
                        INSERT INTO ai_source_chunk
                            (id, tenant_id, job_id, chunk_index, source_reference, chunk_text)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), tenantId, jobId, index, reference, chunk);
                index++;
            }
            if (end == words.size()) break;
            start = Math.max(end - CHUNK_OVERLAP_WORDS, start + 1);
        }
    }

    private String extractPdfText(String objectKey) {
        StoredMedia media = storage.readMedia(objectKey);
        try (PDDocument document = Loader.loadPDF(new ByteArrayInputStream(media.bytes()).readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return normalizeText(stripper.getText(document));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to extract text from PDF", ex);
        }
    }

    private String normalizeText(String value) {
        String text = requireText(value, "sourceText").replaceAll("\\s+", " ").trim();
        if (text.length() < 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source text is too short to generate questions");
        }
        return text;
    }

    private AiGenerationJobResponse toJobResponse(AiGenerationJobRow job, boolean includeQuestions) {
        List<AiGeneratedQuestionResponse> generated = includeQuestions ? generatedQuestions(job.tenantId(), job.id()) : List.of();
        Map<String, Integer> counts = counts(job.tenantId(), job.id());
        return new AiGenerationJobResponse(job.id(), job.taxonomyNodeId(), job.taxonomyKey(), job.childNodeKey(), job.taxonomyPath(),
                job.sourceType(), job.sourceObjectKey(), job.sourceFilename(), job.topic(), job.instructions(), job.questionCount(),
                job.status(), job.errorMessage(), counts.getOrDefault("chunks", 0), counts.getOrDefault("generated", 0),
                counts.getOrDefault("valid", 0), counts.getOrDefault("approved", 0), job.createdAt(), job.updatedAt(), generated);
    }

    private AiGeneratedQuestionResponse toGenerated(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AiGeneratedQuestionResponse(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("job_id")),
                rs.getString("taxonomy_key"),
                rs.getString("child_node_key"),
                rs.getString("status"),
                rs.getString("review_status"),
                rs.getString("question_type"),
                rs.getString("difficulty"),
                rs.getString("question_text"),
                rs.getString("explanation"),
                rs.getString("source_reference"),
                readJson(rs.getString("options_json"), new TypeReference<List<QuestionOption>>() {}),
                readJson(rs.getString("correct_option_keys_json"), new TypeReference<List<String>>() {}),
                readJson(rs.getString("validation_errors_json"), new TypeReference<List<String>>() {}),
                rs.getString("created_question_id") == null ? null : UUID.fromString(rs.getString("created_question_id")),
                timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("updated_at")));
    }

    private List<AiGeneratedQuestionResponse> generatedQuestions(UUID tenantId, UUID jobId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_generated_question
                WHERE job_id = ? AND tenant_id = ?
                ORDER BY created_at ASC
                """, this::toGenerated, jobId, tenantId);
    }

    private AiGeneratedQuestionResponse requireGenerated(UUID tenantId, UUID id) {
        return jdbcTemplate.query("""
                SELECT generated.*
                FROM ai_generated_question generated
                WHERE generated.id = ? AND generated.tenant_id = ?
                """, this::toGenerated, id, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generated question was not found"));
    }

    private AiGenerationJobRow requireJob(UUID tenantId, UUID id) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_generation_job
                WHERE id = ? AND tenant_id = ?
                """, (rs, rowNum) -> toJobRow(rs), id, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI generation job was not found"));
    }

    private AiGenerationJobRow toJobRow(ResultSet rs) throws java.sql.SQLException {
        return new AiGenerationJobRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("tenant_id")),
                UUID.fromString(rs.getString("taxonomy_node_id")),
                rs.getString("taxonomy_key"),
                rs.getString("child_node_key"),
                rs.getString("taxonomy_path"),
                rs.getString("source_type"),
                rs.getString("source_object_key"),
                rs.getString("source_filename"),
                rs.getString("topic"),
                rs.getString("instructions"),
                rs.getInt("question_count"),
                rs.getString("status"),
                rs.getString("error_message"),
                timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("updated_at")));
    }

    private List<AiChunkRow> chunks(UUID tenantId, UUID jobId) {
        return jdbcTemplate.query("""
                SELECT * FROM ai_source_chunk
                WHERE job_id = ? AND tenant_id = ?
                ORDER BY chunk_index ASC
                """, (rs, rowNum) -> new AiChunkRow(
                UUID.fromString(rs.getString("id")),
                rs.getInt("chunk_index"),
                rs.getString("source_reference"),
                rs.getString("chunk_text")), jobId, tenantId);
    }

    private int chunkCount(UUID tenantId, UUID jobId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_source_chunk WHERE job_id = ? AND tenant_id = ?", Integer.class, jobId, tenantId);
        return count == null ? 0 : count;
    }

    private Map<String, Integer> counts(UUID tenantId, UUID jobId) {
        Integer chunks = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_source_chunk WHERE job_id = ? AND tenant_id = ?", Integer.class, jobId, tenantId);
        Integer generated = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_generated_question WHERE job_id = ? AND tenant_id = ?", Integer.class, jobId, tenantId);
        Integer valid = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_generated_question WHERE job_id = ? AND tenant_id = ? AND status = 'VALID'", Integer.class, jobId, tenantId);
        Integer approved = jdbcTemplate.queryForObject("SELECT count(*) FROM ai_generated_question WHERE job_id = ? AND tenant_id = ? AND review_status = 'APPROVED'", Integer.class, jobId, tenantId);
        return Map.of("chunks", nullToZero(chunks), "generated", nullToZero(generated), "valid", nullToZero(valid), "approved", nullToZero(approved));
    }

    private void updateJobStatus(UUID jobId, String status, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE ai_generation_job
                SET status = ?, error_message = ?, updated_at = now()
                WHERE id = ?
                """, status, errorMessage, jobId);
    }

    private TaxonomyNodeEntity activeLeaf(UUID tenantId, UUID id) {
        TaxonomyNodeEntity node = taxonomyNodes.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taxonomy node was not found"));
        if (!"ACTIVE".equals(node.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taxonomy node must be active");
        }
        if (taxonomyNodes.existsByParentNode_IdAndTenantId(id, tenantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI generation requires a selected leaf taxonomy node");
        }
        return node;
    }

    private TaxonomyNodeEntity rootTaxonomyNode(TaxonomyNodeEntity node) {
        TaxonomyNodeEntity root = node.getRootTaxonomyNode();
        return root == null ? node : root;
    }

    private String taxonomyPath(TaxonomyNodeEntity node) {
        List<String> labels = new ArrayList<>();
        TaxonomyNodeEntity current = node;
        while (current != null) {
            labels.add(0, current.getDisplayName());
            current = current.getParentNode();
        }
        return String.join(" > ", labels);
    }

    private String taxonomyImportKey(TaxonomyNodeEntity node) {
        if (!isBlank(node.getExternalKey())) return node.getExternalKey();
        if (!isBlank(node.getNodeKey())) return node.getNodeKey();
        return node.getId().toString();
    }

    private List<String> allowedTypes(AiGenerationJobRequest settings) {
        if (settings == null || settings.allowedQuestionTypes() == null || settings.allowedQuestionTypes().isEmpty()) {
            return List.of("SINGLE_SELECT", "MULTIPLE_SELECT");
        }
        return settings.allowedQuestionTypes().stream().map(this::upper).filter(value -> Set.of("SINGLE_SELECT", "MULTIPLE_SELECT").contains(value)).toList();
    }

    private Map<String, Integer> difficultyMix(AiGenerationJobRequest settings) {
        if (settings == null || settings.difficultyMix() == null || settings.difficultyMix().isEmpty()) {
            return Map.of("EASY", 1, "MEDIUM", 2, "HARD", 1);
        }
        return settings.difficultyMix();
    }

    private List<QuestionOption> normalizeOptions(List<QuestionOption> options) {
        if (options == null) return List.of();
        return options.stream()
                .map(option -> new QuestionOption(requireText(option.key(), "option.key").toUpperCase(Locale.ROOT),
                        trimToNull(option.text()), option.mediaObjectKey(), option.mediaContentType(), option.correct()))
                .toList();
    }

    private List<String> normalizeKeys(List<String> keys) {
        if (keys == null) return List.of();
        return keys.stream().filter(value -> !isBlank(value)).map(value -> value.trim().toUpperCase(Locale.ROOT)).toList();
    }

    private String nullToExpected(String value, String expected) {
        return isBlank(value) ? expected : value.trim();
    }

    private String normalizeSourceType(String value) {
        String normalized = requireText(value, "sourceType").toUpperCase(Locale.ROOT);
        if (!Set.of("PDF", "TEXT").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceType must be PDF or TEXT");
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to write JSON", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read JSON", ex);
        }
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        return value;
    }

    private String requireText(String value, String field) {
        if (isBlank(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        return value.trim();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String rootMessage(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        Throwable current = ex;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? ex.getMessage() : current.getMessage();
    }

    private record AiGenerationJobRow(
            UUID id,
            UUID tenantId,
            UUID taxonomyNodeId,
            String taxonomyKey,
            String childNodeKey,
            String taxonomyPath,
            String sourceType,
            String sourceObjectKey,
            String sourceFilename,
            String topic,
            String instructions,
            int questionCount,
            String status,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt) {
    }

    private record AiChunkRow(
            UUID id,
            int chunkIndex,
            String sourceReference,
            String chunkText) {
    }
}
