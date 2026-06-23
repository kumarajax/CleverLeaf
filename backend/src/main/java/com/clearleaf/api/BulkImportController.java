package com.clearleaf.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/imports/bulk")
public class BulkImportController {
    private final BulkImportService imports;
    private final TenantAuthorizationService tenantAuthorization;

    public BulkImportController(BulkImportService imports, TenantAuthorizationService tenantAuthorization) {
        this.imports = imports;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping("/metadata")
    public List<BulkImportStepMetadata> metadata() {
        return imports.metadata();
    }

    @GetMapping("/{step}/preview")
    public BulkImportPreviewResponse preview(
            @PathVariable("step") BulkImportStep step,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @RequestParam("objectKey") String objectKey) {
        return imports.preview(tenantAuthorization.tenantId(tenantHeader), step, objectKey);
    }

    @PostMapping("/{step}")
    @ResponseStatus(HttpStatus.CREATED)
    public BulkImportSummary importStep(
            @PathVariable("step") BulkImportStep step,
            @RequestParam("objectKey") String objectKey,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt) {
        return imports.importStep(tenantAuthorization.tenantId(tenantHeader), step, objectKey, actor(jwt));
    }

    private String actor(Jwt jwt) {
        if (jwt == null) return "bulk-import";
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) return email;
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) return preferredUsername;
        return jwt.getSubject();
    }
}
