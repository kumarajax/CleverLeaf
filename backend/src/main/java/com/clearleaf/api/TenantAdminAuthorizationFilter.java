package com.clearleaf.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantAdminAuthorizationFilter extends OncePerRequestFilter {
    private final TenantAuthorizationService tenantAuthorization;
    private final ObjectMapper objectMapper;

    public TenantAdminAuthorizationFilter(TenantAuthorizationService tenantAuthorization, ObjectMapper objectMapper) {
        this.tenantAuthorization = tenantAuthorization;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID tenantId;
        try {
            tenantId = tenantAuthorization.tenantId(request.getHeader(TenantAuthorizationService.TENANT_HEADER));
        } catch (IllegalArgumentException ex) {
            forbidden(response, "Invalid tenant id");
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean tenantScopedAdminEndpoint = isTenantScopedAdminEndpoint(request.getRequestURI());
        boolean allowed = tenantScopedAdminEndpoint
                ? tenantAuthorization.canUseAdminApi(authentication, tenantId)
                : tenantAuthorization.canUsePlatformAdminApi(authentication);
        if (!allowed) {
            forbidden(response, "Tenant admin access is required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTenantScopedAdminEndpoint(String uri) {
        return uri.startsWith("/api/admin/taxonomy/")
                || uri.startsWith("/api/admin/questions")
                || uri.startsWith("/api/admin/assigned-tests")
                || uri.startsWith("/api/admin/ai-question-generation")
                || uri.startsWith("/api/admin/imports/")
                || uri.startsWith("/api/admin/media/")
                || uri.startsWith("/api/admin/tenant/");
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}
