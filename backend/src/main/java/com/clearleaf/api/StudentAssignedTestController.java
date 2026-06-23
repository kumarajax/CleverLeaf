package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/assigned-tests")
public class StudentAssignedTestController {
    private final AssignedTestService tests;
    private final TenantAuthorizationService tenantAuthorization;

    public StudentAssignedTestController(AssignedTestService tests, TenantAuthorizationService tenantAuthorization) {
        this.tests = tests;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping
    public List<StudentAssignedTestSummary> assigned(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.studentAssigned(tenantAuthorization.tenantId(tenantHeader), studentIdentifiers(jwt), false);
    }

    @GetMapping("/results")
    public List<StudentAssignedTestSummary> results(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.studentAssigned(tenantAuthorization.tenantId(tenantHeader), studentIdentifiers(jwt), true);
    }

    @PostMapping("/{assignmentId}/start")
    public StudentTestAttemptResponse start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.startAssigned(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), studentIdentifiers(jwt), assignmentId);
    }

    @GetMapping("/{assignmentId}")
    public StudentTestAttemptResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.getAssignedAttempt(tenantAuthorization.tenantId(tenantHeader), studentIdentifiers(jwt), assignmentId);
    }

    @GetMapping("/{assignmentId}/questions/{attemptQuestionId}")
    public StudentTestQuestion question(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("assignmentId") UUID assignmentId,
            @PathVariable("attemptQuestionId") UUID attemptQuestionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.getAssignedQuestion(tenantAuthorization.tenantId(tenantHeader), studentIdentifiers(jwt), assignmentId, attemptQuestionId);
    }

    @PutMapping("/{assignmentId}/questions/{attemptQuestionId}/answer")
    public StudentTestQuestion answer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("assignmentId") UUID assignmentId,
            @PathVariable("attemptQuestionId") UUID attemptQuestionId,
            @RequestBody SubmitStudentAnswerRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.saveAssignedAnswer(tenantAuthorization.tenantId(tenantHeader), studentIdentifiers(jwt), assignmentId, attemptQuestionId, request);
    }

    @PostMapping("/{assignmentId}/submit")
    public StudentTestAttemptResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.submitAssigned(tenantAuthorization.tenantId(tenantHeader), studentIdentifiers(jwt), assignmentId);
    }

    private List<String> studentIdentifiers(Jwt jwt) {
        return java.util.stream.Stream.of(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
}
