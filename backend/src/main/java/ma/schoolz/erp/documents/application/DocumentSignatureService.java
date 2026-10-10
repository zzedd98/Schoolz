package ma.schoolz.erp.documents.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSignatureService {
    private static final String LEGAL_NOTICE_VERSION = "SIMPLE-CONSENT-FR-MA-v1";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;
    private final Map<String, DocumentSignatureProvider> providers;

    public DocumentSignatureService(
            JdbcTemplate jdbc,
            Clock clock,
            IdentityAuditTrail audit,
            List<DocumentSignatureProvider> providers) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
        this.providers =
                providers.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        provider -> provider.key().toLowerCase(Locale.ROOT),
                                        Function.identity()));
    }

    @Transactional(readOnly = true)
    public SignatureDashboard dashboard(SchoolPrincipal actor) {
        require(actor);
        return new SignatureDashboard(
                clock.instant(),
                workflows(actor.tenantId(), null),
                documents(actor.tenantId()),
                parents(actor.tenantId()),
                providerViews(),
                legalNotice());
    }

    @Transactional(readOnly = true)
    public FamilySignatureDashboard familyDashboard(PortalIdentity parent) {
        return new FamilySignatureDashboard(
                clock.instant(),
                parent.displayName(),
                workflows(parent.tenantId(), parent.accountId()),
                legalNotice());
    }

    @Transactional
    public WorkflowView create(
            SchoolPrincipal actor, CreateWorkflowCommand command, String ipAddress) {
        require(actor);
        DocumentReference document =
                currentDocument(
                        actor.tenantId(), required(command.documentId(), "Document requis"));
        ParentReference parent =
                parent(actor.tenantId(), required(command.portalAccountId(), "Famille requise"));
        UUID workflowId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    "INSERT INTO document_signature_workflow"
                        + " (id,tenant_id,document_id,document_version_id,status,recipient_kind,"
                        + " recipient_reference_id,recipient_name,recipient_email,legal_notice_version,"
                        + " generated_at,created_by_user_id,created_at,updated_at) VALUES"
                        + " (?,?,?,?,'GENERATED','PARENT',?,?,?,?,?,?,?,?)",
                    workflowId,
                    actor.tenantId(),
                    document.documentId(),
                    document.versionId(),
                    parent.id(),
                    parent.name(),
                    parent.email(),
                    LEGAL_NOTICE_VERSION,
                    timestamp(now),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } catch (RuntimeException exception) {
            throw new DocumentValidationException(
                    "Un workflow existe déjà pour cette famille et cette version");
        }
        appendEvent(
                actor.tenantId(),
                workflowId,
                null,
                "GENERATED",
                "USER",
                actor.accountId(),
                actor.displayName(),
                ipAddress,
                null,
                null);
        audit(
                actor,
                workflowId,
                "document.signature.generated",
                ipAddress,
                Map.of("documentId", document.documentId(), "versionId", document.versionId()));
        return workflow(actor.tenantId(), workflowId, null);
    }

    @Transactional
    public WorkflowView send(SchoolPrincipal actor, UUID workflowId, String ipAddress) {
        require(actor);
        WorkflowState state = lock(actor.tenantId(), workflowId, null);
        if (!"GENERATED".equals(state.status())) {
            throw new DocumentValidationException("Seul un workflow généré peut être envoyé");
        }
        Instant now = clock.instant();
        updateStatus(actor.tenantId(), workflowId, "SENT", "sent_at", now);
        grantDocument(actor, state.documentId(), state.recipientId());
        appendEvent(
                actor.tenantId(),
                workflowId,
                state.status(),
                "SENT",
                "USER",
                actor.accountId(),
                actor.displayName(),
                ipAddress,
                null,
                null);
        audit(
                actor,
                workflowId,
                "document.signature.sent",
                ipAddress,
                Map.of("recipientId", state.recipientId()));
        return workflow(actor.tenantId(), workflowId, null);
    }

    @Transactional
    public WorkflowView view(PortalIdentity parent, UUID workflowId, String ipAddress) {
        WorkflowState state = lock(parent.tenantId(), workflowId, parent.accountId());
        if ("GENERATED".equals(state.status())) {
            throw new DocumentValidationException("Ce document n'a pas encore été envoyé");
        }
        if ("SENT".equals(state.status())) {
            Instant now = clock.instant();
            updateStatus(parent.tenantId(), workflowId, "VIEWED", "viewed_at", now);
            appendEvent(
                    parent.tenantId(),
                    workflowId,
                    "SENT",
                    "VIEWED",
                    "PARENT",
                    parent.accountId(),
                    parent.displayName(),
                    ipAddress,
                    null,
                    null);
        }
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView accept(
            PortalIdentity parent, UUID workflowId, AcceptCommand command, String ipAddress) {
        if (command == null || !command.consent()) {
            throw new DocumentValidationException("Le consentement explicite est requis");
        }
        WorkflowState state = lock(parent.tenantId(), workflowId, parent.accountId());
        if (!"VIEWED".equals(state.status())) {
            throw new DocumentValidationException(
                    "Le document doit être consulté avant acceptation");
        }
        Instant now = clock.instant();
        updateStatus(parent.tenantId(), workflowId, "ACCEPTED", "accepted_at", now);
        appendEvent(
                parent.tenantId(),
                workflowId,
                "VIEWED",
                "ACCEPTED",
                "PARENT",
                parent.accountId(),
                parent.displayName(),
                ipAddress,
                null,
                null);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    @Transactional
    public WorkflowView sign(
            PortalIdentity parent, UUID workflowId, SignCommand command, String ipAddress) {
        String name =
                required(
                        command == null ? null : command.signatoryName(),
                        "Nom du signataire requis",
                        180);
        if (command == null || !command.confirmSimpleSignature()) {
            throw new DocumentValidationException(
                    "La confirmation de signature électronique simple est requise");
        }
        WorkflowState state = lock(parent.tenantId(), workflowId, parent.accountId());
        if ("SIGNED".equals(state.status())) {
            return workflow(parent.tenantId(), workflowId, parent.accountId());
        }
        if (!"ACCEPTED".equals(state.status())) {
            throw new DocumentValidationException("Le document doit être accepté avant signature");
        }
        DocumentSignatureProvider provider = provider(command.providerKey());
        DocumentSignatureProvider.SignatureEvidence evidence =
                provider.sign(
                        new DocumentSignatureProvider.SignatureRequest(
                                parent.tenantId(),
                                workflowId,
                                state.versionId(),
                                state.documentSha256(),
                                name,
                                ipAddress,
                                state.acceptedAt()));
        jdbc.update(
                "UPDATE document_signature_workflow SET status='SIGNED',signed_at=?,"
                        + " signatory_name=?,signatory_ip=?,provider_key=?,provider_reference=?,"
                        + " assurance_level=?,evidence_sha256=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=? AND status='ACCEPTED'",
                timestamp(evidence.signedAt()),
                name,
                ipAddress,
                provider.key(),
                evidence.providerReference(),
                evidence.assuranceLevel(),
                evidence.evidenceSha256(),
                timestamp(evidence.signedAt()),
                parent.tenantId(),
                workflowId);
        appendEvent(
                parent.tenantId(),
                workflowId,
                "ACCEPTED",
                "SIGNED",
                "PARENT",
                parent.accountId(),
                name,
                ipAddress,
                provider.key(),
                evidence);
        return workflow(parent.tenantId(), workflowId, parent.accountId());
    }

    private void grantDocument(SchoolPrincipal actor, UUID documentId, UUID parentId) {
        int changed =
                jdbc.update(
                        "UPDATE document_access_grant SET can_view=TRUE,can_download=TRUE,"
                                + " valid_until=NULL,granted_by_user_id=?,granted_at=?"
                                + " WHERE tenant_id=? AND document_id=? AND grantee_kind='PARENT'"
                                + " AND grantee_reference_id=?",
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
                            + " VALUES (?, ?, ?, 'PARENT', ?, TRUE, TRUE, NULL, ?, ?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    documentId,
                    parentId,
                    actor.accountId(),
                    timestamp(clock.instant()));
        }
        jdbc.update(
                "UPDATE document_file SET visibility='FAMILY',updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(clock.instant()),
                actor.tenantId(),
                documentId);
    }

    private void updateStatus(
            UUID tenantId, UUID workflowId, String status, String timestampColumn, Instant now) {
        if (!List.of("sent_at", "viewed_at", "accepted_at").contains(timestampColumn)) {
            throw new IllegalArgumentException("Colonne temporelle invalide");
        }
        jdbc.update(
                "UPDATE document_signature_workflow SET status=?,"
                        + timestampColumn
                        + "=?,updated_at=? WHERE tenant_id=? AND id=?",
                status,
                timestamp(now),
                timestamp(now),
                tenantId,
                workflowId);
    }

    private void appendEvent(
            UUID tenantId,
            UUID workflowId,
            String from,
            String to,
            String actorKind,
            UUID actorId,
            String actorName,
            String ipAddress,
            String providerKey,
            DocumentSignatureProvider.SignatureEvidence evidence) {
        jdbc.update(
                "INSERT INTO document_signature_event"
                        + " (id,tenant_id,workflow_id,from_status,to_status,actor_kind,"
                        + " actor_reference_id,actor_name,ip_address,provider_key,"
                        + " provider_reference,evidence_sha256,occurred_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                workflowId,
                from,
                to,
                actorKind,
                actorId,
                required(actorName, "Acteur requis", 180),
                ipAddress,
                providerKey,
                evidence == null ? null : evidence.providerReference(),
                evidence == null ? null : evidence.evidenceSha256(),
                timestamp(evidence == null ? clock.instant() : evidence.signedAt()));
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private List<WorkflowView> workflows(UUID tenantId, UUID parentId) {
        String filter = parentId == null ? "" : " AND sw.recipient_reference_id=?";
        Object[] args =
                parentId == null ? new Object[] {tenantId} : new Object[] {tenantId, parentId};
        return jdbc.query(
                "SELECT sw.*,d.title,d.document_type,dv.version_number,dv.original_file_name,"
                        + " dv.sha256 AS document_sha256 FROM document_signature_workflow sw"
                        + " JOIN document_file d ON d.id=sw.document_id"
                        + " JOIN document_version dv ON dv.id=sw.document_version_id"
                        + " WHERE sw.tenant_id=?"
                        + filter
                        + " ORDER BY sw.updated_at DESC",
                (result, row) -> workflowView(result, tenantId),
                args);
    }

    private WorkflowView workflow(UUID tenantId, UUID workflowId, UUID parentId) {
        return workflows(tenantId, parentId).stream()
                .filter(value -> value.id().equals(workflowId))
                .findFirst()
                .orElseThrow(() -> new DocumentNotFoundException("Workflow introuvable"));
    }

    private WorkflowView workflowView(ResultSet result, UUID tenantId) throws SQLException {
        UUID workflowId = result.getObject("id", UUID.class);
        return new WorkflowView(
                workflowId,
                result.getObject("document_id", UUID.class),
                result.getObject("document_version_id", UUID.class),
                result.getInt("version_number"),
                result.getString("title"),
                result.getString("document_type"),
                result.getString("original_file_name"),
                result.getString("document_sha256"),
                result.getString("status"),
                result.getString("recipient_name"),
                result.getString("recipient_email"),
                result.getString("legal_notice_version"),
                result.getString("provider_key"),
                result.getString("provider_reference"),
                result.getString("assurance_level"),
                instant(result, "generated_at"),
                instant(result, "sent_at"),
                instant(result, "viewed_at"),
                instant(result, "accepted_at"),
                instant(result, "signed_at"),
                result.getString("signatory_name"),
                result.getString("signatory_ip"),
                result.getString("evidence_sha256"),
                events(tenantId, workflowId));
    }

    private List<EventView> events(UUID tenantId, UUID workflowId) {
        return jdbc.query(
                "SELECT id,from_status,to_status,actor_kind,actor_name,ip_address,"
                        + " provider_key,provider_reference,evidence_sha256,occurred_at"
                        + " FROM document_signature_event WHERE tenant_id=? AND workflow_id=?"
                        + " ORDER BY occurred_at,id",
                (result, row) ->
                        new EventView(
                                result.getObject("id", UUID.class),
                                result.getString("from_status"),
                                result.getString("to_status"),
                                result.getString("actor_kind"),
                                result.getString("actor_name"),
                                result.getString("ip_address"),
                                result.getString("provider_key"),
                                result.getString("provider_reference"),
                                result.getString("evidence_sha256"),
                                instant(result, "occurred_at")),
                tenantId,
                workflowId);
    }

    private WorkflowState lock(UUID tenantId, UUID workflowId, UUID parentId) {
        String parentFilter = parentId == null ? "" : " AND sw.recipient_reference_id=?";
        Object[] args =
                parentId == null
                        ? new Object[] {tenantId, workflowId}
                        : new Object[] {tenantId, workflowId, parentId};
        List<WorkflowState> states =
                jdbc.query(
                        "SELECT sw.status,sw.document_id,sw.document_version_id,"
                                + " sw.recipient_reference_id,sw.accepted_at,dv.sha256"
                                + " FROM document_signature_workflow sw"
                                + " JOIN document_version dv ON dv.id=sw.document_version_id"
                                + " WHERE sw.tenant_id=? AND sw.id=?"
                                + parentFilter
                                + " FOR UPDATE",
                        (result, row) ->
                                new WorkflowState(
                                        result.getString("status"),
                                        result.getObject("document_id", UUID.class),
                                        result.getObject("document_version_id", UUID.class),
                                        result.getObject("recipient_reference_id", UUID.class),
                                        instant(result, "accepted_at"),
                                        result.getString("sha256")),
                        args);
        if (states.isEmpty()) throw new DocumentNotFoundException("Workflow introuvable");
        return states.getFirst();
    }

    private DocumentReference currentDocument(UUID tenantId, UUID documentId) {
        List<DocumentReference> values =
                jdbc.query(
                        "SELECT d.id,d.title,dv.id AS version_id,dv.version_number,dv.sha256 FROM"
                            + " document_file d JOIN document_version dv ON dv.document_id=d.id AND"
                            + " dv.version_number=d.current_version_number WHERE d.tenant_id=? AND"
                            + " d.id=? AND d.status='ACTIVE'",
                        (result, row) ->
                                new DocumentReference(
                                        result.getObject("id", UUID.class),
                                        result.getObject("version_id", UUID.class),
                                        result.getInt("version_number"),
                                        result.getString("title"),
                                        result.getString("sha256")),
                        tenantId,
                        documentId);
        if (values.isEmpty()) throw new DocumentNotFoundException("Document introuvable");
        return values.getFirst();
    }

    private ParentReference parent(UUID tenantId, UUID parentId) {
        List<ParentReference> values =
                jdbc.query(
                        "SELECT id,display_name,email FROM admission_portal_account"
                                + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                        (result, row) ->
                                new ParentReference(
                                        result.getObject("id", UUID.class),
                                        result.getString("display_name"),
                                        result.getString("email")),
                        tenantId,
                        parentId);
        if (values.isEmpty()) throw new DocumentNotFoundException("Famille introuvable");
        return values.getFirst();
    }

    private List<DocumentOption> documents(UUID tenantId) {
        return jdbc.query(
                "SELECT d.id,d.title,d.document_type,d.current_version_number,dv.sha256"
                        + " FROM document_file d JOIN document_version dv"
                        + " ON dv.document_id=d.id AND dv.version_number=d.current_version_number"
                        + " WHERE d.tenant_id=? AND d.status='ACTIVE' ORDER BY d.updated_at DESC",
                (result, row) ->
                        new DocumentOption(
                                result.getObject("id", UUID.class),
                                result.getString("title"),
                                result.getString("document_type"),
                                result.getInt("current_version_number"),
                                result.getString("sha256")),
                tenantId);
    }

    private List<ParentOption> parents(UUID tenantId) {
        return jdbc.query(
                "SELECT id,display_name,email FROM admission_portal_account"
                        + " WHERE tenant_id=? AND status='ACTIVE' ORDER BY display_name",
                (result, row) ->
                        new ParentOption(
                                result.getObject("id", UUID.class),
                                result.getString("display_name"),
                                result.getString("email")),
                tenantId);
    }

    private List<ProviderView> providerViews() {
        return providers.keySet().stream()
                .sorted()
                .map(
                        key ->
                                new ProviderView(
                                        key,
                                        "SIMPLE_ELECTRONIC_ACCEPTANCE",
                                        false,
                                        "Adaptateur local de démonstration, non qualifié"))
                .toList();
    }

    private DocumentSignatureProvider provider(String key) {
        String normalized =
                key == null || key.isBlank() ? "local-simple" : key.toLowerCase(Locale.ROOT);
        DocumentSignatureProvider provider = providers.get(normalized);
        if (provider == null)
            throw new DocumentValidationException("Fournisseur de signature inconnu");
        return provider;
    }

    private void audit(
            SchoolPrincipal actor,
            UUID workflowId,
            String action,
            String ipAddress,
            Map<String, ?> metadata) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                "DocumentSignatureWorkflow",
                workflowId.toString(),
                ipAddress,
                metadata,
                null,
                Map.of("workflowId", workflowId));
    }

    private static Map<String, String> legalNotice() {
        Map<String, String> notice = new LinkedHashMap<>();
        notice.put("version", LEGAL_NOTICE_VERSION);
        notice.put("assurance", "SIMPLE_ELECTRONIC_ACCEPTANCE");
        notice.put(
                "fr",
                "Ce parcours conserve une preuve technique de consentement. Il ne constitue pas une"
                        + " signature électronique qualifiée.");
        notice.put(
                "ar",
                "يحفظ هذا المسار دليلاً تقنياً على الموافقة ولا يشكل توقيعاً إلكترونياً مؤهلاً.");
        return notice;
    }

    private static void require(SchoolPrincipal actor) {
        if (actor == null || !actor.permissions().contains("document.sign")) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission requise : document.sign");
        }
    }

    private static UUID required(UUID value, String message) {
        if (value == null) throw new DocumentValidationException(message);
        return value;
    }

    private static String required(String value, String message, int maximum) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) throw new DocumentValidationException(message);
        if (normalized.length() > maximum) throw new DocumentValidationException(message);
        return normalized;
    }

    private record DocumentReference(
            UUID documentId, UUID versionId, int version, String title, String sha256) {}

    private record ParentReference(UUID id, String name, String email) {}

    private record WorkflowState(
            String status,
            UUID documentId,
            UUID versionId,
            UUID recipientId,
            Instant acceptedAt,
            String documentSha256) {}

    public record CreateWorkflowCommand(UUID documentId, UUID portalAccountId) {}

    public record AcceptCommand(boolean consent) {}

    public record SignCommand(
            String signatoryName, String providerKey, boolean confirmSimpleSignature) {}

    public record DocumentOption(
            UUID id, String title, String documentType, int currentVersion, String sha256) {}

    public record ParentOption(UUID id, String displayName, String email) {}

    public record ProviderView(
            String key, String assuranceLevel, boolean qualified, String description) {}

    public record EventView(
            UUID id,
            String fromStatus,
            String toStatus,
            String actorKind,
            String actorName,
            String ipAddress,
            String providerKey,
            String providerReference,
            String evidenceSha256,
            Instant occurredAt) {}

    public record WorkflowView(
            UUID id,
            UUID documentId,
            UUID documentVersionId,
            int versionNumber,
            String documentTitle,
            String documentType,
            String fileName,
            String documentSha256,
            String status,
            String recipientName,
            String recipientEmail,
            String legalNoticeVersion,
            String providerKey,
            String providerReference,
            String assuranceLevel,
            Instant generatedAt,
            Instant sentAt,
            Instant viewedAt,
            Instant acceptedAt,
            Instant signedAt,
            String signatoryName,
            String signatoryIp,
            String evidenceSha256,
            List<EventView> events) {}

    public record SignatureDashboard(
            Instant generatedAt,
            List<WorkflowView> workflows,
            List<DocumentOption> documents,
            List<ParentOption> parents,
            List<ProviderView> providers,
            Map<String, String> legalNotice) {}

    public record FamilySignatureDashboard(
            Instant generatedAt,
            String parentName,
            List<WorkflowView> workflows,
            Map<String, String> legalNotice) {}
}
