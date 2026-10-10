package ma.schoolz.erp.foundation.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID required() {
        return current()
                .orElseThrow(() -> new IllegalStateException("No tenant is bound to this request"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
