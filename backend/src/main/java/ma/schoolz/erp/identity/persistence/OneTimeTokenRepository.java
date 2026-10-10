package ma.schoolz.erp.identity.persistence;

import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.OneTimeToken;
import ma.schoolz.erp.identity.domain.TokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {
    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);
}
