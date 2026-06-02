package com.clearleaf.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/lookups")
public class CommonLookupController {
    private final CommonLookupService lookups;

    public CommonLookupController(CommonLookupService lookups) {
        this.lookups = lookups;
    }

    @GetMapping
    public Page<LookupResponse> lookups(
            @RequestParam("lookupType") String lookupType,
            @RequestParam(value = "status", required = false) String status,
            @PageableDefault(sort = {"sortOrder", "lookupMeaning"}) Pageable pageable) {
        return lookups.list(lookupType, status, pageable);
    }
}
