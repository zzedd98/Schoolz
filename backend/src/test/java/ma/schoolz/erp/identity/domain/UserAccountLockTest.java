package ma.schoolz.erp.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountLockTest {

    @Test
    void locksAfterFiveFailuresAndClearsTheLockAfterASuccess() {
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        UserAccount account =
                UserAccount.create(
                        UUID.randomUUID(), "user@example.ma", "User", "a-hash", "fr-MA", true, now);

        for (int attempt = 0; attempt < 5; attempt++) {
            account.recordLoginFailure(now);
        }

        assertThat(account.locked(now.plusSeconds(1))).isTrue();
        assertThat(account.lockedUntil()).isEqualTo(now.plusSeconds(15 * 60));

        account.recordLoginSuccess(now.plusSeconds(16 * 60));
        assertThat(account.failedLoginAttempts()).isZero();
        assertThat(account.lockedUntil()).isNull();
    }
}
