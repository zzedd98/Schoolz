package ma.schoolz.erp.foundation.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "schoolz.security")
public record IdentitySecurityProperties(
        @NotBlank String jwtIssuer,
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotBlank @Size(min = 32) String tokenPepper,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        Duration emailVerificationTtl,
        @NotBlank String refreshCookieName,
        boolean secureCookies) {}
