package ma.schoolz.erp.foundation.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class SecureTokenService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final IdentitySecurityProperties properties;

    public SecureTokenService(IdentitySecurityProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public String hash(String rawToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            properties.tokenPepper().getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash token", exception);
        }
    }
}
