package ma.schoolz.erp.documents.application;

import java.util.UUID;

/** Port de stockage par clé d'objet, compatible avec un adaptateur S3 en production. */
public interface DocumentObjectStorage {
    String providerName();

    StoredObject put(
            UUID tenantId, String objectKey, String contentType, byte[] content, String sha256);

    ObjectContent get(UUID tenantId, String objectKey);

    record StoredObject(String objectKey, String contentType, long sizeBytes, String sha256) {}

    record ObjectContent(String contentType, byte[] content, String sha256) {}
}
