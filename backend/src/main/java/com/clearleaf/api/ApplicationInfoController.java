package com.clearleaf.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/application")
public class ApplicationInfoController {
    private final String applicationCode;
    private final String applicationName;

    public ApplicationInfoController(
            @Value("${app.identity.application-code}") String applicationCode,
            @Value("${app.identity.application-name}") String applicationName) {
        this.applicationCode = applicationCode;
        this.applicationName = applicationName;
    }

    @GetMapping
    public ApplicationInfo get() {
        return new ApplicationInfo(applicationCode, applicationName);
    }
}
