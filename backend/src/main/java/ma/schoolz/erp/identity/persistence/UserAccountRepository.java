package ma.schoolz.erp.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByTenantIdAndNormalizedEmail(UUID tenantId, String normalizedEmail);

    Optional<UserAccount> findByIdAndTenantId(UUID id, UUID tenantId);

    List<UserAccount> findAllByTenantIdOrderByDisplayNameAsc(UUID tenantId);

    boolean existsByTenantIdAndNormalizedEmail(UUID tenantId, String normalizedEmail);
}
