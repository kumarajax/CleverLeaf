package com.clearleaf.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-question-generation")
public class AiQuestionGenerationController {
    private final AiQuestionGenerationService service;
    private final AiProviderConnectionService connections;
    private final TenantAuthorizationService tenantAuthorization;

    public AiQuestionGenerationController(
            AiQuestionGenerationService service,
            AiProviderConnectionService connections,
            TenantAuthorizationService tenantAuthorization) {
        this.service = service;
        this.connections = connections;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping("/connection")
    public AiConnectionResponse connection(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return connections.getConnection(tenantAuthorization.tenantId(tenantHeader));
    }

    @PostMapping("/connection/verify")
    public AiConnectionResponse verifyConnection(@RequestBody AiConnectionRequest request) {
        return connections.verifyConnection(request);
    }

    @PutMapping("/connection")
    public AiConnectionResponse saveConnection(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @RequestBody AiConnectionRequest request) {
        return connections.saveConnection(tenantAuthorization.tenantId(tenantHeader), request);
    }

    @GetMapping("/jobs")
    public List<AiGenerationJobResponse> jobs(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader) {
        return service.listJobs(tenantAuthorization.tenantId(tenantHeader));
    }

    @GetMapping("/jobs/{id}")
    public AiGenerationJobResponse job(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id) {
        return service.getJob(tenantAuthorization.tenantId(tenantHeader), id);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public AiGenerationJobResponse createJob(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @RequestBody AiGenerationJobRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.createJob(tenantAuthorization.tenantId(tenantHeader), request, actor(jwt));
    }

    @PostMapping("/jobs/{id}/generate")
    public AiGenerationJobResponse generate(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) AiGenerationJobRequest request) {
        return service.generate(tenantAuthorization.tenantId(tenantHeader), id, request);
    }

    @PostMapping("/jobs/{id}/bulk-approve")
    public AiGenerationJobResponse bulkApprove(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.bulkApprove(tenantAuthorization.tenantId(tenantHeader), id, actor(jwt));
    }

    @PutMapping("/generated-questions/{id}")
    public AiGeneratedQuestionResponse updateQuestion(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @RequestBody UpdateAiGeneratedQuestionRequest request) {
        return service.updateGeneratedQuestion(tenantAuthorization.tenantId(tenantHeader), id, request);
    }

    @PostMapping("/generated-questions/{id}/approve")
    public AiGeneratedQuestionResponse approve(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return service.approve(tenantAuthorization.tenantId(tenantHeader), id, actor(jwt));
    }

    @PostMapping("/generated-questions/{id}/reject")
    public AiGeneratedQuestionResponse reject(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) AiRejectRequest request) {
        return service.reject(tenantAuthorization.tenantId(tenantHeader), id, request);
    }

    private String actor(Jwt jwt) {
        if (jwt == null) return "ai-generation";
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) return preferredUsername;
        return jwt.getSubject();
    }
}
