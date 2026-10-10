package ma.schoolz.erp.identity.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.schoolz.erp.identity.domain.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query(
            """
            update RefreshSession session
               set session.revokedAt = :now
             where session.familyId = :familyId
               and session.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query(
            """
            update RefreshSession session
               set session.revokedAt = :now
             where session.userId = :userId
               and session.tenantId = :tenantId
               and session.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("now") Instant now);
}
