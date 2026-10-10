package ma.schoolz.erp.identity.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);

    Set<Role> findAllByTenantIdAndCodeIn(UUID tenantId, Collection<String> codes);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
