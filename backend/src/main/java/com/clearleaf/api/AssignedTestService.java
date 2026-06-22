package com.clearleaf.api;

import com.clearleaf.api.entity.AdminTestEntity;
import com.clearleaf.api.entity.AdminTestVersionEntity;
import com.clearleaf.api.entity.AdminTestVersionQuestionEntity;
import com.clearleaf.api.entity.AssignedTestAssignmentEntity;
import com.clearleaf.api.entity.AssignedTestImportJobEntity;
import com.clearleaf.api.entity.AssignedTestImportRowEntity;
import com.clearleaf.api.entity.QuestionAnswerEntity;
import com.clearleaf.api.entity.QuestionEntity;
import com.clearleaf.api.entity.QuestionOptionEntity;
import com.clearleaf.api.entity.QuestionTagEntity;
import com.clearleaf.api.entity.QuestionTaxonomyNodeEntity;
import com.clearleaf.api.entity.TaxonomyNodeEntity;
import com.clearleaf.api.entity.TestAttemptEntity;
import com.clearleaf.api.entity.TestAttemptQuestionEntity;
import com.clearleaf.api.repository.AdminTestRepository;
import com.clearleaf.api.repository.AdminTestVersionRepository;
import com.clearleaf.api.repository.AssignedTestAssignmentRepository;
import com.clearleaf.api.repository.AssignedTestImportJobRepository;
import com.clearleaf.api.repository.AssignedTestImportRowRepository;
import com.clearleaf.api.repository.QuestionRepository;
import com.clearleaf.api.repository.TestAttemptQuestionRepository;
import com.clearleaf.api.repository.TestAttemptRepository;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AssignedTestService {
    private static final Set<String> ASSIGNED_TEST_WORKFLOW_STATUSES = Set.of("ACTIVE", "APPROVED", "PRACTICE");
    private static final Set<String> OPEN_ASSIGNMENT_STATUSES = Set.of("ASSIGNED", "STARTED");
    private static final Set<String> REASSIGNABLE_ASSIGNMENT_STATUSES = Set.of("STARTED", "SUBMITTED");
    private static final String TEST_STATUS_DRAFT = "DRAFT";
    private static final String TEST_STATUS_ACTIVE = "ACTIVE";
    private static final String TEST_STATUS_PUBLISHED = "PUBLISHED";
    private static final String ASSIGNMENT_STATUS_ASSIGNED = "ASSIGNED";
    private static final String ASSIGNMENT_STATUS_REASSIGNED = "REASSIGNED";

    private final AdminTestRepository adminTests;
    private final AdminTestVersionRepository versions;
    private final AssignedTestAssignmentRepository assignments;
    private final AssignedTestImportJobRepository jobs;
    private final AssignedTestImportRowRepository jobRows;
    private final QuestionRepository questions;
    private final TestAttemptRepository attempts;
    private final TestAttemptQuestionRepository attemptQuestions;
    private final MinioStorageService storage;
    private final TransactionTemplate transactions;

    public AssignedTestService(
            AdminTestRepository adminTests,
            AdminTestVersionRepository versions,
            AssignedTestAssignmentRepository assignments,
            AssignedTestImportJobRepository jobs,
            AssignedTestImportRowRepository jobRows,
            QuestionRepository questions,
            TestAttemptRepository attempts,
            TestAttemptQuestionRepository attemptQuestions,
            MinioStorageService storage,
            PlatformTransactionManager transactionManager) {
        this.adminTests = adminTests;
        this.versions = versions;
        this.assignments = assignments;
        this.jobs = jobs;
        this.jobRows = jobRows;
        this.questions = questions;
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<AdminAssignedTestSummary> adminTests(String creatorSubject) {
        return adminTests.findByCreatorSubjectOrderByCreatedAtDesc(requireSubject(creatorSubject))
                .stream()
                .map(test -> versions.findFirstByTest_IdOrderByVersionNumberDesc(test.getId())
                        .map(this::toSummary)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public AdminAssignedTestDetail createAdminTest(String creatorSubject, CreateAdminAssignedTestRequest request) {
        String subject = requireSubject(creatorSubject);
        String publicKey = requireText(request.publicKey(), "publicKey").toUpperCase(Locale.ROOT);
        if (adminTests.findByPublicKeyIgnoreCase(publicKey).isPresent()) {
            throw new IllegalArgumentException("Test publicKey already exists: " + publicKey);
        }
        List<UUID> questionIds = request.questionIds() == null ? List.of() : request.questionIds().stream().distinct().toList();
        if (questionIds.isEmpty()) {
            throw new IllegalArgumentException("At least one question is required");
        }
        int timeAllowedSeconds = request.timeAllowedSeconds() <= 0 ? 1800 : request.timeAllowedSeconds();

        AdminTestEntity test = new AdminTestEntity();
        test.setId(UUID.randomUUID());
        test.setPublicKey(publicKey);
        test.setName(requireText(request.name(), "name"));
        test.setCreatorSubject(subject);
        test.setStatus(TEST_STATUS_DRAFT);

        AdminTestVersionEntity version = new AdminTestVersionEntity();
        version.setId(UUID.randomUUID());
        version.setTest(test);
        version.setVersionNumber(1);
        version.setTimeAllowedSeconds(timeAllowedSeconds);
        version.setAvailableFrom(request.availableFrom());
        version.setAvailableUntil(request.availableUntil());
        test.getVersions().add(version);

        for (int index = 0; index < questionIds.size(); index++) {
            UUID questionId = questionIds.get(index);
            QuestionEntity question = questions.findById(questionId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown question: " + questionId));
            validateAssignedTestQuestion(question);
            AdminTestVersionQuestionEntity versionQuestion = new AdminTestVersionQuestionEntity();
            versionQuestion.setId(UUID.randomUUID());
            versionQuestion.setVersion(version);
            versionQuestion.setQuestion(question);
            versionQuestion.setQuestionOrder(index + 1);
            version.getQuestions().add(versionQuestion);
        }
        adminTests.save(test);
        return toDetail(version);
    }

    @Transactional(readOnly = true)
    public AdminAssignedTestDetail adminTest(String creatorSubject, UUID versionId) {
        return toDetail(requireCreatorVersion(creatorSubject, versionId));
    }

    @Transactional
    public AdminAssignedTestSummary activateTest(String creatorSubject, UUID versionId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        AdminTestEntity test = version.getTest();
        if (!TEST_STATUS_DRAFT.equals(test.getStatus())) {
            throw new IllegalStateException("Only draft tests can be activated");
        }
        if (version.getQuestions().isEmpty()) {
            throw new IllegalStateException("At least one question is required before activating a test");
        }
        test.setStatus(TEST_STATUS_ACTIVE);
        adminTests.save(test);
        return toSummary(version);
    }

    @Transactional
    public void deleteTest(String creatorSubject, UUID versionId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        AdminTestEntity test = version.getTest();
        if (TEST_STATUS_PUBLISHED.equals(test.getStatus())) {
            throw new IllegalStateException("Published tests cannot be deleted");
        }
        if (!Set.of(TEST_STATUS_DRAFT, TEST_STATUS_ACTIVE).contains(test.getStatus())) {
            throw new IllegalStateException("Only draft or active tests can be deleted");
        }
        adminTests.delete(test);
    }

    @Transactional
    public AssignedTestImportJobResponse startAssignmentImport(String actorSubject, String objectKey) {
        AssignedTestImportJobEntity job = new AssignedTestImportJobEntity();
        job.setId(UUID.randomUUID());
        job.setActorSubject(requireSubject(actorSubject));
        job.setObjectKey(requireText(objectKey, "objectKey"));
        job.setStatus("QUEUED");
        jobs.save(job);
        CompletableFuture.runAsync(() -> processImportJob(job.getId()));
        return toJob(job);
    }

    @Transactional(readOnly = true)
    public AssignedTestImportJobResponse importJob(String actorSubject, UUID jobId) {
        return toJob(jobs.findByIdAndActorSubject(jobId, requireSubject(actorSubject))
                .orElseThrow(() -> new IllegalArgumentException("Unknown import job: " + jobId)));
    }

    @Transactional(readOnly = true)
    public List<AssignedTestImportRowResponse> importRows(String actorSubject, UUID jobId) {
        importJob(actorSubject, jobId);
        return jobRows.findTop200ByJob_IdOrderByLineNumberAsc(jobId).stream().map(this::toRow).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAssignedTestResult> adminResults(String creatorSubject, UUID versionId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        return assignments.findByVersion_Test_CreatorSubjectOrderByAssignedAtDesc(requireSubject(creatorSubject)).stream()
                .filter(assignment -> assignment.getVersion().getId().equals(version.getId()))
                .map(assignment -> toAdminResult(assignment, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminAssignedTestResult adminResult(String creatorSubject, UUID versionId, UUID assignmentId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        AssignedTestAssignmentEntity assignment = assignments.findById(assignmentId)
                .filter(candidate -> candidate.getVersion().getId().equals(version.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown assigned test result: " + assignmentId));
        return toAdminResult(assignment, true);
    }

    @Transactional
    public AdminAssignedTestResult assignStudent(String creatorSubject, UUID versionId, AssignAdminTestRequest request) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        ensureAssignable(version);
        String studentSubject = requireText(request == null ? null : request.studentSubject(), "studentSubject");
        AssignedTestAssignmentEntity assignment = assignments.findFirstByVersion_IdAndStudentSubjectIgnoreCaseAndStatusInOrderByAssignedAtDesc(
                        version.getId(), studentSubject, OPEN_ASSIGNMENT_STATUSES)
                .orElseGet(() -> createAssignment(version, studentSubject, null));
        publishTest(version);
        return toAdminResult(assignment, false);
    }

    @Transactional
    public AdminAssignedTestSummary publishResults(String creatorSubject, UUID versionId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        Instant now = Instant.now();
        version.setResultsPublishedAt(now);
        assignments.findByVersion_Test_CreatorSubjectOrderByAssignedAtDesc(requireSubject(creatorSubject)).stream()
                .filter(assignment -> assignment.getVersion().getId().equals(version.getId()))
                .filter(assignment -> "SUBMITTED".equals(assignment.getStatus()))
                .forEach(assignment -> assignment.setResultsPublishedAt(now));
        versions.save(version);
        return toSummary(version);
    }

    @Transactional
    public AdminAssignedTestResult publishStudentResult(String creatorSubject, UUID versionId, UUID assignmentId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        AssignedTestAssignmentEntity assignment = assignments.findById(assignmentId)
                .filter(candidate -> candidate.getVersion().getId().equals(version.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown assigned test result: " + assignmentId));
        if (!"SUBMITTED".equals(assignment.getStatus())) {
            throw new IllegalStateException("Only submitted test results can be published");
        }
        assignment.setResultsPublishedAt(Instant.now());
        assignments.save(assignment);
        return toAdminResult(assignment, false);
    }

    @Transactional
    public AdminAssignedTestResult reassignStudentTest(String creatorSubject, UUID versionId, UUID assignmentId) {
        AdminTestVersionEntity version = requireCreatorVersion(creatorSubject, versionId);
        ensureAssignable(version);
        AssignedTestAssignmentEntity assignment = assignments.findById(assignmentId)
                .filter(candidate -> candidate.getVersion().getId().equals(version.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown assigned test result: " + assignmentId));
        if (!REASSIGNABLE_ASSIGNMENT_STATUSES.contains(assignment.getStatus())) {
            throw new IllegalStateException("Only started or submitted assignments can be reassigned");
        }
        if (isResultPublished(assignment)) {
            throw new IllegalStateException("Published results cannot be reassigned");
        }
        assignments.findFirstByVersion_IdAndStudentSubjectIgnoreCaseAndStatusInOrderByAssignedAtDesc(
                version.getId(), assignment.getStudentSubject(), OPEN_ASSIGNMENT_STATUSES)
                .ifPresent(openAssignment -> {
                    if (!openAssignment.getId().equals(assignment.getId())) {
                        throw new IllegalStateException("Student already has an open assignment for this test");
                    }
        });
        assignment.setStatus(ASSIGNMENT_STATUS_REASSIGNED);
        assignment.setResetAt(Instant.now());
        assignments.saveAndFlush(assignment);
        AssignedTestAssignmentEntity reassigned = createAssignment(version, assignment.getStudentSubject(), null);
        publishTest(version);
        return toAdminResult(reassigned, false);
    }

    @Transactional(readOnly = true)
    public List<StudentAssignedTestSummary> studentAssigned(Collection<String> studentIdentifiers, boolean resultsOnly) {
        List<String> identifiers = requireIdentifiers(studentIdentifiers);
        return assignments.findByStudentSubjectInOrderByAssignedAtDesc(identifiers)
                .stream()
                .filter(assignment -> !ASSIGNMENT_STATUS_REASSIGNED.equals(assignment.getStatus()))
                .filter(assignment -> resultsOnly == (isResultPublished(assignment) && "SUBMITTED".equals(assignment.getStatus())))
                .map(this::toStudentSummary)
                .toList();
    }

    @Transactional
    public StudentTestAttemptResponse startAssigned(String canonicalStudentSubject, Collection<String> studentIdentifiers, UUID assignmentId) {
        AssignedTestAssignmentEntity assignment = findStudentAssignment(studentIdentifiers, assignmentId);
        AdminTestVersionEntity version = assignment.getVersion();
        Instant now = Instant.now();
        if (!TEST_STATUS_PUBLISHED.equals(version.getTest().getStatus())) {
            throw new IllegalStateException("Assigned test has not been published");
        }
        if (version.getAvailableFrom() != null && now.isBefore(version.getAvailableFrom())) {
            throw new IllegalStateException("Assigned test is not available yet");
        }
        if (version.getAvailableUntil() != null && now.isAfter(version.getAvailableUntil())) {
            throw new IllegalStateException("Assigned test is no longer available");
        }
        return attempts.findByAssignment_Id(assignment.getId())
                .map(this::toAttemptResponse)
                .orElseGet(() -> createAssignedAttempt(assignment, requireSubject(canonicalStudentSubject), now));
    }

    @Transactional(readOnly = true)
    public StudentTestAttemptResponse getAssignedAttempt(Collection<String> studentIdentifiers, UUID assignmentId) {
        AssignedTestAssignmentEntity assignment = findStudentAssignment(studentIdentifiers, assignmentId);
        TestAttemptEntity attempt = attempts.findByAssignment_Id(assignment.getId())
                .orElseThrow(() -> new IllegalArgumentException("Assigned test has not been started"));
        if ("SUBMITTED".equals(attempt.getStatus()) && !isResultPublished(assignment)) {
            return toAttemptResponse(attempt, false);
        }
        return toAttemptResponse(attempt);
    }

    @Transactional(readOnly = true)
    public StudentTestQuestion getAssignedQuestion(Collection<String> studentIdentifiers, UUID assignmentId, UUID attemptQuestionId) {
        TestAttemptEntity attempt = findStudentAttempt(studentIdentifiers, assignmentId);
        TestAttemptQuestionEntity question = findAttemptQuestion(attempt, attemptQuestionId);
        boolean reveal = "SUBMITTED".equals(attempt.getStatus()) && isResultPublished(attempt.getAssignment());
        return toQuestion(question, reveal);
    }

    @Transactional
    public StudentTestQuestion saveAssignedAnswer(Collection<String> studentIdentifiers, UUID assignmentId, UUID attemptQuestionId, SubmitStudentAnswerRequest request) {
        TestAttemptEntity attempt = findStudentAttempt(studentIdentifiers, assignmentId);
        ensureAnswerable(attempt);
        TestAttemptQuestionEntity question = findAttemptQuestion(attempt, attemptQuestionId);
        question.setSubmittedAnswer(serializeAnswer(question.getQuestion(), request));
        question.setAnsweredAt(Instant.now());
        question.setCorrect(null);
        question.setPointsAwarded(null);
        attemptQuestions.save(question);
        return toQuestion(question, false);
    }

    @Transactional
    public StudentTestAttemptResponse submitAssigned(Collection<String> studentIdentifiers, UUID assignmentId) {
        TestAttemptEntity attempt = findStudentAttempt(studentIdentifiers, assignmentId);
        if (!"SUBMITTED".equals(attempt.getStatus())) {
            grade(attempt);
            Instant now = Instant.now();
            attempt.setSubmittedAt(now);
            attempt.setStatus("SUBMITTED");
            attempt.getAssignment().setStatus("SUBMITTED");
            attempt.getAssignment().setSubmittedAt(now);
            attempts.save(attempt);
        }
        return toAttemptResponse(attempt, isResultPublished(attempt.getAssignment()));
    }

    private void processImportJob(UUID jobId) {
        transactions.executeWithoutResult(status -> {
            AssignedTestImportJobEntity job = jobs.findById(jobId).orElseThrow();
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            jobs.save(job);
        });
        try {
            String csv = storage.readText(jobs.findById(jobId).orElseThrow().getObjectKey());
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build()
                    .parse(new StringReader(csv));
            int total = 0;
            int imported = 0;
            int skipped = 0;
            int failed = 0;
            for (CSVRecord record : records) {
                total++;
                RowOutcome outcome = importAssignmentRow(jobId, total + 1, value(record, "TestPublicKey", "TestId"), value(record, "StudentSubject"));
                if ("IMPORTED".equals(outcome.status())) imported++;
                else if ("SKIPPED".equals(outcome.status())) skipped++;
                else failed++;
            }
            int finalTotal = total;
            int finalImported = imported;
            int finalSkipped = skipped;
            int finalFailed = failed;
            transactions.executeWithoutResult(status -> {
                AssignedTestImportJobEntity job = jobs.findById(jobId).orElseThrow();
                job.setTotalRows(finalTotal);
                job.setImportedRows(finalImported);
                job.setSkippedRows(finalSkipped);
                job.setFailedRows(finalFailed);
                job.setStatus("COMPLETED");
                job.setCompletedAt(Instant.now());
                jobs.save(job);
            });
        } catch (Exception ex) {
            transactions.executeWithoutResult(status -> {
                AssignedTestImportJobEntity job = jobs.findById(jobId).orElseThrow();
                job.setStatus("FAILED");
                job.setErrorMessage(ex.getMessage());
                job.setCompletedAt(Instant.now());
                jobs.save(job);
            });
        }
    }

    private RowOutcome importAssignmentRow(UUID jobId, int lineNumber, String testPublicKey, String studentSubject) {
        return transactions.execute(status -> {
            AssignedTestImportJobEntity job = jobs.findById(jobId).orElseThrow();
            String cleanKey = testPublicKey == null ? "" : testPublicKey.trim();
            String cleanSubject = studentSubject == null ? "" : studentSubject.trim();
            String rowStatus;
            String message;
            if (cleanKey.isBlank() || cleanSubject.isBlank()) {
                rowStatus = "FAILED";
                message = "TestPublicKey and StudentSubject are required";
            } else {
                AdminTestEntity test = adminTests.findByPublicKeyIgnoreCase(cleanKey).orElse(null);
                AdminTestVersionEntity version = test == null ? null : versions.findFirstByTest_IdOrderByVersionNumberDesc(test.getId()).orElse(null);
                if (test == null || version == null) {
                    rowStatus = "FAILED";
                    message = "Unknown TestPublicKey";
                } else if (!job.getActorSubject().equals(test.getCreatorSubject())) {
                    rowStatus = "FAILED";
                    message = "Only the test creator can assign this test";
                } else if (!isAssignableStatus(test.getStatus())) {
                    rowStatus = "FAILED";
                    message = "Only active or published tests can be assigned";
                } else if (assignments.findFirstByVersion_IdAndStudentSubjectIgnoreCaseAndStatusInOrderByAssignedAtDesc(
                        version.getId(), cleanSubject, OPEN_ASSIGNMENT_STATUSES).isPresent()) {
                    rowStatus = "SKIPPED";
                    message = "Assignment already exists";
                } else {
                    createAssignment(version, cleanSubject, job);
                    publishTest(version);
                    rowStatus = "IMPORTED";
                    message = "Assigned";
                }
            }
            AssignedTestImportRowEntity row = new AssignedTestImportRowEntity();
            row.setId(UUID.randomUUID());
            row.setJob(job);
            row.setLineNumber(lineNumber);
            row.setTestPublicKey(cleanKey);
            row.setStudentSubject(cleanSubject);
            row.setStatus(rowStatus);
            row.setMessage(message);
            jobRows.save(row);
            return new RowOutcome(rowStatus);
        });
    }

    private StudentTestAttemptResponse createAssignedAttempt(AssignedTestAssignmentEntity assignment, String studentSubject, Instant now) {
        if (ASSIGNMENT_STATUS_REASSIGNED.equals(assignment.getStatus())) {
            throw new IllegalStateException("Assigned test has been reassigned");
        }
        AdminTestVersionEntity version = assignment.getVersion();
        TestAttemptEntity attempt = new TestAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setStudentSubject(studentSubject);
        attempt.setTestName(version.getTest().getName());
        TaxonomyNodeEntity taxonomy = version.getQuestions().getFirst().getQuestion().getChildTaxonomyNode();
        attempt.setTaxonomyNode(taxonomy);
        attempt.setDifficulty("MEDIUM");
        attempt.setStatus("IN_PROGRESS");
        attempt.setStartedAt(now);
        attempt.setExpiresAt(now.plusSeconds(version.getTimeAllowedSeconds()));
        attempt.setMaxPoints(version.getQuestions().size());
        attempt.setSourceType("ASSIGNED");
        attempt.setAssignment(assignment);
        for (AdminTestVersionQuestionEntity versionQuestion : version.getQuestions()) {
            TestAttemptQuestionEntity attemptQuestion = new TestAttemptQuestionEntity();
            attemptQuestion.setId(UUID.randomUUID());
            attemptQuestion.setAttempt(attempt);
            attemptQuestion.setQuestion(versionQuestion.getQuestion());
            attemptQuestion.setQuestionOrder(versionQuestion.getQuestionOrder());
            attempt.getQuestions().add(attemptQuestion);
        }
        assignment.setStatus("STARTED");
        assignment.setStartedAt(now);
        attempts.save(attempt);
        return toAttemptResponse(attempt);
    }

    private AssignedTestAssignmentEntity createAssignment(AdminTestVersionEntity version, String studentSubject, AssignedTestImportJobEntity importJob) {
        AssignedTestAssignmentEntity assignment = new AssignedTestAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setVersion(version);
        assignment.setStudentSubject(studentSubject);
        assignment.setStatus(ASSIGNMENT_STATUS_ASSIGNED);
        assignment.setImportJob(importJob);
        return assignments.save(assignment);
    }

    private void ensureAnswerable(TestAttemptEntity attempt) {
        if ("SUBMITTED".equals(attempt.getStatus())) {
            throw new IllegalStateException("Test has already been submitted");
        }
        if (Instant.now().isAfter(attempt.getExpiresAt())) {
            grade(attempt);
            attempt.setStatus("SUBMITTED");
            attempt.setSubmittedAt(Instant.now());
            attempt.getAssignment().setStatus("SUBMITTED");
            attempt.getAssignment().setSubmittedAt(attempt.getSubmittedAt());
            attempts.save(attempt);
            throw new IllegalStateException("Test time has expired");
        }
    }

    private void grade(TestAttemptEntity attempt) {
        int score = 0;
        for (TestAttemptQuestionEntity question : attempt.getQuestions()) {
            boolean correct = isCorrect(question.getQuestion(), question.getSubmittedAnswer());
            question.setCorrect(correct);
            question.setPointsAwarded(correct ? 1 : 0);
            if (correct) score++;
        }
        attempt.setScorePoints(score);
    }

    private AdminTestVersionEntity requireCreatorVersion(String creatorSubject, UUID versionId) {
        AdminTestVersionEntity version = versions.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown test version: " + versionId));
        if (!version.getTest().getCreatorSubject().equals(requireSubject(creatorSubject))) {
            throw new IllegalArgumentException("Only the test creator can access this test");
        }
        return version;
    }

    private void validateAssignedTestQuestion(QuestionEntity question) {
        if (!ASSIGNED_TEST_WORKFLOW_STATUSES.contains(question.getWorkflowStatus())) {
            throw new IllegalArgumentException("Assigned tests can only use ACTIVE, APPROVED, or PRACTICE questions");
        }
        TaxonomyNodeEntity node = question.getChildTaxonomyNode();
        while (node != null) {
            if (!"ACTIVE".equals(node.getStatus())) {
                throw new IllegalArgumentException("Assigned tests can only use questions from ACTIVE taxonomy branches");
            }
            node = node.getParentNode();
        }
    }

    private void ensureAssignable(AdminTestVersionEntity version) {
        if (!isAssignableStatus(version.getTest().getStatus())) {
            throw new IllegalStateException("Only active or published tests can be assigned. Activate the test first.");
        }
    }

    private boolean isAssignableStatus(String status) {
        return TEST_STATUS_ACTIVE.equals(status) || TEST_STATUS_PUBLISHED.equals(status);
    }

    private void publishTest(AdminTestVersionEntity version) {
        AdminTestEntity test = version.getTest();
        if (TEST_STATUS_ACTIVE.equals(test.getStatus())) {
            test.setStatus(TEST_STATUS_PUBLISHED);
            adminTests.save(test);
        }
    }

    private AssignedTestAssignmentEntity findStudentAssignment(Collection<String> studentIdentifiers, UUID assignmentId) {
        AssignedTestAssignmentEntity assignment = assignments.findByIdAndStudentSubjectIn(assignmentId, requireIdentifiers(studentIdentifiers))
                .orElseThrow(() -> new IllegalArgumentException("Unknown assigned test: " + assignmentId));
        if (ASSIGNMENT_STATUS_REASSIGNED.equals(assignment.getStatus())) {
            throw new IllegalStateException("Assigned test has been reassigned");
        }
        return assignment;
    }

    private TestAttemptEntity findStudentAttempt(Collection<String> studentIdentifiers, UUID assignmentId) {
        TestAttemptEntity attempt = attempts.findByAssignment_IdAndStudentSubjectIn(assignmentId, requireIdentifiers(studentIdentifiers))
                .orElseThrow(() -> new IllegalArgumentException("Assigned test has not been started"));
        if (ASSIGNMENT_STATUS_REASSIGNED.equals(attempt.getAssignment().getStatus())) {
            throw new IllegalStateException("Assigned test has been reassigned");
        }
        return attempt;
    }

    private List<String> requireIdentifiers(Collection<String> values) {
        List<String> identifiers = values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (identifiers.isEmpty()) {
            throw new IllegalArgumentException("student identity is required");
        }
        return identifiers;
    }

    private TestAttemptQuestionEntity findAttemptQuestion(TestAttemptEntity attempt, UUID attemptQuestionId) {
        return attempt.getQuestions().stream()
                .filter(question -> question.getId().equals(attemptQuestionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown test question: " + attemptQuestionId));
    }

    private AdminAssignedTestSummary toSummary(AdminTestVersionEntity version) {
        AdminTestEntity test = version.getTest();
        return new AdminAssignedTestSummary(
                test.getId(),
                version.getId(),
                test.getPublicKey(),
                test.getName(),
                test.getStatus(),
                version.getQuestions().size(),
                version.getTimeAllowedSeconds(),
                version.getAvailableFrom(),
                version.getAvailableUntil(),
                version.getResultsPublishedAt(),
                assignments.countByVersion_IdAndStatusNot(version.getId(), ASSIGNMENT_STATUS_REASSIGNED),
                assignments.countByVersion_IdAndStatus(version.getId(), "SUBMITTED"),
                test.getCreatedAt());
    }

    private AdminAssignedTestDetail toDetail(AdminTestVersionEntity version) {
        AdminTestEntity test = version.getTest();
        return new AdminAssignedTestDetail(
                test.getId(),
                version.getId(),
                test.getPublicKey(),
                test.getName(),
                test.getStatus(),
                version.getTimeAllowedSeconds(),
                version.getAvailableFrom(),
                version.getAvailableUntil(),
                version.getResultsPublishedAt(),
                version.getQuestions().stream()
                        .sorted(Comparator.comparingInt(AdminTestVersionQuestionEntity::getQuestionOrder))
                        .map(AdminTestVersionQuestionEntity::getQuestion)
                        .map(this::toQuestionAdminRecord)
                        .toList());
    }

    private AdminAssignedTestResult toAdminResult(AssignedTestAssignmentEntity assignment, boolean includeAttempt) {
        TestAttemptEntity attempt = attempts.findByAssignment_Id(assignment.getId()).orElse(null);
        return new AdminAssignedTestResult(
                assignment.getId(),
                attempt == null ? null : attempt.getId(),
                assignment.getStudentSubject(),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getStartedAt(),
                assignment.getSubmittedAt(),
                assignment.getResultsPublishedAt(),
                attempt == null ? null : attempt.getScorePoints(),
                attempt == null ? assignment.getVersion().getQuestions().size() : attempt.getMaxPoints(),
                includeAttempt && attempt != null ? toAttemptResponse(attempt, true) : null);
    }

    private StudentAssignedTestSummary toStudentSummary(AssignedTestAssignmentEntity assignment) {
        TestAttemptEntity attempt = attempts.findByAssignment_Id(assignment.getId()).orElse(null);
        return new StudentAssignedTestSummary(
                assignment.getId(),
                attempt == null ? null : attempt.getId(),
                assignment.getVersion().getTest().getName(),
                assignment.getStatus(),
                assignment.getVersion().getQuestions().size(),
                assignment.getVersion().getTimeAllowedSeconds(),
                assignment.getVersion().getAvailableFrom(),
                assignment.getVersion().getAvailableUntil(),
                assignment.getAssignedAt(),
                assignment.getStartedAt(),
                assignment.getSubmittedAt(),
                !isResultPublished(assignment) || attempt == null ? null : attempt.getScorePoints(),
                attempt == null ? assignment.getVersion().getQuestions().size() : attempt.getMaxPoints(),
                isResultPublished(assignment));
    }

    private boolean isResultPublished(AssignedTestAssignmentEntity assignment) {
        return assignment.getResultsPublishedAt() != null || assignment.getVersion().getResultsPublishedAt() != null;
    }

    private AssignedTestImportJobResponse toJob(AssignedTestImportJobEntity job) {
        return new AssignedTestImportJobResponse(job.getId(), job.getObjectKey(), job.getStatus(), job.getTotalRows(),
                job.getImportedRows(), job.getSkippedRows(), job.getFailedRows(), job.getErrorMessage(),
                job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt());
    }

    private AssignedTestImportRowResponse toRow(AssignedTestImportRowEntity row) {
        return new AssignedTestImportRowResponse(row.getLineNumber(), row.getTestPublicKey(), row.getStudentSubject(), row.getStatus(), row.getMessage());
    }

    private StudentTestAttemptResponse toAttemptResponse(TestAttemptEntity attempt) {
        return toAttemptResponse(attempt, "SUBMITTED".equals(attempt.getStatus()));
    }

    private StudentTestAttemptResponse toAttemptResponse(TestAttemptEntity attempt, boolean reveal) {
        TestAttemptQuestionEntity current = attempt.getQuestions().stream()
                .min(Comparator.comparingInt(TestAttemptQuestionEntity::getQuestionOrder))
                .orElseThrow(() -> new IllegalStateException("Test attempt has no questions"));
        return new StudentTestAttemptResponse(
                attempt.getId(),
                attempt.getTestName(),
                attempt.getDifficulty(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getExpiresAt(),
                attempt.getSubmittedAt(),
                attempt.getQuestions().size(),
                reveal ? attempt.getScorePoints() : null,
                attempt.getMaxPoints(),
                attempt.getQuestions().stream()
                        .sorted(Comparator.comparingInt(TestAttemptQuestionEntity::getQuestionOrder))
                        .map(question -> new StudentTestNavigationItem(question.getId(), question.getQuestionOrder(),
                                question.getSubmittedAnswer() != null && !question.getSubmittedAnswer().isBlank(),
                                reveal ? question.getCorrect() : null))
                        .toList(),
                toQuestion(current, reveal),
                reveal ? attempt.getQuestions().stream()
                        .sorted(Comparator.comparingInt(TestAttemptQuestionEntity::getQuestionOrder))
                        .map(question -> toQuestion(question, true))
                        .toList() : List.of());
    }

    private StudentTestQuestion toQuestion(TestAttemptQuestionEntity attemptQuestion, boolean revealScore) {
        QuestionEntity question = attemptQuestion.getQuestion();
        return new StudentTestQuestion(
                attemptQuestion.getId(),
                attemptQuestion.getQuestionOrder(),
                question.getQuestionType(),
                question.getQuestionText(),
                question.getQuestionMediaObjectKey(),
                question.getQuestionMediaContentType(),
                question.getOptions().stream()
                        .sorted(Comparator.comparingInt(QuestionOptionEntity::getSortOrder))
                        .map(option -> new StudentQuestionOption(option.getOptionKey(), option.getOptionText(),
                                option.getOptionMediaObjectKey(), option.getOptionMediaContentType()))
                        .toList(),
                optionAnswer(question, attemptQuestion.getSubmittedAnswer()),
                textAnswer(question, attemptQuestion.getSubmittedAnswer()),
                revealScore ? correctOptionKeys(question) : List.of(),
                revealScore ? correctAnswerText(question) : "",
                revealScore ? correctAnswerMediaObjectKey(question) : null,
                revealScore ? correctAnswerMediaContentType(question) : null,
                revealScore ? attemptQuestion.getCorrect() : null);
    }

    private QuestionAdminRecord toQuestionAdminRecord(QuestionEntity question) {
        QuestionTaxonomyNodeEntity primary = question.getTaxonomyAssignments().stream()
                .filter(QuestionTaxonomyNodeEntity::isPrimary)
                .findFirst()
                .orElse(null);
        return new QuestionAdminRecord(
                question.getId(),
                primary == null ? null : primary.getTaxonomyNode().getId(),
                primary == null ? "" : primary.getTaxonomyNode().getDisplayName(),
                primary == null ? "" : primary.getTaxonomyNode().getStatus(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getWorkflowStatus(),
                question.getQuestionText(),
                question.getQuestionMediaObjectKey(),
                question.getQuestionMediaContentType(),
                question.getExplanation(),
                question.getSourceReference(),
                question.getLicenseCategory(),
                question.getOptions().stream().map(option -> new QuestionOption(option.getOptionKey(), option.getOptionText(),
                        option.getOptionMediaObjectKey(), option.getOptionMediaContentType(), option.isCorrect())).toList(),
                question.getTaxonomyAssignments().stream().map(assignment -> new QuestionTaxonomyAssignment(assignment.getTaxonomyNode().getId(), assignment.isPrimary())).toList(),
                question.getAnswers().stream().map(answer -> new QuestionAnswer(answer.getAnswerValue(), answer.getAnswerMediaObjectKey(),
                        answer.getAnswerMediaContentType(), answer.getAnswerType(), answer.getToleranceValue(), answer.getCaseSensitive())).toList(),
                question.getTags().stream().map(tag -> tag.getId().getTagCode()).toList());
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
        if (!usesOptions(question) || submittedAnswer == null || submittedAnswer.isBlank()) return List.of();
        return List.of(submittedAnswer.split(","));
    }

    private String textAnswer(QuestionEntity question, String submittedAnswer) {
        return usesOptions(question) ? "" : submittedAnswer;
    }

    private List<String> correctOptionKeys(QuestionEntity question) {
        if (!usesOptions(question)) return List.of();
        return question.getOptions().stream()
                .filter(QuestionOptionEntity::isCorrect)
                .map(QuestionOptionEntity::getOptionKey)
                .sorted()
                .toList();
    }

    private String correctAnswerText(QuestionEntity question) {
        if (usesOptions(question)) return "";
        return question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuestionAnswerEntity::getSortOrder))
                .map(QuestionAnswerEntity::getAnswerValue)
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    private String correctAnswerMediaObjectKey(QuestionEntity question) {
        if (usesOptions(question)) return null;
        return question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuestionAnswerEntity::getSortOrder))
                .map(QuestionAnswerEntity::getAnswerMediaObjectKey)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String correctAnswerMediaContentType(QuestionEntity question) {
        if (usesOptions(question)) return null;
        return question.getAnswers().stream()
                .sorted(Comparator.comparingInt(QuestionAnswerEntity::getSortOrder))
                .filter(answer -> answer.getAnswerMediaObjectKey() != null && !answer.getAnswerMediaObjectKey().isBlank())
                .map(QuestionAnswerEntity::getAnswerMediaContentType)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
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
        if ("NUMERICAL".equalsIgnoreCase(question.getQuestionType())) return numericalCorrect(question, submittedAnswer);
        return textCorrect(question, submittedAnswer);
    }

    private boolean numericalCorrect(QuestionEntity question, String submittedAnswer) {
        try {
            BigDecimal submitted = new BigDecimal(submittedAnswer.trim());
            for (QuestionAnswerEntity answer : question.getAnswers()) {
                if (answer.getAnswerValue() == null || answer.getAnswerValue().isBlank()) continue;
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
            if (answer.getAnswerValue() == null || answer.getAnswerValue().isBlank()) continue;
            boolean caseSensitive = Boolean.TRUE.equals(answer.getCaseSensitive());
            if (caseSensitive && submitted.equals(answer.getAnswerValue().trim())) return true;
            if (!caseSensitive && submitted.equalsIgnoreCase(answer.getAnswerValue().trim())) return true;
        }
        return false;
    }

    private boolean usesOptions(QuestionEntity question) {
        return Set.of("SINGLE_SELECT", "MULTIPLE_SELECT", "TRUE_FALSE").contains(question.getQuestionType().toUpperCase(Locale.ROOT));
    }

    private String value(CSVRecord record, String... names) {
        for (String name : names) {
            if (record.isMapped(name)) {
                return record.get(name);
            }
        }
        return "";
    }

    private String requireSubject(String value) {
        return requireText(value, "subject");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private record RowOutcome(String status) {}
}
