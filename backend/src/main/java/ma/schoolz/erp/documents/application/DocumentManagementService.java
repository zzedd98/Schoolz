package ma.schoolz.erp.documents.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.foundation.security.SecureTokenService;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentManagementService {
    public static final Set<String> DOCUMENT_TYPES =
            Set.of(
                    "PARENT_ID",
                    "BIRTH_CERTIFICATE",
                    "PHOTO",
                    "CERTIFICATE",
                    "CONTRACT",
                    "AUTHORIZATION",
                    "REPORT_CARD",
                    "INVOICE",
                    "ATTESTATION",
                    "INTERNAL");
    public static final Set<String> PDF_TEMPLATES =
            Set.of(
                    "SCHOOL_CERTIFICATE",
                    "RECEIPT",
                    "INVOICE",
                    "CERTIFICATE",
                    "REPORT_CARD",
                    "CONTRACT");
    private static final Set<String> OWNER_KINDS =
            Set.of("SCHOOL", "STUDENT", "PARENT", "STAFF", "FAMILY_ACCOUNT");
    private static final Set<String> VISIBILITIES = Set.of("INTERNAL", "FAMILY", "PRIVATE");
    private static final Set<String> CONTENT_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png");
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;
    private final SecureTokenService tokens;
    private final DocumentObjectStorage storage;
    private final DocumentPdfRenderer pdf;

    public DocumentManagementService(
            JdbcTemplate jdbc,
            Clock clock,
            IdentityAuditTrail audit,
            SecureTokenService tokens,
            DocumentObjectStorage storage,
            DocumentPdfRenderer pdf) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
        this.tokens = tokens;
        this.storage = storage;
        this.pdf = pdf;
    }

    @Transactional(readOnly = true)
    public DocumentDashboard dashboard(SchoolPrincipal actor) {
        require(actor, "document.read");
        return new DocumentDashboard(
                clock.instant(),
                DOCUMENT_TYPES.stream().sorted().toList(),
                PDF_TEMPLATES.stream().sorted().toList(),
                categories(actor.tenantId()),
                documents(actor.tenantId(), null),
                parents(actor.tenantId()),
                storageDescriptor());
    }

    @Transactional(readOnly = true)
    public FamilyDocumentDashboard familyDashboard(PortalIdentity parent) {
        return new FamilyDocumentDashboard(
                clock.instant(),
                parent.displayName(),
                documents(parent.tenantId(), parent.accountId()),
                storageDescriptor());
    }

    @Transactional
    public CategoryView createCategory(SchoolPrincipal actor, CategoryCommand command, String ip) {
        require(actor, "document.write");
        String code = code(command.code());
        String labelFr = required(command.labelFr(), "Libellé français obligatoire", 180);
        String labelAr = optional(command.labelAr(), 180);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO document_category"
                        + " (id,tenant_id,code,label_fr,label_ar,system_category,active,created_at)"
                        + " VALUES (?,?,?,?,?,FALSE,TRUE,?)",
                    id,
                    actor.tenantId(),
                    code,
                    labelFr,
                    labelAr,
                    timestamp(clock.instant()));
        } catch (RuntimeException exception) {
            throw new DocumentValidationException("Cette catégorie existe déjà");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "document.category.create",
                "DocumentCategory",
                id.toString(),
                ip,
                Map.of("code", code),
                null,
                Map.of("labelFr", labelFr));
        return category(actor.tenantId(), id);
    }

    @Transactional
    public DocumentView upload(
            SchoolPrincipal actor,
            UploadMetadata metadata,
            String originalFileName,
            String contentType,
            byte[] content,
            String ip) {
        require(actor, "document.write");
        validateContent(originalFileName, contentType, content);
        UUID documentId = UUID.randomUUID();
        insertDocument(actor, documentId, metadata);
        insertVersion(
                actor.tenantId(),
                actor.accountId(),
                documentId,
                1,
                originalFileName,
                contentType,
                content,
                "UPLOAD",
                null);
        auditDocument(actor, documentId, "document.upload", ip, Map.of("version", 1));
        return document(actor.tenantId(), documentId, null);
    }

    @Transactional
    public DocumentView addVersion(
            SchoolPrincipal actor,
            UUID documentId,
            String originalFileName,
            String contentType,
            byte[] content,
            String ip) {
        require(actor, "document.write");
        validateContent(originalFileName, contentType, content);
        int version = lockAndNextVersion(actor.tenantId(), documentId);
        insertVersion(
                actor.tenantId(),
                actor.accountId(),
                documentId,
                version,
                originalFileName,
                contentType,
                content,
                "UPLOAD",
                null);
        auditDocument(actor, documentId, "document.version.create", ip, Map.of("version", version));
        return document(actor.tenantId(), documentId, null);
    }

    @Transactional
    public DocumentView generatePdf(SchoolPrincipal actor, GeneratePdfCommand command, String ip) {
        require(actor, "document.generate");
        String template = normalize(command.template());
        if (!PDF_TEMPLATES.contains(template)) {
            throw new DocumentValidationException("Modèle PDF invalide");
        }
        UploadMetadata metadata =
                new UploadMetadata(
                        command.categoryId(),
                        documentTypeForTemplate(template),
                        command.title(),
                        command.description(),
                        command.ownerKind(),
                        command.ownerReferenceId(),
                        command.visibility(),
                        command.expiresOn());
        UUID documentId = UUID.randomUUID();
        insertDocument(actor, documentId, metadata);
        byte[] content =
                pdf.render(
                        template,
                        required(command.title(), "Titre obligatoire", 240),
                        required(command.recipient(), "Destinataire obligatoire", 240),
                        optional(command.reference(), 180),
                        command.fields() == null ? Map.of() : command.fields());
        String fileName = fileName(template + "-" + command.recipient()) + ".pdf";
        insertVersion(
                actor.tenantId(),
                actor.accountId(),
                documentId,
                1,
                fileName,
                "application/pdf",
                content,
                "GENERATED",
                template);
        auditDocument(
                actor,
                documentId,
                "document.pdf.generate",
                ip,
                Map.of("template", template, "version", 1));
        return document(actor.tenantId(), documentId, null);
    }

    @Transactional
    public DocumentView shareWithFamily(
            SchoolPrincipal actor, UUID documentId, ShareCommand command, String ip) {
        require(actor, "document.share");
        UUID parentId = required(command.portalAccountId(), "Famille obligatoire");
        ensureParent(actor.tenantId(), parentId);
        ensureDocument(actor.tenantId(), documentId);
        Instant validUntil = command.validUntil();
        if (validUntil != null && !validUntil.isAfter(clock.instant())) {
            throw new DocumentValidationException("La date de partage doit être future");
        }
        int changed =
                jdbc.update(
                        "UPDATE document_access_grant SET can_view=TRUE,can_download=?,"
                                + " valid_until=?,granted_by_user_id=?,granted_at=?"
                                + " WHERE tenant_id=? AND document_id=? AND grantee_kind='PARENT'"
                                + " AND grantee_reference_id=?",
                        command.canDownload(),
                        validUntil == null ? null : timestamp(validUntil),
                        actor.accountId(),
                        timestamp(clock.instant()),
                        actor.tenantId(),
                        documentId,
                        parentId);
        if (changed == 0) {
            jdbc.update(
                    "INSERT INTO document_access_grant"
                            + " (id,tenant_id,document_id,grantee_kind,grantee_reference_id,"
                            + " can_view,can_download,valid_until,granted_by_user_id,granted_at)"
                            + " VALUES (?, ?, ?, 'PARENT', ?, TRUE, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    documentId,
                    parentId,
                    command.canDownload(),
                    validUntil == null ? null : timestamp(validUntil),
                    actor.accountId(),
                    timestamp(clock.instant()));
        }
        jdbc.update(
                "UPDATE document_file SET visibility='FAMILY',updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(clock.instant()),
                actor.tenantId(),
                documentId);
        auditDocument(
                actor,
                documentId,
                "document.share.family",
                ip,
                Map.of("parentId", parentId, "download", command.canDownload()));
        return document(actor.tenantId(), documentId, null);
    }

    @Transactional
    public SignedUrlView signedUrl(SchoolPrincipal actor, UUID documentId) {
        require(actor, "document.read");
        DocumentVersion version = currentVersion(actor.tenantId(), documentId, null, true);
        return issue(actor.tenantId(), version, "USER", actor.accountId());
    }

    @Transactional
    public SignedUrlView familySignedUrl(PortalIdentity parent, UUID documentId) {
        DocumentVersion version =
                currentVersion(parent.tenantId(), documentId, parent.accountId(), true);
        return issue(parent.tenantId(), version, "PARENT", parent.accountId());
    }

    @Transactional
    public DownloadContent download(String rawToken) {
        if (rawToken == null || rawToken.length() < 32 || rawToken.length() > 180) {
            throw new DocumentNotFoundException("Lien de téléchargement invalide");
        }
        String hash = tokens.hash(rawToken);
        List<DownloadReference> references =
                jdbc.query(
                        "SELECT sd.id,sd.tenant_id,dv.object_key,dv.original_file_name,"
                                + " dv.content_type,dv.sha256 FROM document_signed_download sd"
                                + " JOIN document_version dv ON dv.id=sd.document_version_id"
                                + " WHERE sd.token_hash=? AND sd.revoked_at IS NULL"
                                + " AND sd.expires_at>? AND sd.download_count<sd.max_downloads",
                        (result, row) ->
                                new DownloadReference(
                                        result.getObject("id", UUID.class),
                                        result.getObject("tenant_id", UUID.class),
                                        result.getString("object_key"),
                                        result.getString("original_file_name"),
                                        result.getString("content_type"),
                                        result.getString("sha256")),
                        hash,
                        timestamp(clock.instant()));
        if (references.isEmpty()) {
            throw new DocumentNotFoundException("Ce lien a expiré ou a déjà été consommé");
        }
        DownloadReference reference = references.getFirst();
        int changed =
                jdbc.update(
                        "UPDATE document_signed_download SET download_count=download_count+1,"
                            + " last_downloaded_at=? WHERE id=? AND download_count<max_downloads"
                            + " AND expires_at>? AND revoked_at IS NULL",
                        timestamp(clock.instant()),
                        reference.id(),
                        timestamp(clock.instant()));
        if (changed != 1) {
            throw new DocumentNotFoundException("Ce lien n'est plus disponible");
        }
        DocumentObjectStorage.ObjectContent content =
                storage.get(reference.tenantId(), reference.objectKey());
        if (!MessageDigest.isEqual(
                reference.sha256().getBytes(StandardCharsets.UTF_8),
                content.sha256().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("L'intégrité du document ne peut pas être confirmée");
        }
        return new DownloadContent(
                reference.fileName(),
                reference.contentType(),
                content.content(),
                reference.sha256());
    }

    private SignedUrlView issue(
            UUID tenantId, DocumentVersion version, String requesterKind, UUID requesterId) {
        String rawToken = tokens.generate();
        Instant expiresAt = clock.instant().plus(10, ChronoUnit.MINUTES);
        jdbc.update(
                "INSERT INTO document_signed_download"
                    + " (id,tenant_id,document_version_id,token_hash,requested_by_kind,"
                    + " requested_by_reference_id,expires_at,max_downloads,download_count,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,3,0,?)",
                UUID.randomUUID(),
                tenantId,
                version.id(),
                tokens.hash(rawToken),
                requesterKind,
                requesterId,
                timestamp(expiresAt),
                timestamp(clock.instant()));
        return new SignedUrlView(
                "/api/v1/public/documents/downloads/" + rawToken,
                expiresAt,
                3,
                version.sha256(),
                version.fileName());
    }

    private void insertDocument(SchoolPrincipal actor, UUID documentId, UploadMetadata metadata) {
        UUID categoryId = required(metadata.categoryId(), "Catégorie obligatoire");
        category(actor.tenantId(), categoryId);
        String type = normalize(metadata.documentType());
        String ownerKind = normalize(metadata.ownerKind());
        String visibility = normalize(metadata.visibility());
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new DocumentValidationException("Type documentaire invalide");
        }
        if (!OWNER_KINDS.contains(ownerKind)) {
            throw new DocumentValidationException("Propriétaire documentaire invalide");
        }
        if (!VISIBILITIES.contains(visibility)) {
            throw new DocumentValidationException("Visibilité documentaire invalide");
        }
        if (!"SCHOOL".equals(ownerKind) && metadata.ownerReferenceId() == null) {
            throw new DocumentValidationException("Référence propriétaire obligatoire");
        }
        if (metadata.expiresOn() != null && metadata.expiresOn().isBefore(LocalDate.now(clock))) {
            throw new DocumentValidationException("La date d'expiration est passée");
        }
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO document_file"
                        + " (id,tenant_id,category_id,document_type,title,description,owner_kind,"
                        + " owner_reference_id,visibility,status,expires_on,current_version_number,"
                        + " created_by_user_id,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE',?,0,?,?,?)",
                documentId,
                actor.tenantId(),
                categoryId,
                type,
                required(metadata.title(), "Titre obligatoire", 240),
                optional(metadata.description(), 2000),
                ownerKind,
                metadata.ownerReferenceId(),
                visibility,
                metadata.expiresOn(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
    }

    private void insertVersion(
            UUID tenantId,
            UUID actorId,
            UUID documentId,
            int version,
            String originalFileName,
            String contentType,
            byte[] content,
            String sourceKind,
            String template) {
        String sha256 = sha256(content);
        UUID versionId = UUID.randomUUID();
        String objectKey =
                "tenants/"
                        + tenantId
                        + "/documents/"
                        + documentId
                        + "/v"
                        + version
                        + "/"
                        + versionId;
        DocumentObjectStorage.StoredObject object =
                storage.put(tenantId, objectKey, contentType, content, sha256);
        jdbc.update(
                "INSERT INTO document_version"
                        + " (id,tenant_id,document_id,version_number,object_key,original_file_name,"
                        + " content_type,size_bytes,sha256,source_kind,pdf_template,"
                        + " created_by_user_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                versionId,
                tenantId,
                documentId,
                version,
                object.objectKey(),
                required(originalFileName, "Nom de fichier obligatoire", 255),
                object.contentType(),
                object.sizeBytes(),
                object.sha256(),
                sourceKind,
                template,
                actorId,
                timestamp(clock.instant()));
        jdbc.update(
                "UPDATE document_file SET current_version_number=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                version,
                timestamp(clock.instant()),
                tenantId,
                documentId);
    }

    private int lockAndNextVersion(UUID tenantId, UUID documentId) {
        List<Integer> versions =
                jdbc.query(
                        "SELECT current_version_number FROM document_file"
                                + " WHERE tenant_id=? AND id=? FOR UPDATE",
                        (result, row) -> result.getInt(1),
                        tenantId,
                        documentId);
        if (versions.isEmpty()) throw new DocumentNotFoundException("Document introuvable");
        return versions.getFirst() + 1;
    }

    private List<DocumentView> documents(UUID tenantId, UUID parentId) {
        String familyJoin =
                parentId == null
                        ? ""
                        : " JOIN document_access_grant dag ON dag.document_id=d.id"
                                + " AND dag.tenant_id=d.tenant_id AND dag.grantee_kind='PARENT'"
                                + " AND dag.grantee_reference_id=? AND dag.can_view=TRUE"
                                + " AND (dag.valid_until IS NULL OR dag.valid_until>?)";
        String sql =
                "SELECT d.*,c.code AS category_code,c.label_fr AS category_label, dv.id AS"
                        + " version_id,dv.original_file_name,dv.content_type,dv.size_bytes,"
                        + " dv.sha256,dv.source_kind,dv.pdf_template,dv.created_at AS"
                        + " version_created_at FROM document_file d JOIN document_category c ON"
                        + " c.id=d.category_id JOIN document_version dv ON dv.document_id=d.id AND"
                        + " dv.version_number=d.current_version_number"
                        + familyJoin
                        + " WHERE d.tenant_id=? AND d.status='ACTIVE' ORDER BY d.updated_at DESC";
        Object[] args =
                parentId == null
                        ? new Object[] {tenantId}
                        : new Object[] {parentId, timestamp(clock.instant()), tenantId};
        return jdbc.query(sql, (result, row) -> documentView(result, tenantId, parentId), args);
    }

    private DocumentView document(UUID tenantId, UUID documentId, UUID parentId) {
        return documents(tenantId, parentId).stream()
                .filter(value -> value.id().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new DocumentNotFoundException("Document introuvable"));
    }

    private DocumentVersion currentVersion(
            UUID tenantId, UUID documentId, UUID parentId, boolean requireDownload) {
        String permission =
                parentId == null
                        ? ""
                        : " JOIN document_access_grant dag ON dag.document_id=d.id"
                                + " AND dag.tenant_id=d.tenant_id AND dag.grantee_kind='PARENT'"
                                + " AND dag.grantee_reference_id=? AND dag.can_view=TRUE"
                                + (requireDownload ? " AND dag.can_download=TRUE" : "")
                                + " AND (dag.valid_until IS NULL OR dag.valid_until>?)";
        String sql =
                "SELECT dv.id,dv.original_file_name,dv.sha256 FROM document_file d"
                        + " JOIN document_version dv ON dv.document_id=d.id"
                        + " AND dv.version_number=d.current_version_number"
                        + permission
                        + " WHERE d.tenant_id=? AND d.id=? AND d.status='ACTIVE'"
                        + " AND (d.expires_on IS NULL OR d.expires_on>=?)";
        Object[] args =
                parentId == null
                        ? new Object[] {tenantId, documentId, LocalDate.now(clock)}
                        : new Object[] {
                            parentId,
                            timestamp(clock.instant()),
                            tenantId,
                            documentId,
                            LocalDate.now(clock)
                        };
        List<DocumentVersion> versions =
                jdbc.query(
                        sql,
                        (result, row) ->
                                new DocumentVersion(
                                        result.getObject("id", UUID.class),
                                        result.getString("original_file_name"),
                                        result.getString("sha256")),
                        args);
        if (versions.isEmpty()) {
            throw new DocumentNotFoundException("Document indisponible ou accès insuffisant");
        }
        return versions.getFirst();
    }

    private List<VersionView> versions(UUID tenantId, UUID documentId) {
        return jdbc.query(
                "SELECT id,version_number,original_file_name,content_type,size_bytes,sha256,"
                        + " source_kind,pdf_template,created_at FROM document_version"
                        + " WHERE tenant_id=? AND document_id=? ORDER BY version_number DESC",
                (result, row) ->
                        new VersionView(
                                result.getObject("id", UUID.class),
                                result.getInt("version_number"),
                                result.getString("original_file_name"),
                                result.getString("content_type"),
                                result.getLong("size_bytes"),
                                result.getString("sha256"),
                                result.getString("source_kind"),
                                result.getString("pdf_template"),
                                instant(result, "created_at")),
                tenantId,
                documentId);
    }

    private DocumentView documentView(ResultSet result, UUID tenantId, UUID parentId)
            throws SQLException {
        UUID documentId = result.getObject("id", UUID.class);
        return new DocumentView(
                documentId,
                result.getString("document_type"),
                result.getString("title"),
                result.getString("description"),
                result.getString("category_code"),
                result.getString("category_label"),
                result.getString("owner_kind"),
                result.getObject("owner_reference_id", UUID.class),
                result.getString("visibility"),
                result.getString("status"),
                result.getObject("expires_on", LocalDate.class),
                result.getInt("current_version_number"),
                versions(tenantId, documentId),
                parentId != null,
                instant(result, "updated_at"));
    }

    private List<CategoryView> categories(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,label_fr,label_ar,system_category,active FROM document_category"
                        + " WHERE tenant_id=? AND active=TRUE ORDER BY label_fr",
                (result, row) ->
                        new CategoryView(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("label_fr"),
                                result.getString("label_ar"),
                                result.getBoolean("system_category"),
                                result.getBoolean("active")),
                tenantId);
    }

    private CategoryView category(UUID tenantId, UUID categoryId) {
        return categories(tenantId).stream()
                .filter(value -> value.id().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new DocumentNotFoundException("Catégorie introuvable"));
    }

    private List<ParentView> parents(UUID tenantId) {
        return jdbc.query(
                "SELECT id,display_name,email FROM admission_portal_account"
                        + " WHERE tenant_id=? AND status='ACTIVE' ORDER BY display_name",
                (result, row) ->
                        new ParentView(
                                result.getObject("id", UUID.class),
                                result.getString("display_name"),
                                result.getString("email")),
                tenantId);
    }

    private void ensureParent(UUID tenantId, UUID parentId) {
        if (parents(tenantId).stream().noneMatch(parent -> parent.id().equals(parentId))) {
            throw new DocumentNotFoundException("Famille introuvable");
        }
    }

    private void ensureDocument(UUID tenantId, UUID documentId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM document_file WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        documentId);
        if (count == null || count == 0) {
            throw new DocumentNotFoundException("Document introuvable");
        }
    }

    private void validateContent(String fileName, String contentType, byte[] content) {
        required(fileName, "Nom de fichier obligatoire", 255);
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(normalizedType)) {
            throw new DocumentValidationException("Seuls PDF, JPEG et PNG sont acceptés");
        }
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new DocumentValidationException("Le fichier doit peser entre 1 octet et 10 Mio");
        }
    }

    private void auditDocument(
            SchoolPrincipal actor,
            UUID documentId,
            String action,
            String ip,
            Map<String, ?> metadata) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                "Document",
                documentId.toString(),
                ip,
                metadata,
                null,
                Map.of("documentId", documentId));
    }

    private static void require(SchoolPrincipal actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission requise : " + permission);
        }
    }

    private static UUID required(UUID value, String message) {
        if (value == null) throw new DocumentValidationException(message);
        return value;
    }

    private static String required(String value, String message, int maximum) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) throw new DocumentValidationException(message);
        if (normalized.length() > maximum) {
            throw new DocumentValidationException(
                    message + " (" + maximum + " caractères maximum)");
        }
        return normalized;
    }

    private static String optional(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maximum) {
            throw new DocumentValidationException(maximum + " caractères maximum");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String code(String value) {
        String normalized = normalize(value).replaceAll("[^A-Z0-9_-]", "_");
        if (normalized.isBlank() || normalized.length() > 80) {
            throw new DocumentValidationException("Code de catégorie invalide");
        }
        return normalized;
    }

    private static String documentTypeForTemplate(String template) {
        return switch (template) {
            case "SCHOOL_CERTIFICATE" -> "ATTESTATION";
            case "RECEIPT", "INVOICE" -> "INVOICE";
            case "CERTIFICATE" -> "CERTIFICATE";
            case "REPORT_CARD" -> "REPORT_CARD";
            case "CONTRACT" -> "CONTRACT";
            default -> "INTERNAL";
        };
    }

    private static String fileName(String value) {
        String normalized =
                java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "document-schoolz" : normalized;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Map<String, String> storageDescriptor() {
        Map<String, String> descriptor = new LinkedHashMap<>();
        descriptor.put("contract", "S3_COMPATIBLE_OBJECT_KEYS");
        descriptor.put("adapter", storage.providerName());
        descriptor.put("download", "TEMPORARY_SIGNED_URL");
        descriptor.put("integrity", "SHA_256_VERIFIED");
        return descriptor;
    }

    private record DocumentVersion(UUID id, String fileName, String sha256) {}

    private record DownloadReference(
            UUID id,
            UUID tenantId,
            String objectKey,
            String fileName,
            String contentType,
            String sha256) {}

    public record CategoryCommand(String code, String labelFr, String labelAr) {}

    public record UploadMetadata(
            UUID categoryId,
            String documentType,
            String title,
            String description,
            String ownerKind,
            UUID ownerReferenceId,
            String visibility,
            LocalDate expiresOn) {}

    public record GeneratePdfCommand(
            UUID categoryId,
            String template,
            String title,
            String description,
            String recipient,
            String reference,
            String ownerKind,
            UUID ownerReferenceId,
            String visibility,
            LocalDate expiresOn,
            Map<String, Object> fields) {}

    public record ShareCommand(UUID portalAccountId, boolean canDownload, Instant validUntil) {}

    public record ParentView(UUID id, String displayName, String email) {}

    public record CategoryView(
            UUID id,
            String code,
            String labelFr,
            String labelAr,
            boolean systemCategory,
            boolean active) {}

    public record VersionView(
            UUID id,
            int versionNumber,
            String fileName,
            String contentType,
            long sizeBytes,
            String sha256,
            String sourceKind,
            String pdfTemplate,
            Instant createdAt) {}

    public record DocumentView(
            UUID id,
            String documentType,
            String title,
            String description,
            String categoryCode,
            String categoryLabel,
            String ownerKind,
            UUID ownerReferenceId,
            String visibility,
            String status,
            LocalDate expiresOn,
            int currentVersion,
            List<VersionView> versions,
            boolean sharedWithCurrentFamily,
            Instant updatedAt) {}

    public record DocumentDashboard(
            Instant generatedAt,
            List<String> documentTypes,
            List<String> pdfTemplates,
            List<CategoryView> categories,
            List<DocumentView> documents,
            List<ParentView> parents,
            Map<String, String> storage) {}

    public record FamilyDocumentDashboard(
            Instant generatedAt,
            String parentName,
            List<DocumentView> documents,
            Map<String, String> storage) {}

    public record SignedUrlView(
            String url, Instant expiresAt, int maxDownloads, String sha256, String fileName) {}

    public record DownloadContent(
            String fileName, String contentType, byte[] content, String sha256) {}
}
