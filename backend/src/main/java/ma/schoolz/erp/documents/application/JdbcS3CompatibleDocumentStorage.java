package ma.schoolz.erp.documents.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptateur durable de démonstration. Il conserve le contrat bucket/key d'un stockage S3 et peut
 * être remplacé par un adaptateur MinIO, AWS S3 ou compatible sans modifier le domaine DMS.
 */
@Component
@ConditionalOnProperty(
        name = "schoolz.storage.provider",
        havingValue = "jdbc",
        matchIfMissing = true)
public class JdbcS3CompatibleDocumentStorage implements DocumentObjectStorage {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcS3CompatibleDocumentStorage(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String providerName() {
        return "JDBC_LOCAL";
    }

    @Override
    public StoredObject put(
            UUID tenantId, String objectKey, String contentType, byte[] content, String sha256) {
        if (!objectKey.startsWith("tenants/" + tenantId + "/")) {
            throw new DocumentValidationException("La clé d'objet ne correspond pas au tenant");
        }
        jdbc.update(
                "INSERT INTO document_object_blob"
                        + " (object_key,tenant_id,content,content_type,size_bytes,sha256,stored_at)"
                        + " VALUES (?,?,?,?,?,?,?)",
                objectKey,
                tenantId,
                content,
                contentType,
                content.length,
                sha256,
                Timestamp.from(clock.instant()));
        return new StoredObject(objectKey, contentType, content.length, sha256);
    }

    @Override
    public ObjectContent get(UUID tenantId, String objectKey) {
        List<ObjectContent> values =
                jdbc.query(
                        "SELECT content_type,content,sha256 FROM document_object_blob"
                                + " WHERE tenant_id=? AND object_key=?",
                        (result, row) ->
                                new ObjectContent(
                                        result.getString("content_type"),
                                        result.getBytes("content"),
                                        result.getString("sha256")),
                        tenantId,
                        objectKey);
        if (values.isEmpty()) {
            throw new DocumentNotFoundException("Objet documentaire introuvable");
        }
        return values.getFirst();
    }
}
