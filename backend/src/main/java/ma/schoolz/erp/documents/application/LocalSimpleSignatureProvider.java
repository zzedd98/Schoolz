package ma.schoolz.erp.documents.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class LocalSimpleSignatureProvider implements DocumentSignatureProvider {
    private final Clock clock;

    public LocalSimpleSignatureProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String key() {
        return "local-simple";
    }

    @Override
    public SignatureEvidence sign(SignatureRequest request) {
        String canonical =
                request.tenantId()
                        + "|"
                        + request.workflowId()
                        + "|"
                        + request.documentVersionId()
                        + "|"
                        + request.documentSha256()
                        + "|"
                        + request.signatoryName()
                        + "|"
                        + request.ipAddress()
                        + "|"
                        + request.acceptedAt();
        String evidence = sha256(canonical);
        return new SignatureEvidence(
                "LOCAL-" + request.workflowId(),
                "SIMPLE_ELECTRONIC_ACCEPTANCE",
                evidence,
                clock.instant());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }
}
