package com.clearleaf.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/admin/taxonomy")
public class TaxonomyController {
    private final TaxonomyService taxonomy;
    private final TenantAuthorizationService tenantAuthorization;

    public TaxonomyController(TaxonomyService taxonomy, TenantAuthorizationService tenantAuthorization) {
        this.taxonomy = taxonomy;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping("/nodes")
    public Page<TaxonomyNode> nodes(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "parentNodeId", required = false) UUID parentNodeId,
            @RequestParam(value = "includeDescendants", required = false, defaultValue = "false") boolean includeDescendants,
            @PageableDefault(sort = {"sortOrder", "displayName"}) Pageable pageable) {
        return taxonomy.listNodes(tenantAuthorization.tenantId(tenantHeader), status, parentNodeId, includeDescendants, pageable);
    }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyNode create(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @Valid @RequestBody CreateTaxonomyNodeRequest request) {
        return taxonomy.createNode(tenantAuthorization.tenantId(tenantHeader), request);
    }

    @PutMapping("/nodes/{id}")
    public TaxonomyNode update(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateTaxonomyNodeRequest request) {
        return taxonomy.updateNode(tenantAuthorization.tenantId(tenantHeader), id, request);
    }

    @PostMapping("/taxonomyVersions/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyCloneResponse cloneEdition(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @RequestBody CloneTaxonomyEditionRequest request) {
        return taxonomy.cloneEdition(tenantAuthorization.tenantId(tenantHeader), request);
    }

    @PostMapping("/taxonomyVersions/{id}/activate")
    public TaxonomyCloneResponse activateEdition(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id) {
        return taxonomy.activateEdition(tenantAuthorization.tenantId(tenantHeader), id);
    }

    @PostMapping("/nodes/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id) {
        taxonomy.deactivate(tenantAuthorization.tenantId(tenantHeader), id);
    }

    @DeleteMapping("/nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnused(
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PathVariable("id") UUID id) {
        taxonomy.deleteUnused(tenantAuthorization.tenantId(tenantHeader), id);
    }
}
