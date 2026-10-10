package ma.schoolz.erp.foundation.security;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionReadinessValidator {
    private final IdentitySecurityProperties security;
    private final String allowedOrigins;
    private final boolean demoSeedEnabled;
    private final boolean developmentTokensExposed;
    private final String provisioningKey;
    private final String publicBaseUrl;

    public ProductionReadinessValidator(
            IdentitySecurityProperties security,
            @Value("${schoolz.security.allowed-origins}") String allowedOrigins,
            @Value("${schoolz.demo.seed-enabled:false}") boolean demoSeedEnabled,
            @Value("${schoolz.demo.expose-tokens:false}") boolean developmentTokensExposed,
            @Value("${schoolz.provisioning.key:}") String provisioningKey,
            @Value("${schoolz.provisioning.public-base-url:}") String publicBaseUrl) {
        this.security = security;
        this.allowedOrigins = allowedOrigins;
        this.demoSeedEnabled = demoSeedEnabled;
        this.developmentTokensExposed = developmentTokensExposed;
        this.provisioningKey = provisioningKey;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostConstruct
    void validate() {
        validateConfiguration(security, allowedOrigins, demoSeedEnabled, developmentTokensExposed);
        if (provisioningKey == null || provisioningKey.length() < 64) {
            throw new IllegalStateException(
                    "SCHOOLZ_PROVISIONING_KEY must contain at least 64 characters");
        }
        rejectPlaceholder("SCHOOLZ_PROVISIONING_KEY", provisioningKey);
        if (provisioningKey.equals(security.jwtSecret())
                || provisioningKey.equals(security.tokenPepper())) {
            throw new IllegalStateException(
                    "Provisioning key must be independent from authentication secrets");
        }
        URI publicUrl = parseUri("SCHOOLZ_PUBLIC_BASE_URL", publicBaseUrl);
        if (!"https".equalsIgnoreCase(publicUrl.getScheme()) || publicUrl.getHost() == null) {
            throw new IllegalStateException("Production public base URL must be an HTTPS URL");
        }
    }

    static void validateConfiguration(
            IdentitySecurityProperties security,
            String allowedOrigins,
            boolean demoSeedEnabled,
            boolean developmentTokensExposed) {
        if (!security.secureCookies()) {
            throw new IllegalStateException("Production requires secure authentication cookies");
        }
        if (demoSeedEnabled || developmentTokensExposed) {
            throw new IllegalStateException("Demo data and development tokens must be disabled");
        }
        rejectPlaceholder("AUTH_JWT_SECRET", security.jwtSecret());
        rejectPlaceholder("AUTH_TOKEN_PEPPER", security.tokenPepper());
        if (security.jwtSecret().equals(security.tokenPepper())) {
            throw new IllegalStateException("JWT secret and token pepper must be independent");
        }
        URI issuer = parseUri("AUTH_JWT_ISSUER", security.jwtIssuer());
        if (!"https".equalsIgnoreCase(issuer.getScheme())) {
            throw new IllegalStateException("Production JWT issuer must use HTTPS");
        }
        String[] origins =
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toArray(String[]::new);
        if (origins.length == 0) {
            throw new IllegalStateException("At least one production origin is required");
        }
        for (String origin : origins) {
            URI uri = parseUri("ALLOWED_ORIGINS", origin);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalStateException("Production origins must be absolute HTTPS URLs");
            }
        }
    }

    private static void rejectPlaceholder(String name, String value) {
        String normalized = value == null ? "" : value.toLowerCase();
        if (normalized.contains("change_me")
                || normalized.contains("change-before")
                || normalized.contains("schoolz-local")) {
            throw new IllegalStateException(name + " still contains a development placeholder");
        }
    }

    private static URI parseUri(String name, String value) {
        try {
            return URI.create(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(name + " is not a valid URI", exception);
        }
    }
}
