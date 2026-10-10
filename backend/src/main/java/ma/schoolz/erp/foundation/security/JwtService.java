package ma.schoolz.erp.foundation.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final byte[] HEADER =
            ENCODER.encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final IdentitySecurityProperties properties;

    public JwtService(
            ObjectMapper objectMapper, Clock clock, IdentitySecurityProperties properties) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.properties = properties;
    }

    public IssuedAccessToken issue(SchoolPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.jwtIssuer());
        claims.put("sub", principal.accountId().toString());
        claims.put("tid", principal.tenantId().toString());
        claims.put("email", principal.email());
        claims.put("name", principal.displayName());
        claims.put("locale", principal.locale());
        claims.put("roles", principal.roles());
        claims.put("permissions", principal.permissions());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        try {
            byte[] payload = ENCODER.encode(objectMapper.writeValueAsBytes(claims));
            String signingInput =
                    new String(HEADER, StandardCharsets.US_ASCII)
                            + "."
                            + new String(payload, StandardCharsets.US_ASCII);
            String signature = ENCODER.encodeToString(sign(signingInput));
            return new IssuedAccessToken(signingInput + "." + signature, expiresAt);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token", exception);
        }
    }

    public SchoolPrincipal verify(String token) {
        try {
            if (token == null || token.length() > 8192) {
                throw new InvalidAccessTokenException();
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new InvalidAccessTokenException();
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] expected = sign(signingInput);
            byte[] provided = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, provided)
                    || !MessageDigest.isEqual(
                            HEADER, parts[0].getBytes(StandardCharsets.US_ASCII))) {
                throw new InvalidAccessTokenException();
            }

            Map<String, Object> claims =
                    objectMapper.readValue(
                            DECODER.decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            long now = clock.instant().getEpochSecond();
            long issuedAt = ((Number) claims.get("iat")).longValue();
            long expiresAt = ((Number) claims.get("exp")).longValue();
            UUID.fromString((String) claims.get("jti"));
            if (!properties.jwtIssuer().equals(claims.get("iss"))
                    || expiresAt <= now
                    || issuedAt > now + 60
                    || expiresAt <= issuedAt
                    || expiresAt - issuedAt > properties.accessTokenTtl().toSeconds() + 60) {
                throw new InvalidAccessTokenException();
            }
            return new SchoolPrincipal(
                    UUID.fromString((String) claims.get("sub")),
                    UUID.fromString((String) claims.get("tid")),
                    (String) claims.get("email"),
                    (String) claims.get("name"),
                    (String) claims.get("locale"),
                    stringSet(claims.get("roles")),
                    stringSet(claims.get("permissions")));
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidAccessTokenException();
        }
    }

    private byte[] sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(
                        properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return Set.of();
        }
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {}
}
