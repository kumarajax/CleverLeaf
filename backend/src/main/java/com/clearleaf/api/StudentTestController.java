package com.clearleaf.api;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/tests")
public class StudentTestController {
    private final StudentTestService tests;
    private final TenantAuthorizationService tenantAuthorization;

    public StudentTestController(StudentTestService tests, TenantAuthorizationService tenantAuthorization) {
        this.tests = tests;
        this.tenantAuthorization = tenantAuthorization;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentTestAttemptResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateStudentTestRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.createAttempt(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), request);
    }

    @GetMapping("/history")
    public Page<StudentTestAttemptSummary> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "taxonomy", required = false) String taxonomy,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.history(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), page, size, dateFrom, dateTo, taxonomy);
    }

    @GetMapping("/{attemptId}")
    public StudentTestAttemptResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("attemptId") UUID attemptId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.getAttempt(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), attemptId);
    }

    @GetMapping("/{attemptId}/questions/{attemptQuestionId}")
    public StudentTestQuestion question(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("attemptId") UUID attemptId,
            @PathVariable("attemptQuestionId") UUID attemptQuestionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.getQuestion(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), attemptId, attemptQuestionId);
    }

    @PutMapping("/{attemptId}/questions/{attemptQuestionId}/answer")
    public StudentTestQuestion answer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("attemptId") UUID attemptId,
            @PathVariable("attemptQuestionId") UUID attemptQuestionId,
            @RequestBody SubmitStudentAnswerRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.saveAnswer(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), attemptId, attemptQuestionId, request);
    }

    @PostMapping("/{attemptId}/questions/{attemptQuestionId}/submit")
    public StudentTestQuestion submitQuestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("attemptId") UUID attemptId,
            @PathVariable("attemptQuestionId") UUID attemptQuestionId,
            @RequestBody SubmitStudentAnswerRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.submitQuestion(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), attemptId, attemptQuestionId, request);
    }

    @PostMapping("/{attemptId}/submit")
    public StudentTestAttemptResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("attemptId") UUID attemptId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.submit(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), attemptId);
    }
}
