package ma.schoolz.erp.provisioning.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningAccessService {

    private final byte[] expectedKey;

    public ProvisioningAccessService(@Value("${schoolz.provisioning.key:}") String key) {
        this.expectedKey = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
    }

    public boolean authorized(String candidate) {
        if (expectedKey.length < 32 || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedKey, candidate.getBytes(StandardCharsets.UTF_8));
    }
}
