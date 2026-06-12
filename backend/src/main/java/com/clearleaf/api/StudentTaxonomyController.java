package com.clearleaf.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/student/taxonomy")
public class StudentTaxonomyController {
    private final TaxonomyService taxonomy;
    private final TenantAuthorizationService tenantAuthorization;

    public StudentTaxonomyController(TaxonomyService taxonomy, TenantAuthorizationService tenantAuthorization) {
        this.taxonomy = taxonomy;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping("/search")
    public List<StudentTaxonomyNode> search(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @RequestParam(value = "query", required = false) String query) {
        return taxonomy.searchActiveStudentTaxonomy(tenantAuthorization.tenantId(tenantHeader), query);
    }
}
