package com.clearleaf.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/lookups")
public class CommonLookupController {
    private final CommonLookupService lookups;
    private final TenantAuthorizationService tenantAuthorization;

    public CommonLookupController(CommonLookupService lookups, TenantAuthorizationService tenantAuthorization) {
        this.lookups = lookups;
        this.tenantAuthorization = tenantAuthorization;
    }

    @GetMapping
    public Page<LookupResponse> lookups(
            @RequestParam("lookupType") String lookupType,
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = TenantAuthorizationService.TENANT_HEADER, required = false) String tenantHeader,
            @PageableDefault(sort = {"sortOrder", "lookupMeaning"}) Pageable pageable) {
        return lookups.list(tenantAuthorization.tenantId(tenantHeader), lookupType, status, pageable);
    }
}
