package com.clearleaf.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.clearleaf.api.repository.TenantUserMembershipRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {
    private final TenantUserMembershipRepository memberships;

    public MeController(TenantUserMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                realmRoles(jwt),
                clientRoles(jwt),
                tenantMemberships(jwt.getSubject()),
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
    }

    private List<TenantMembershipResponse> tenantMemberships(String subject) {
        return memberships.findByUserSubjectAndStatusOrderByTenant_TenantNameAsc(subject, "ACTIVE")
                .stream()
                .map(membership -> new TenantMembershipResponse(
                        membership.getTenant().getId(),
                        membership.getTenant().getTenantName(),
                        membership.getRole(),
                        membership.getStatus()))
                .toList();
    }

    private List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object rawRoles = realmAccess.get("roles");
        if (rawRoles instanceof List<?> list) {
            List<String> roles = new ArrayList<>();
            for (Object role : list) {
                if (role != null) roles.add(role.toString());
            }
            return roles;
        }
        return List.of();
    }

    private Map<String, List<String>> clientRoles(Jwt jwt) {
        Object resourceAccess = jwt.getClaims().get("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resources)) {
            return Map.of();
        }
        Map<String, List<String>> roles = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : resources.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> clientRoles)) {
                continue;
            }
            Object rawRoles = clientRoles.get("roles");
            List<String> values = new ArrayList<>();
            if (rawRoles instanceof List<?> list) {
                for (Object role : list) {
                    if (role != null) values.add(role.toString());
                }
            }
            roles.put(entry.getKey().toString(), values);
        }
        return roles;
    }
}
