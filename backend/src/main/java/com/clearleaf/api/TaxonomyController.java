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

@RestController
@RequestMapping("/api/admin/taxonomy")
public class TaxonomyController {
    private final TaxonomyService taxonomy;

    public TaxonomyController(TaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    @GetMapping("/nodes")
    public Page<TaxonomyNode> nodes(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "parentNodeId", required = false) UUID parentNodeId,
            @RequestParam(value = "includeDescendants", required = false, defaultValue = "false") boolean includeDescendants,
            @PageableDefault(sort = {"sortOrder", "displayName"}) Pageable pageable) {
        return taxonomy.listNodes(status, parentNodeId, includeDescendants, pageable);
    }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyNode create(@Valid @RequestBody CreateTaxonomyNodeRequest request) {
        return taxonomy.createNode(request);
    }

    @PutMapping("/nodes/{id}")
    public TaxonomyNode update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateTaxonomyNodeRequest request) {
        return taxonomy.updateNode(id, request);
    }

    @PostMapping("/taxonomyVersions/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxonomyCloneResponse cloneEdition(@RequestBody CloneTaxonomyEditionRequest request) {
        return taxonomy.cloneEdition(request);
    }

    @PostMapping("/taxonomyVersions/{id}/activate")
    public TaxonomyCloneResponse activateEdition(@PathVariable("id") UUID id) {
        return taxonomy.activateEdition(id);
    }

    @PostMapping("/nodes/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable("id") UUID id) {
        taxonomy.deactivate(id);
    }

    @DeleteMapping("/nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnused(@PathVariable("id") UUID id) {
        taxonomy.deleteUnused(id);
    }
}
