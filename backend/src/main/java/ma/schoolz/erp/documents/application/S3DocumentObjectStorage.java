package ma.schoolz.erp.documents.application;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "schoolz.storage.provider", havingValue = "s3")
public class S3DocumentObjectStorage implements DocumentObjectStorage {
    private final MinioClient client;
    private final String bucket;
    private final boolean autoCreateBucket;

    public S3DocumentObjectStorage(
            @Value("${schoolz.storage.s3.endpoint}") String endpoint,
            @Value("${schoolz.storage.s3.access-key}") String accessKey,
            @Value("${schoolz.storage.s3.secret-key}") String secretKey,
            @Value("${schoolz.storage.s3.bucket}") String bucket,
            @Value("${schoolz.storage.s3.auto-create-bucket:false}") boolean autoCreateBucket) {
        this.client =
                MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.autoCreateBucket = autoCreateBucket;
    }

    @PostConstruct
    void verifyBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists && autoCreateBucket) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                return;
            }
            if (!exists) {
                throw new IllegalStateException("Configured document bucket does not exist");
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Document object storage is unavailable", exception);
        }
    }

    @Override
    public String providerName() {
        return "S3_COMPATIBLE";
    }

    @Override
    public StoredObject put(
            UUID tenantId, String objectKey, String contentType, byte[] content, String sha256) {
        requireTenantKey(tenantId, objectKey);
        try {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .contentType(contentType)
                            .userMetadata(
                                    Map.of("sha256", sha256, "tenant-id", tenantId.toString()))
                            .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                            .build());
            return new StoredObject(objectKey, contentType, content.length, sha256);
        } catch (Exception exception) {
            throw new DocumentStorageException("Le document ne peut pas être stocké", exception);
        }
    }

    @Override
    public ObjectContent get(UUID tenantId, String objectKey) {
        requireTenantKey(tenantId, objectKey);
        try {
            StatObjectResponse metadata =
                    client.statObject(
                            StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            byte[] content;
            try (GetObjectResponse response =
                    client.getObject(
                            GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                content = response.readAllBytes();
            }
            Set<String> checksumValues = metadata.userMetadata().get("sha256");
            String expectedSha256 =
                    checksumValues == null
                            ? null
                            : checksumValues.stream().findFirst().orElse(null);
            String actualSha256 = sha256(content);
            if (expectedSha256 == null || !expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new DocumentStorageException(
                        "L'intégrité du document stocké ne peut pas être vérifiée", null);
            }
            return new ObjectContent(metadata.contentType(), content, actualSha256);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new DocumentNotFoundException("Objet documentaire introuvable");
            }
            throw new DocumentStorageException("Le document ne peut pas être lu", exception);
        } catch (DocumentStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentStorageException("Le document ne peut pas être lu", exception);
        }
    }

    private static void requireTenantKey(UUID tenantId, String objectKey) {
        if (!objectKey.startsWith("tenants/" + tenantId + "/")) {
            throw new DocumentValidationException("La clé d'objet ne correspond pas au tenant");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
