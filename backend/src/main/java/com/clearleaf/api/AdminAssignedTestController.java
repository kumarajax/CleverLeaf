package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assigned-tests")
public class AdminAssignedTestController {
    private final AssignedTestService tests;

    public AdminAssignedTestController(AssignedTestService tests) {
        this.tests = tests;
    }

    @GetMapping
    public List<AdminAssignedTestSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return tests.adminTests(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAssignedTestDetail create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateAdminAssignedTestRequest request) {
        return tests.createAdminTest(jwt.getSubject(), request);
    }

    @GetMapping("/{versionId}")
    public AdminAssignedTestDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable("versionId") UUID versionId) {
        return tests.adminTest(jwt.getSubject(), versionId);
    }

    @PostMapping("/assignment-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AssignedTestImportJobResponse importAssignments(@AuthenticationPrincipal Jwt jwt, @RequestParam("objectKey") String objectKey) {
        return tests.startAssignmentImport(jwt.getSubject(), objectKey);
    }

    @GetMapping("/assignment-imports/{jobId}")
    public AssignedTestImportJobResponse importJob(@AuthenticationPrincipal Jwt jwt, @PathVariable("jobId") UUID jobId) {
        return tests.importJob(jwt.getSubject(), jobId);
    }

    @GetMapping("/assignment-imports/{jobId}/rows")
    public List<AssignedTestImportRowResponse> importRows(@AuthenticationPrincipal Jwt jwt, @PathVariable("jobId") UUID jobId) {
        return tests.importRows(jwt.getSubject(), jobId);
    }

    @GetMapping("/{versionId}/results")
    public List<AdminAssignedTestResult> results(@AuthenticationPrincipal Jwt jwt, @PathVariable("versionId") UUID versionId) {
        return tests.adminResults(jwt.getSubject(), versionId);
    }

    @GetMapping("/{versionId}/results/{assignmentId}")
    public AdminAssignedTestResult result(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @PathVariable("assignmentId") UUID assignmentId) {
        return tests.adminResult(jwt.getSubject(), versionId, assignmentId);
    }

    @PostMapping("/{versionId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAssignedTestResult assignStudent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @RequestBody AssignAdminTestRequest request) {
        return tests.assignStudent(jwt.getSubject(), versionId, request);
    }

    @PostMapping("/{versionId}/publish-results")
    public AdminAssignedTestSummary publishResults(@AuthenticationPrincipal Jwt jwt, @PathVariable("versionId") UUID versionId) {
        return tests.publishResults(jwt.getSubject(), versionId);
    }

    @PostMapping("/{versionId}/results/{assignmentId}/publish")
    public AdminAssignedTestResult publishStudentResult(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("versionId") UUID versionId,
            @PathVariable("assignmentId") UUID assignmentId) {
        return tests.publishStudentResult(jwt.getSubject(), versionId, assignmentId);
    }
}
