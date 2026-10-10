package ma.schoolz.erp.privacy.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyGovernanceService {
    private static final Set<String> REQUEST_TYPES =
            Set.of("ACCESS", "EXPORT", "RECTIFICATION", "ERASURE", "RESTRICTION", "OBJECTION");
    private static final Set<String> SUBJECT_TYPES =
            Set.of("STUDENT", "GUARDIAN", "EMPLOYEE", "PROSPECT");
    private static final Set<String> REQUEST_STATUSES =
            Set.of(
                    "RECEIVED",
                    "IDENTITY_VERIFIED",
                    "IN_REVIEW",
                    "FULFILLED",
                    "REJECTED",
                    "CANCELLED");
    private static final Set<String> RESOLUTION_ACTIONS =
            Set.of("EXPORTED", "RECTIFIED", "ANONYMIZED", "DELETED", "RESTRICTED", "NO_ACTION");
    private static final Set<String> CONSENT_DECISIONS = Set.of("GRANTED", "WITHDRAWN");
    private static final Set<String> EXPIRY_ACTIONS = Set.of("ANONYMIZE", "DELETE", "REVIEW");
    private static final Set<String> LEGAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public PrivacyGovernanceService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('privacy.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        UUID tenant = actor.tenantId();
        List<PurposeView> purposes =
                jdbc.query(
                        "SELECT * FROM privacy_processing_purpose WHERE tenant_id=? ORDER BY"
                                + " sensitive DESC,name",
                        this::purpose,
                        tenant);
        List<RequestView> requests =
                jdbc.query(
                        "SELECT * FROM privacy_data_subject_request WHERE tenant_id=? ORDER BY"
                                + " received_at DESC FETCH FIRST 30 ROWS ONLY",
                        this::request,
                        tenant);
        List<RetentionView> retention =
                jdbc.query(
                        "SELECT * FROM privacy_retention_policy WHERE tenant_id=? ORDER BY"
                                + " data_category",
                        this::retention,
                        tenant);
        List<ExportView> exports =
                jdbc.query(
                        "SELECT * FROM privacy_export_log WHERE tenant_id=? ORDER BY created_at"
                                + " DESC FETCH FIRST 20 ROWS ONLY",
                        this::export,
                        tenant);
        List<HostingView> hosting =
                jdbc.query(
                        "SELECT * FROM privacy_hosting_configuration WHERE tenant_id=?",
                        this::mapHosting,
                        tenant);
        Instant now = clock.instant();
        long open = requests.stream().filter(item -> !terminal(item.status())).count();
        long overdue =
                requests.stream()
                        .filter(item -> !terminal(item.status()) && item.dueAt().isBefore(now))
                        .count();
        Integer consentEvents =
                jdbc.queryForObject(
                        "SELECT count(*) FROM privacy_consent_event WHERE tenant_id=?",
                        Integer.class,
                        tenant);
        return new Dashboard(
                now,
                new Summary(
                        open, overdue, purposes.size(), consentEvents == null ? 0 : consentEvents),
                purposes,
                requests,
                retention,
                exports,
                hosting.isEmpty() ? null : hosting.getFirst());
    }

    @Transactional
    @PreAuthorize("hasAuthority('privacy.manage')")
    public RequestView createRequest(
            SchoolPrincipal actor, RequestCommand command, String ipAddress) {
        if (command == null) throw new IllegalArgumentException("La demande est obligatoire");
        String type = oneOf(command.requestType(), REQUEST_TYPES, "Type de demande inconnu");
        String subjectType =
                oneOf(command.subjectType(), SUBJECT_TYPES, "Type de personne inconnu");
        String subject = required(command.subjectReference(), 120, "Référence de personne requise");
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        String number =
                "PRV-" + now.toString().substring(0, 4) + "-" + id.toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO privacy_data_subject_request"
                    + " (id,tenant_id,request_number,request_type,subject_type,subject_reference,status,received_at,due_at,notes,created_by_user_id,updated_by_user_id,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,'RECEIVED',?,?,?,?,?,?,?)",
                id,
                actor.tenantId(),
                number,
                type,
                subjectType,
                subject,
                Timestamp.from(now),
                Timestamp.from(now.plus(30, ChronoUnit.DAYS)),
                nullable(command.notes(), 4000),
                actor.accountId(),
                actor.accountId(),
                Timestamp.from(now),
                Timestamp.from(now));
        RequestView created = request(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PRIVACY_REQUEST_CREATED",
                "privacy-request",
                id.toString(),
                ipAddress,
                Map.of("module", "privacy", "requestType", type, "subjectType", subjectType),
                null,
                Map.of("status", created.status(), "dueAt", created.dueAt()));
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('privacy.manage')")
    public RequestView updateRequest(
            SchoolPrincipal actor, UUID requestId, RequestStatusCommand command, String ipAddress) {
        if (command == null) throw new IllegalArgumentException("Le statut est obligatoire");
        RequestView before = request(actor.tenantId(), requestId);
        String status = oneOf(command.status(), REQUEST_STATUSES, "Statut de demande inconnu");
        String resolution = nullableUpper(command.resolutionAction());
        if ("FULFILLED".equals(status)) {
            resolution = oneOf(resolution, RESOLUTION_ACTIONS, "Action de résolution requise");
        } else if (resolution != null) {
            throw new IllegalArgumentException("L’action de résolution exige un statut terminé");
        }
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE privacy_data_subject_request SET"
                        + " status=?,resolution_action=?,resolved_at=?,"
                        + " notes=?,updated_by_user_id=?,updated_at=? WHERE tenant_id=? AND id=?",
                status,
                resolution,
                terminal(status) ? Timestamp.from(now) : null,
                nullable(command.notes(), 4000),
                actor.accountId(),
                Timestamp.from(now),
                actor.tenantId(),
                requestId);
        RequestView after = request(actor.tenantId(), requestId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PRIVACY_REQUEST_STATUS_CHANGED",
                "privacy-request",
                requestId.toString(),
                ipAddress,
                Map.of("module", "privacy"),
                Map.of("status", before.status()),
                Map.of("status", after.status()));
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('privacy.manage')")
    public void recordConsent(SchoolPrincipal actor, ConsentCommand command, String ipAddress) {
        if (command == null || command.purposeId() == null)
            throw new IllegalArgumentException("La finalité est obligatoire");
        requirePurpose(actor.tenantId(), command.purposeId());
        String subjectType =
                oneOf(command.subjectType(), SUBJECT_TYPES, "Type de personne inconnu");
        String decision =
                oneOf(command.decision(), CONSENT_DECISIONS, "Décision de consentement inconnue");
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO privacy_consent_event"
                    + " (id,tenant_id,purpose_id,subject_type,subject_reference,decision,source,evidence_reference,decided_at,recorded_by_user_id,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id,
                actor.tenantId(),
                command.purposeId(),
                subjectType,
                required(command.subjectReference(), 120, "Référence de personne requise"),
                decision,
                required(command.source(), 40, "Source requise"),
                nullable(command.evidenceReference(), 220),
                Timestamp.from(now),
                actor.accountId(),
                Timestamp.from(now));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PRIVACY_CONSENT_RECORDED",
                "privacy-consent",
                id.toString(),
                ipAddress,
                Map.of("module", "privacy", "decision", decision, "subjectType", subjectType),
                null,
                Map.of("purposeId", command.purposeId()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('privacy.manage')")
    public void configureRetention(
            SchoolPrincipal actor, RetentionCommand command, String ipAddress) {
        if (command == null || command.retentionDays() < 1 || command.retentionDays() > 36500)
            throw new IllegalArgumentException("Durée de rétention invalide");
        String category = required(command.dataCategory(), 80, "Catégorie requise");
        String action = oneOf(command.expiryAction(), EXPIRY_ACTIONS, "Action d’échéance inconnue");
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE privacy_retention_policy SET retention_days=?,expiry_action=?,"
                            + " legal_hold=?,last_reviewed_at=?,updated_by_user_id=?,updated_at=?"
                            + " WHERE tenant_id=? AND data_category=?",
                        command.retentionDays(),
                        action,
                        command.legalHold(),
                        Timestamp.from(now),
                        actor.accountId(),
                        Timestamp.from(now),
                        actor.tenantId(),
                        category);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO privacy_retention_policy"
                        + " (id,tenant_id,data_category,retention_days,expiry_action,legal_hold,last_reviewed_at,updated_by_user_id,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    category,
                    command.retentionDays(),
                    action,
                    command.legalHold(),
                    Timestamp.from(now),
                    actor.accountId(),
                    Timestamp.from(now));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PRIVACY_RETENTION_CONFIGURED",
                "privacy-retention",
                category,
                ipAddress,
                Map.of("module", "privacy"),
                null,
                Map.of("days", command.retentionDays(), "action", action));
    }

    @Transactional
    @PreAuthorize("hasAuthority('privacy.export')")
    public ExportView prepareExport(
            SchoolPrincipal actor, ExportCommand command, String ipAddress) {
        if (command == null) throw new IllegalArgumentException("L’export est obligatoire");
        String subjectType =
                oneOf(command.subjectType(), SUBJECT_TYPES, "Type de personne inconnu");
        String subject = required(command.subjectReference(), 120, "Référence de personne requise");
        if (command.requestId() != null) request(actor.tenantId(), command.requestId());
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO privacy_export_log"
                    + " (id,tenant_id,request_id,subject_type,subject_reference,scope_summary,status,exported_by_user_id,created_at,expires_at)"
                    + " VALUES (?,?,?,?,?,?,'PREPARED',?,?,?)",
                id,
                actor.tenantId(),
                command.requestId(),
                subjectType,
                subject,
                required(command.scopeSummary(), 500, "Périmètre d’export requis"),
                actor.accountId(),
                Timestamp.from(now),
                Timestamp.from(now.plus(7, ChronoUnit.DAYS)));
        ExportView created = export(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PRIVACY_EXPORT_PREPARED",
                "privacy-export",
                id.toString(),
                ipAddress,
                Map.of("module", "privacy", "subjectType", subjectType),
                null,
                Map.of("status", created.status(), "expiresAt", created.expiresAt()));
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('privacy.manage')")
    public HostingView configureHosting(
            SchoolPrincipal actor, HostingCommand command, String ipAddress) {
        if (command == null) throw new IllegalArgumentException("L’hébergement est obligatoire");
        String country =
                required(command.countryCode(), 2, "Code pays ISO requis").toUpperCase(Locale.ROOT);
        String legal =
                oneOf(
                        command.legalValidationStatus(),
                        LEGAL_STATUSES,
                        "Statut de validation juridique inconnu");
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE privacy_hosting_configuration SET country_code=?,region_label=?,"
                                + " provider_name=?,encryption_at_rest=?,encryption_in_transit=?,"
                                + " cross_border_transfer=?,legal_validation_status=?,"
                                + " updated_by_user_id=?,updated_at=? WHERE tenant_id=?",
                        country,
                        required(command.regionLabel(), 120, "Région requise"),
                        required(command.providerName(), 160, "Hébergeur requis"),
                        command.encryptionAtRest(),
                        command.encryptionInTransit(),
                        command.crossBorderTransfer(),
                        legal,
                        actor.accountId(),
                        Timestamp.from(now),
                        actor.tenantId());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO privacy_hosting_configuration"
                        + " (tenant_id,country_code,region_label,provider_name,encryption_at_rest,encryption_in_transit,cross_border_transfer,legal_validation_status,updated_by_user_id,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                    actor.tenantId(),
                    country,
                    required(command.regionLabel(), 120, "Région requise"),
                    required(command.providerName(), 160, "Hébergeur requis"),
                    command.encryptionAtRest(),
                    command.encryptionInTransit(),
                    command.crossBorderTransfer(),
                    legal,
                    actor.accountId(),
                    Timestamp.from(now));
        }
        HostingView result = hosting(actor.tenantId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PRIVACY_HOSTING_CONFIGURED",
                "privacy-hosting",
                actor.tenantId().toString(),
                ipAddress,
                Map.of("module", "privacy"),
                null,
                Map.of(
                        "countryCode", result.countryCode(),
                        "crossBorderTransfer", result.crossBorderTransfer(),
                        "legalValidationStatus", result.legalValidationStatus()));
        return result;
    }

    private RequestView request(UUID tenant, UUID id) {
        List<RequestView> values =
                jdbc.query(
                        "SELECT * FROM privacy_data_subject_request WHERE tenant_id=? AND id=?",
                        this::request,
                        tenant,
                        id);
        if (values.isEmpty()) throw new IllegalArgumentException("Demande introuvable");
        return values.getFirst();
    }

    private ExportView export(UUID tenant, UUID id) {
        List<ExportView> values =
                jdbc.query(
                        "SELECT * FROM privacy_export_log WHERE tenant_id=? AND id=?",
                        this::export,
                        tenant,
                        id);
        if (values.isEmpty()) throw new IllegalArgumentException("Export introuvable");
        return values.getFirst();
    }

    private HostingView hosting(UUID tenant) {
        return jdbc
                .query(
                        "SELECT * FROM privacy_hosting_configuration WHERE tenant_id=?",
                        this::mapHosting,
                        tenant)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Hébergement introuvable"));
    }

    private void requirePurpose(UUID tenant, UUID id) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM privacy_processing_purpose WHERE tenant_id=? AND id=?"
                                + " AND active=TRUE",
                        Integer.class,
                        tenant,
                        id);
        if (count == null || count == 0) throw new IllegalArgumentException("Finalité introuvable");
    }

    private PurposeView purpose(ResultSet result, int row) throws SQLException {
        return new PurposeView(
                result.getObject("id", UUID.class),
                result.getString("purpose_code"),
                result.getString("name"),
                result.getString("description"),
                result.getString("lawful_basis"),
                result.getString("data_categories"),
                result.getString("recipients"),
                result.getInt("retention_days"),
                result.getBoolean("sensitive"),
                result.getBoolean("active"));
    }

    private RequestView request(ResultSet result, int row) throws SQLException {
        Timestamp resolved = result.getTimestamp("resolved_at");
        return new RequestView(
                result.getObject("id", UUID.class),
                result.getString("request_number"),
                result.getString("request_type"),
                result.getString("subject_type"),
                result.getString("subject_reference"),
                result.getString("status"),
                result.getTimestamp("received_at").toInstant(),
                result.getTimestamp("due_at").toInstant(),
                resolved == null ? null : resolved.toInstant(),
                result.getString("resolution_action"),
                result.getString("notes"));
    }

    private RetentionView retention(ResultSet result, int row) throws SQLException {
        return new RetentionView(
                result.getObject("id", UUID.class),
                result.getString("data_category"),
                result.getInt("retention_days"),
                result.getString("expiry_action"),
                result.getBoolean("legal_hold"),
                result.getTimestamp("last_reviewed_at").toInstant());
    }

    private ExportView export(ResultSet result, int row) throws SQLException {
        return new ExportView(
                result.getObject("id", UUID.class),
                result.getObject("request_id", UUID.class),
                result.getString("subject_type"),
                result.getString("subject_reference"),
                result.getString("scope_summary"),
                result.getString("artifact_sha256"),
                result.getString("status"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("expires_at").toInstant());
    }

    private HostingView mapHosting(ResultSet result, int row) throws SQLException {
        return new HostingView(
                result.getString("country_code"),
                result.getString("region_label"),
                result.getString("provider_name"),
                result.getBoolean("encryption_at_rest"),
                result.getBoolean("encryption_in_transit"),
                result.getBoolean("cross_border_transfer"),
                result.getString("legal_validation_status"),
                result.getTimestamp("updated_at").toInstant());
    }

    private boolean terminal(String status) {
        return Set.of("FULFILLED", "REJECTED", "CANCELLED").contains(status);
    }

    private String oneOf(String value, Set<String> allowed, String message) {
        String normalized = nullableUpper(value);
        if (normalized == null || !allowed.contains(normalized))
            throw new IllegalArgumentException(message);
        return normalized;
    }

    private String nullableUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String required(String value, int maximum, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String clean = value.trim();
        if (clean.length() > maximum) throw new IllegalArgumentException("Valeur trop longue");
        return clean;
    }

    private String nullable(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > maximum) throw new IllegalArgumentException("Valeur trop longue");
        return clean;
    }

    public record Dashboard(
            Instant generatedAt,
            Summary summary,
            List<PurposeView> purposes,
            List<RequestView> requests,
            List<RetentionView> retention,
            List<ExportView> exports,
            HostingView hosting) {}

    public record Summary(
            long openRequests, long overdueRequests, int purposes, int consentEvents) {}

    public record PurposeView(
            UUID id,
            String purposeCode,
            String name,
            String description,
            String lawfulBasis,
            String dataCategories,
            String recipients,
            int retentionDays,
            boolean sensitive,
            boolean active) {}

    public record RequestView(
            UUID id,
            String requestNumber,
            String requestType,
            String subjectType,
            String subjectReference,
            String status,
            Instant receivedAt,
            Instant dueAt,
            Instant resolvedAt,
            String resolutionAction,
            String notes) {}

    public record RetentionView(
            UUID id,
            String dataCategory,
            int retentionDays,
            String expiryAction,
            boolean legalHold,
            Instant lastReviewedAt) {}

    public record ExportView(
            UUID id,
            UUID requestId,
            String subjectType,
            String subjectReference,
            String scopeSummary,
            String artifactSha256,
            String status,
            Instant createdAt,
            Instant expiresAt) {}

    public record HostingView(
            String countryCode,
            String regionLabel,
            String providerName,
            boolean encryptionAtRest,
            boolean encryptionInTransit,
            boolean crossBorderTransfer,
            String legalValidationStatus,
            Instant updatedAt) {}

    public record RequestCommand(
            String requestType, String subjectType, String subjectReference, String notes) {}

    public record RequestStatusCommand(String status, String resolutionAction, String notes) {}

    public record ConsentCommand(
            UUID purposeId,
            String subjectType,
            String subjectReference,
            String decision,
            String source,
            String evidenceReference) {}

    public record RetentionCommand(
            String dataCategory, int retentionDays, String expiryAction, boolean legalHold) {}

    public record ExportCommand(
            UUID requestId, String subjectType, String subjectReference, String scopeSummary) {}

    public record HostingCommand(
            String countryCode,
            String regionLabel,
            String providerName,
            boolean encryptionAtRest,
            boolean encryptionInTransit,
            boolean crossBorderTransfer,
            String legalValidationStatus) {}
}
