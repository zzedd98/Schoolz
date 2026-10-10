package ma.schoolz.erp.foundation.security;

import java.util.Set;
import java.util.UUID;

public record SchoolPrincipal(
        UUID accountId,
        UUID tenantId,
        String email,
        String displayName,
        String locale,
        Set<String> roles,
        Set<String> permissions) {}
