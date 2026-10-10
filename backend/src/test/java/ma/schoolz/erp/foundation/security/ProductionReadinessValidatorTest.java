package ma.schoolz.erp.foundation.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProductionReadinessValidatorTest {
    private static final String JWT_SECRET = "a".repeat(64);
    private static final String TOKEN_PEPPER = "b".repeat(64);

    @Test
    void acceptsIndependentSecretsAndHttpsOrigins() {
        ProductionReadinessValidator.validateConfiguration(
                properties("https://schoolz.example.ma", JWT_SECRET, TOKEN_PEPPER, true),
                "https://schoolz.example.ma,https://admin.schoolz.example.ma",
                false,
                false);
    }

    @Test
    void refusesDemoFeaturesAndInsecureOrigins() {
        assertThatThrownBy(
                        () ->
                                ProductionReadinessValidator.validateConfiguration(
                                        properties(
                                                "https://schoolz.example.ma",
                                                JWT_SECRET,
                                                TOKEN_PEPPER,
                                                true),
                                        "http://localhost:8088",
                                        true,
                                        false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesDevelopmentSecrets() {
        assertThatThrownBy(
                        () ->
                                ProductionReadinessValidator.validateConfiguration(
                                        properties(
                                                "https://schoolz.example.ma",
                                                "schoolz-local-jwt-signing-key-change-before-deploy",
                                                TOKEN_PEPPER,
                                                true),
                                        "https://schoolz.example.ma",
                                        false,
                                        false))
                .isInstanceOf(IllegalStateException.class);
    }

    private static IdentitySecurityProperties properties(
            String issuer, String jwtSecret, String tokenPepper, boolean secureCookies) {
        return new IdentitySecurityProperties(
                issuer,
                jwtSecret,
                tokenPepper,
                Duration.ofMinutes(10),
                Duration.ofDays(14),
                Duration.ofMinutes(30),
                Duration.ofHours(24),
                "schoolz_refresh",
                secureCookies);
    }
}
