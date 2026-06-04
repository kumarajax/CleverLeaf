package com.clearleaf.api;

import com.clearleaf.api.entity.QuestionAnswerEntity;
import com.clearleaf.api.entity.QuestionEntity;
import com.clearleaf.api.entity.QuestionOptionEntity;
import com.clearleaf.api.entity.TaxonomyNodeEntity;
import com.clearleaf.api.entity.TestAttemptEntity;
import com.clearleaf.api.entity.TestAttemptQuestionEntity;
import com.clearleaf.api.repository.QuestionRepository;
import com.clearleaf.api.repository.TaxonomyNodeRepository;
import com.clearleaf.api.repository.TestAttemptQuestionRepository;
import com.clearleaf.api.repository.TestAttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentTestService {
    private static final Set<String> TESTABLE_WORKFLOW_STATUSES = Set.of("ACTIVE", "READY_FOR_TEST", "PRACTICE");

    private final TestAttemptRepository attempts;
    private final TestAttemptQuestionRepository attemptQuestions;
    private final QuestionRepository questions;
    private final TaxonomyNodeRepository taxonomyNodes;

    public StudentTestService(
            TestAttemptRepository attempts,
            TestAttemptQuestionRepository attemptQuestions,
            QuestionRepository questions,
            TaxonomyNodeRepository taxonomyNodes) {
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.questions = questions;
        this.taxonomyNodes = taxonomyNodes;
    }

    @Transactional
    public StudentTestAttemptResponse createAttempt(String studentSubject, CreateStudentTestRequest request) {
        if (studentSubject == null || studentSubject.isBlank()) {
            throw new IllegalArgumentException("student subject is required");
        }
        UUID taxonomyNodeId = requireUuid(request == null ? null : request.taxonomyNodeId(), "taxonomyNodeId");
        String difficulty = normalizeDifficulty(request.difficulty());
        int questionCount = request.questionCount() <= 0 ? 10 : Math.min(request.questionCount(), 50);
        TaxonomyNodeEntity taxonomy = taxonomyNodes.findById(taxonomyNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown taxonomy node: " + taxonomyNodeId));
        List<UUID> taxonomyNodeIds = descendantIds(taxonomy.getId());
        List<QuestionEntity> eligible = questions.findRandomEligibleForTest(
                taxonomyNodeIds,
                difficulty,
                TESTABLE_WORKFLOW_STATUSES,
                PageRequest.of(0, questionCount * 3));
        List<QuestionEntity> selected = uniqueQuestions(eligible, questionCount);
        if (selected.size() < questionCount) {
            throw new IllegalArgumentException("Only " + selected.size() + " matching question(s) are available for this test");
        }

        Instant now = Instant.now();
        TestAttemptEntity attempt = new TestAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setStudentSubject(studentSubject);
        attempt.setTestName(defaultTestName(request.testName(), taxonomy, difficulty));
        attempt.setTaxonomyNode(taxonomy);
        attempt.setDifficulty(difficulty);
        attempt.setStatus("IN_PROGRESS");
        attempt.setStartedAt(now);
        attempt.setExpiresAt(now.plusSeconds((long) secondsPerQuestion(difficulty) * questionCount));
        attempt.setMaxPoints(questionCount);

        for (int index = 0; index < selected.size(); index++) {
            TestAttemptQuestionEntity attemptQuestion = new TestAttemptQuestionEntity();
            attemptQuestion.setId(UUID.randomUUID());
            attemptQuestion.setAttempt(attempt);
            attemptQuestion.setQuestion(selected.get(index));
            attemptQuestion.setQuestionOrder(index + 1);
            attempt.getQuestions().add(attemptQuestion);
        }
        attempts.save(attempt);
        return toAttemptResponse(attempt, attempt.getQuestions().getFirst());
    }

    private List<QuestionEntity> uniqueQuestions(List<QuestionEntity> eligible, int questionCount) {
        Map<UUID, QuestionEntity> uniqueById = new LinkedHashMap<>();
        for (QuestionEntity question : eligible) {
            uniqueById.putIfAbsent(question.getId(), question);
            if (uniqueById.size() == questionCount) break;
        }
        return uniqueById.values().stream().toList();
    }

    @Transactional(readOnly = true)
    public StudentTestAttemptResponse getAttempt(String studentSubject, UUID attemptId) {
        TestAttemptEntity attempt = findAttempt(studentSubject, attemptId);
        TestAttemptQuestionEntity current = attempt.getQuestions().stream()
                .min(Comparator.comparingInt(TestAttemptQuestionEntity::getQuestionOrder))
                .orElseThrow(() -> new IllegalStateException("Test attempt has no questions"));
        return toAttemptResponse(attempt, current);
    }

    @Transactional(readOnly = true)
    public StudentTestQuestion getQuestion(String studentSubject, UUID attemptId, UUID attemptQuestionId) {
        TestAttemptEntity attempt = findAttempt(studentSubject, attemptId);
        TestAttemptQuestionEntity attemptQuestion = findAttemptQuestion(attempt, attemptQuestionId);
        return toQuestion(attemptQuestion, "SUBMITTED".equals(attempt.getStatus()) || attemptQuestion.getCorrect() != null);
    }

    @Transactional
    public StudentTestQuestion saveAnswer(String studentSubject, UUID attemptId, UUID attemptQuestionId, SubmitStudentAnswerRequest request) {
        TestAttemptEntity attempt = findAttempt(studentSubject, attemptId);
        ensureAnswerable(attempt);
        TestAttemptQuestionEntity attemptQuestion = findAttemptQuestion(attempt, attemptQuestionId);
        if (attemptQuestion.getCorrect() != null) {
            throw new IllegalStateException("Question has already been submitted");
        }
        attemptQuestion.setSubmittedAnswer(serializeAnswer(attemptQuestion.getQuestion(), request));
        attemptQuestion.setAnsweredAt(Instant.now());
        attemptQuestion.setCorrect(null);
        attemptQuestion.setPointsAwarded(null);
        attemptQuestions.save(attemptQuestion);
        return toQuestion(attemptQuestion, false);
    }

    @Transactional
    public StudentTestQuestion submitQuestion(String studentSubject, UUID attemptId, UUID attemptQuestionId, SubmitStudentAnswerRequest request) {
        TestAttemptEntity attempt = findAttempt(studentSubject, attemptId);
        ensureAnswerable(attempt);
        TestAttemptQuestionEntity attemptQuestion = findAttemptQuestion(attempt, attemptQuestionId);
        if (attemptQuestion.getCorrect() != null) {
            return toQuestion(attemptQuestion, true);
        }
        attemptQuestion.setSubmittedAnswer(serializeAnswer(attemptQuestion.getQuestion(), request));
        attemptQuestion.setAnsweredAt(Instant.now());
        boolean correct = isCorrect(attemptQuestion.getQuestion(), attemptQuestion.getSubmittedAnswer());
        attemptQuestion.setCorrect(correct);
        attemptQuestion.setPointsAwarded(correct ? 1 : 0);
        attemptQuestions.save(attemptQuestion);
        return toQuestion(attemptQuestion, true);
    }

    @Transactional
    public StudentTestAttemptResponse submit(String studentSubject, UUID attemptId) {
        TestAttemptEntity attempt = findAttempt(studentSubject, attemptId);
        if ("SUBMITTED".equals(attempt.getStatus())) {
            return toAttemptResponse(attempt, attempt.getQuestions().getFirst());
        }
        int score = 0;
        for (TestAttemptQuestionEntity attemptQuestion : attempt.getQuestions()) {
            boolean correct = isCorrect(attemptQuestion.getQuestion(), attemptQuestion.getSubmittedAnswer());
            attemptQuestion.setCorrect(correct);
            attemptQuestion.setPointsAwarded(correct ? 1 : 0);
            if (correct) score++;
        }
        attempt.setScorePoints(score);
        attempt.setSubmittedAt(Instant.now());
        attempt.setStatus("SUBMITTED");
        attempts.save(attempt);
        return toAttemptResponse(attempt, attempt.getQuestions().getFirst());
    }

    private TestAttemptEntity findAttempt(String studentSubject, UUID attemptId) {
        return attempts.findByIdAndStudentSubject(requireUuid(attemptId, "attemptId"), studentSubject)
                .orElseThrow(() -> new IllegalArgumentException("Unknown test attempt: " + attemptId));
    }

    private TestAttemptQuestionEntity findAttemptQuestion(TestAttemptEntity attempt, UUID attemptQuestionId) {
        return attempt.getQuestions().stream()
                .filter(question -> question.getId().equals(attemptQuestionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown test question: " + attemptQuestionId));
    }

    private void ensureAnswerable(TestAttemptEntity attempt) {
        if ("SUBMITTED".equals(attempt.getStatus())) {
            throw new IllegalStateException("Test has already been submitted");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            submit(attempt.getStudentSubject(), attempt.getId());
            throw new IllegalStateException("Test time has expired");
        }
    }

    private StudentTestAttemptResponse toAttemptResponse(TestAttemptEntity attempt, TestAttemptQuestionEntity current) {
        boolean testSubmitted = "SUBMITTED".equals(attempt.getStatus());
        return new StudentTestAttemptResponse(
                attempt.getId(),
                attempt.getTestName(),
                attempt.getDifficulty(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getExpiresAt(),
                attempt.getSubmittedAt(),
                attempt.getQuestions().size(),
                attempt.getScorePoints(),
                attempt.getMaxPoints(),
                attempt.getQuestions().stream()
                        .sorted(Comparator.comparingInt(TestAttemptQuestionEntity::getQuestionOrder))
                        .map(question -> new StudentTestNavigationItem(
                                question.getId(),
                                question.getQuestionOrder(),
                                question.getSubmittedAnswer() != null && !question.getSubmittedAnswer().isBlank(),
                                question.getCorrect()))
                        .toList(),
                toQuestion(current, testSubmitted || current.getCorrect() != null),
                testSubmitted
                        ? attempt.getQuestions().stream()
                                .sorted(Comparator.comparingInt(TestAttemptQuestionEntity::getQuestionOrder))
                                .map(question -> toQuestion(question, true))
                                .toList()
                        : List.of());
    }

    private StudentTestQuestion toQuestion(TestAttemptQuestionEntity attemptQuestion, boolean revealScore) {
        QuestionEntity question = attemptQuestion.getQuestion();
        return new StudentTestQuestion(
                attemptQuestion.getId(),
                attemptQuestion.getQuestionOrder(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getOptions().stream()
                        .sorted(Comparator.comparingInt(QuestionOptionEntity::getSortOrder))
                        .map(option -> new StudentQuestionOption(option.getOptionKey(), option.getOptionText()))
                        .toList(),
                optionAnswer(question, attemptQuestion.getSubmittedAnswer()),
                textAnswer(question, attemptQuestion.getSubmittedAnswer()),
                revealScore ? correctOptionKeys(question) : List.of(),
                revealScore ? correctAnswerText(question) : "",
                revealScore ? attemptQuestion.getCorrect() : null);
    }

    private List<UUID> descendantIds(UUID rootId) {
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        List<UUID> ids = new ArrayList<>();
        Set<UUID> visited = new LinkedHashSet<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!visited.add(current)) continue;
            ids.add(current);
            for (TaxonomyNodeEntity child : taxonomyNodes.findByParentNode_IdOrderBySortOrderAscDisplayNameAsc(current)) {
                queue.addLast(child.getId());
            }
        }
        return ids;
    }

    private String serializeAnswer(QuestionEntity question, SubmitStudentAnswerRequest request) {
        if (request == null) return null;
        if (usesOptions(question)) {
            return request.selectedOptionKeys() == null ? "" : request.selectedOptionKeys().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .sorted()
                    .reduce((first, second) -> first + "," + second)
                    .orElse("");
        }
        return request.answerText() == null ? "" : request.answerText().trim();
    }

    private List<String> optionAnswer(QuestionEntity question, String submittedAnswer) {
        if (!usesOptions(question) || submittedAnswer == null || submittedAnswer.isBlank()) {
            return List.of();
        }
        return List.of(submittedAnswer.split(","));
    }

    private String textAnswer(QuestionEntity question, String submittedAnswer) {
        return usesOptions(question) ? "" : submittedAnswer;
    }

    private List<String> correctOptionKeys(QuestionEntity question) {
        if (!usesOptions(question)) {
            return List.of();
        }
        return question.getOptions().stream()
                .filter(QuestionOptionEntity::isCorrect)
                .map(QuestionOptionEntity::getOptionKey)
                .sorted()
                .toList();
    }

    private String correctAnswerText(QuestionEntity question) {
        if (usesOptions(question)) {
            return "";
        }
        return question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuestionAnswerEntity::getSortOrder))
                .map(QuestionAnswerEntity::getAnswerValue)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private boolean isCorrect(QuestionEntity question, String submittedAnswer) {
        if (submittedAnswer == null || submittedAnswer.isBlank()) return false;
        if (usesOptions(question)) {
            Set<String> expected = question.getOptions().stream()
                    .filter(QuestionOptionEntity::isCorrect)
                    .map(option -> option.getOptionKey().trim().toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> submitted = Arrays.stream(submittedAnswer.split(","))
                    .filter(value -> !value.isBlank())
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
            return expected.equals(submitted);
        }
        if ("NUMERICAL".equalsIgnoreCase(question.getQuestionType())) {
            return numericalCorrect(question, submittedAnswer);
        }
        return textCorrect(question, submittedAnswer);
    }

    private boolean numericalCorrect(QuestionEntity question, String submittedAnswer) {
        try {
            BigDecimal submitted = new BigDecimal(submittedAnswer.trim());
            for (QuestionAnswerEntity answer : question.getAnswers()) {
                BigDecimal expected = new BigDecimal(answer.getAnswerValue().trim());
                BigDecimal tolerance = answer.getToleranceValue() == null ? BigDecimal.ZERO : answer.getToleranceValue();
                if (submitted.subtract(expected).abs().compareTo(tolerance) <= 0) return true;
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        return false;
    }

    private boolean textCorrect(QuestionEntity question, String submittedAnswer) {
        String submitted = submittedAnswer.trim();
        for (QuestionAnswerEntity answer : question.getAnswers()) {
            boolean caseSensitive = Boolean.TRUE.equals(answer.getCaseSensitive());
            if (caseSensitive && submitted.equals(answer.getAnswerValue().trim())) return true;
            if (!caseSensitive && submitted.equalsIgnoreCase(answer.getAnswerValue().trim())) return true;
        }
        return false;
    }

    private boolean usesOptions(QuestionEntity question) {
        return Set.of("SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE").contains(question.getQuestionType().toUpperCase(Locale.ROOT));
    }

    private int secondsPerQuestion(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> 60;
            case "HARD" -> 30;
            default -> 45;
        };
    }

    private String defaultTestName(String value, TaxonomyNodeEntity taxonomy, String difficulty) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return taxonomy.getDisplayName() + " " + difficulty.charAt(0) + difficulty.substring(1).toLowerCase(Locale.ROOT) + " Test";
    }

    private String normalizeDifficulty(String value) {
        String normalized = value == null || value.isBlank() ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(normalized)) {
            throw new IllegalArgumentException("Invalid difficulty: " + value);
        }
        return normalized;
    }

    private UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
