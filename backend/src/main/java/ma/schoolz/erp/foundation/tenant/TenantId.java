package ma.schoolz.erp.foundation.tenant;

import java.util.Objects;
import java.util.UUID;

/** Stable, non-null identifier for an organization tenant. */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "Tenant id is required");
    }

    public static TenantId from(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
