package ma.schoolz.erp.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.IdentitySecurityProperties;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.application.AuthenticationFailedException;
import ma.schoolz.erp.identity.application.AuthenticationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authentication;
    private final IdentitySecurityProperties properties;
    private final boolean exposeDevelopmentTokens;

    public AuthenticationController(
            AuthenticationService authentication,
            IdentitySecurityProperties properties,
            @Value("${schoolz.demo.expose-tokens:false}") boolean exposeDevelopmentTokens) {
        this.authentication = authentication;
        this.properties = properties;
        this.exposeDevelopmentTokens = exposeDevelopmentTokens;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthenticationService.AuthenticationResult result =
                authentication.login(
                        request.tenantSlug(),
                        request.email(),
                        request.password(),
                        clientContext(servletRequest));
        return authenticatedResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @CookieValue(
                            name = "${schoolz.security.refresh-cookie-name:schoolz_refresh}",
                            required = false)
                    String refreshToken,
            HttpServletRequest servletRequest) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationFailedException();
        }
        return authenticatedResponse(
                authentication.refresh(refreshToken, clientContext(servletRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(
                            name = "${schoolz.security.refresh-cookie-name:schoolz_refresh}",
                            required = false)
                    String refreshToken,
            HttpServletRequest servletRequest) {
        authentication.logout(refreshToken, clientContext(servletRequest));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<TokenRequestedResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        Optional<String> token =
                authentication.requestPasswordReset(request.tenantSlug(), request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                        new TokenRequestedResponse(
                                "If the account exists, reset instructions have been created.",
                                exposeDevelopmentTokens ? token.orElse(null) : null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authentication.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activate-account")
    public ResponseEntity<Void> activateAccount(@Valid @RequestBody ResetPasswordRequest request) {
        authentication.activateAccount(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authentication.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal SchoolPrincipal principal) {
        return MeResponse.from(principal);
    }

    private ResponseEntity<AuthenticationResponse> authenticatedResponse(
            AuthenticationService.AuthenticationResult result) {
        ResponseCookie cookie =
                ResponseCookie.from(properties.refreshCookieName(), result.refreshToken())
                        .httpOnly(true)
                        .secure(properties.secureCookies())
                        .sameSite("Strict")
                        .path("/api/v1/auth")
                        .maxAge(Duration.between(Instant.now(), result.refreshExpiresAt()))
                        .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(
                        new AuthenticationResponse(
                                result.accessToken(),
                                result.accessExpiresAt(),
                                MeResponse.from(result.principal())));
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(properties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private static AuthenticationService.ClientContext clientContext(HttpServletRequest request) {
        return new AuthenticationService.ClientContext(
                truncate(request.getRemoteAddr(), 64),
                truncate(request.getHeader(HttpHeaders.USER_AGENT), 500));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record LoginRequest(
            @NotBlank String tenantSlug,
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record ForgotPasswordRequest(
            @NotBlank String tenantSlug, @NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token, @NotBlank @Size(min = 12, max = 200) String newPassword) {}

    public record VerifyEmailRequest(@NotBlank String token) {}

    public record AuthenticationResponse(
            String accessToken, Instant accessExpiresAt, MeResponse user) {}

    public record TokenRequestedResponse(String message, String developmentToken) {}

    public record MeResponse(
            UUID id,
            UUID tenantId,
            String email,
            String displayName,
            String locale,
            Set<String> roles,
            Set<String> permissions) {
        static MeResponse from(SchoolPrincipal principal) {
            return new MeResponse(
                    principal.accountId(),
                    principal.tenantId(),
                    principal.email(),
                    principal.displayName(),
                    principal.locale(),
                    principal.roles(),
                    principal.permissions());
        }
    }
}
