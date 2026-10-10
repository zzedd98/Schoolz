package ma.schoolz.erp.webhooks.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class WebhookSecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    WebhookSecretCipher(
            @Value("${schoolz.webhooks.master-key:schoolz-local-webhook-master-key-change-me}")
                    String masterKey) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible d'initialiser le coffre webhook", exception);
        }
    }

    String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de chiffrer le secret webhook", exception);
        }
    }

    String decrypt(String value) {
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            byte[] iv = Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Secret webhook illisible", exception);
        }
    }
}
