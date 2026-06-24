package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/assigned-tests")
public class AdminAssignedTestController {
    private final AssignedTestService tests;
    private final TenantAuthorizationService tenantAuthorization;

    public AdminAssignedTestController(AssignedTestService tests, TenantAuthorizationService tenantAuthorization) {
        this.tests = tests;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping
    public List<AdminAssignedTestSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.adminTests(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAssignedTestDetail create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateAdminAssignedTestRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.createAdminTest(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), request);
    }

    @GetMapping("/{versionId}")
    public AdminAssignedTestDetail get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.adminTest(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId);
    }

    @PutMapping("/{versionId}/questions")
    public AdminAssignedTestDetail updateQuestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestBody UpdateAdminAssignedTestQuestionsRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.updateDraftQuestions(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId, request);
    }

    @PostMapping("/{versionId}/activate")
    public AdminAssignedTestSummary activate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.activateTest(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId);
    }

    @PostMapping("/{versionId}/expire")
    public AdminAssignedTestSummary expire(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.expireTest(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId);
    }

    @DeleteMapping("/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        tests.deleteTest(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId);
    }

    @PostMapping("/assignment-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AssignedTestImportJobResponse importAssignments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("objectKey") String objectKey,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.startAssignmentImport(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), objectKey);
    }

    @GetMapping("/assignment-imports/{jobId}")
    public AssignedTestImportJobResponse importJob(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("jobId") UUID jobId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.importJob(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), jobId);
    }

    @GetMapping("/assignment-imports/{jobId}/rows")
    public List<AssignedTestImportRowResponse> importRows(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("jobId") UUID jobId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.importRows(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), jobId);
    }

    @GetMapping("/{versionId}/results")
    public List<AdminAssignedTestResult> results(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.adminResults(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId);
    }

    @GetMapping("/{versionId}/results/{assignmentId}")
    public AdminAssignedTestResult result(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.adminResult(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId, assignmentId);
    }

    @PostMapping("/{versionId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAssignedTestResult assignStudent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestBody AssignAdminTestRequest request,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.assignStudent(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId, request);
    }

    @PostMapping("/{versionId}/publish-results")
    public AdminAssignedTestSummary publishResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.publishResults(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId);
    }

    @PostMapping("/{versionId}/results/{assignmentId}/publish")
    public AdminAssignedTestResult publishStudentResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.publishStudentResult(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId, assignmentId);
    }

    @PostMapping("/{versionId}/results/{assignmentId}/reassign")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAssignedTestResult reassignStudentTest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return tests.reassignStudentTest(tenantAuthorization.tenantId(tenantHeader), jwt.getSubject(), versionId, assignmentId);
    }
}
