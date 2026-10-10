package ma.schoolz.erp.documents.application;

import java.time.Instant;
import java.util.UUID;

/** Extension point pour un futur fournisseur certifié. */
public interface DocumentSignatureProvider {
    String key();

    SignatureEvidence sign(SignatureRequest request);

    record SignatureRequest(
            UUID tenantId,
            UUID workflowId,
            UUID documentVersionId,
            String documentSha256,
            String signatoryName,
            String ipAddress,
            Instant acceptedAt) {}

    record SignatureEvidence(
            String providerReference,
            String assuranceLevel,
            String evidenceSha256,
            Instant signedAt) {}
}
