package ma.schoolz.erp.foundation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private final IdentitySecurityProperties properties =
            new IdentitySecurityProperties(
                    "schoolz-test",
                    "a-test-signing-key-with-more-than-thirty-two-characters",
                    "a-test-token-pepper-with-more-than-thirty-two-characters",
                    Duration.ofMinutes(10),
                    Duration.ofDays(14),
                    Duration.ofMinutes(30),
                    Duration.ofHours(24),
                    "schoolz_refresh",
                    false);
    private final JwtService service = new JwtService(new ObjectMapper(), clock, properties);

    @Test
    void issuesAndVerifiesTenantAndPermissions() {
        SchoolPrincipal principal =
                new SchoolPrincipal(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "admin@schoolz.ma",
                        "Admin",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        Set.of("user.read"));

        SchoolPrincipal decoded = service.verify(service.issue(principal).value());

        assertThat(decoded).isEqualTo(principal);
    }

    @Test
    void rejectsATamperedToken() {
        SchoolPrincipal principal =
                new SchoolPrincipal(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "admin@schoolz.ma",
                        "Admin",
                        "fr-MA",
                        Set.of(),
                        Set.of());
        String token = service.issue(principal).value();

        assertThatThrownBy(() -> service.verify(token.substring(0, token.length() - 2) + "aa"))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void rejectsOversizedAndUnsignedJwtVariants() {
        assertThatThrownBy(() -> service.verify("a".repeat(8193)))
                .isInstanceOf(InvalidAccessTokenException.class);
        assertThatThrownBy(
                        () ->
                                service.verify(
                                        "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0."
                                                + "eyJpc3MiOiJzY2hvb2x6LXRlc3QifQ."))
                .isInstanceOf(InvalidAccessTokenException.class);
    }
}
