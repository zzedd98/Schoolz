package ma.schoolz.erp.integrations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.schoolz.erp.foundation.security.FileSecurityScanner;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import ma.schoolz.erp.integrations.application.ExchangeFileCodec.EncodedFile;
import ma.schoolz.erp.integrations.application.ExchangeFileCodec.ParsedFile;
import ma.schoolz.erp.integrations.application.IntegrationAdapter.DatasetSchema;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IntegrationService {
    private static final Set<String> FORMATS = Set.of("CSV", "XLSX");
    private static final Set<String> IMPORT_CONTENT_TYPES =
            Set.of("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final long MAX_IMPORT_SIZE = 5L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ExchangeFileCodec files;
    private final IdentityAuditTrail audit;
    private final FileSecurityScanner fileSecurity;
    private final Clock clock;
    private final Map<String, IntegrationAdapter> adapters;

    public IntegrationService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            ExchangeFileCodec files,
            IdentityAuditTrail audit,
            FileSecurityScanner fileSecurity,
            Clock clock,
            List<IntegrationAdapter> adapters) {
        this.jdbc = jdbc;
        this.json = json;
        this.files = files;
        this.audit = audit;
        this.fileSecurity = fileSecurity;
        this.clock = clock;
        this.adapters =
                adapters.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        adapter -> adapter.descriptor().key(),
                                        Function.identity()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('integration.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        return dashboard(actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('integration.manage')")
    public TransferJobView export(
            SchoolPrincipal actor, String adapterKey, ExportCommand command, String ipAddress) {
        TransferJobView job =
                exportForTenant(actor.tenantId(), actor.accountId(), adapterKey, command);
        record(actor, "INTEGRATION_EXPORT_CREATED", job.id(), ipAddress, job);
        return job;
    }

    TransferJobView exportForTenant(
            UUID tenantId, UUID actorId, String adapterKey, ExportCommand command) {
        String format = normalizeFormat(command.format());
        IntegrationAdapter adapter = adapter(adapterKey);
        DatasetSchema schema = adapter.schema(command.dataset());
        if (!schema.exportable())
            throw new IllegalArgumentException("Ce jeu de données n’est pas exportable");
        UUID adapterId = adapterId(tenantId, adapterKey);
        UUID profileId = profileId(tenantId, adapterId, command.dataset(), format, "EXPORT");
        List<Map<String, String>> rows = exportRows(tenantId, command.dataset(), schema.headers());
        EncodedFile encoded = files.encode(format, schema.headers(), rows);
        String hash = sha256(encoded.content());
        UUID jobId = UUID.randomUUID();
        Instant now = clock.instant();
        String extension = format.toLowerCase(Locale.ROOT);
        String name =
                "schoolz-"
                        + adapterKey
                        + "-"
                        + command.dataset().toLowerCase(Locale.ROOT)
                        + "."
                        + extension;
        jdbc.update(
                """
                INSERT INTO integration_transfer_job
                    (id,tenant_id,adapter_id,mapping_profile_id,direction,dataset_type,file_format,
                     status,original_file_name,row_count,success_count,error_count,sha256,
                     created_by_user_id,created_at,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                jobId,
                tenantId,
                adapterId,
                profileId,
                "EXPORT",
                command.dataset(),
                format,
                "COMPLETED",
                name,
                rows.size(),
                rows.size(),
                0,
                hash,
                actorId,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO integration_export_file
                    (id,tenant_id,transfer_job_id,file_name,content_type,file_content,file_size,
                     sha256,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                jobId,
                name,
                encoded.contentType(),
                encoded.content(),
                encoded.content().length,
                hash,
                timestamp(now));
        return job(tenantId, jobId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('integration.manage')")
    public TransferJobView importFile(
            SchoolPrincipal actor,
            String adapterKey,
            String dataset,
            MultipartFile file,
            String ipAddress) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("Le fichier est vide");
        IntegrationAdapter adapter = adapter(adapterKey);
        DatasetSchema schema = adapter.schema(dataset);
        if (!schema.importable())
            throw new IllegalArgumentException("Ce jeu de données n’est pas importable");
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Le fichier ne peut pas être lu", exception);
        }
        fileSecurity.inspect(
                file.getOriginalFilename(),
                file.getContentType(),
                content,
                IMPORT_CONTENT_TYPES,
                MAX_IMPORT_SIZE);
        ParsedFile parsed = files.parse(file.getOriginalFilename(), content, schema.headers());
        UUID adapterId = adapterId(actor.tenantId(), adapterKey);
        UUID profileId = profileId(actor.tenantId(), adapterId, dataset, parsed.format(), "IMPORT");
        UUID jobId = UUID.randomUUID();
        Instant now = clock.instant();
        long errors =
                parsed.rows().stream()
                        .filter(
                                row ->
                                        schema.headers().stream()
                                                .anyMatch(
                                                        header ->
                                                                row.getOrDefault(header, "")
                                                                        .isBlank()))
                        .count();
        jdbc.update(
                """
                INSERT INTO integration_transfer_job
                    (id,tenant_id,adapter_id,mapping_profile_id,direction,dataset_type,file_format,
                     status,original_file_name,row_count,success_count,error_count,sha256,
                     created_by_user_id,created_at,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                jobId,
                actor.tenantId(),
                adapterId,
                profileId,
                "IMPORT",
                dataset,
                parsed.format(),
                errors == 0 ? "VALIDATED" : "COMPLETED_WITH_ERRORS",
                file.getOriginalFilename(),
                parsed.rows().size(),
                parsed.rows().size() - errors,
                errors,
                sha256(content),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        for (int index = 0; index < parsed.rows().size(); index++) {
            Map<String, String> row = parsed.rows().get(index);
            List<String> missing =
                    schema.headers().stream()
                            .filter(header -> row.getOrDefault(header, "").isBlank())
                            .toList();
            jdbc.update(
                    """
                    INSERT INTO integration_transfer_row
                        (id,tenant_id,transfer_job_id,row_number,status,data_json,error_message,created_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    jobId,
                    index + 2,
                    missing.isEmpty() ? "VALID" : "INVALID",
                    writeJson(row),
                    missing.isEmpty()
                            ? null
                            : "Valeurs obligatoires absentes : " + String.join(", ", missing),
                    timestamp(now));
        }
        TransferJobView job = job(actor.tenantId(), jobId);
        record(actor, "INTEGRATION_IMPORT_VALIDATED", jobId, ipAddress, job);
        return job;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('integration.read')")
    public FileDownload download(SchoolPrincipal actor, UUID jobId) {
        List<FileDownload> files =
                jdbc.query(
                        "SELECT file_name,content_type,file_content,sha256 FROM"
                            + " integration_export_file WHERE tenant_id=? AND transfer_job_id=?",
                        (rs, row) ->
                                new FileDownload(
                                        rs.getString("file_name"),
                                        rs.getString("content_type"),
                                        rs.getBytes("file_content"),
                                        rs.getString("sha256")),
                        actor.tenantId(),
                        jobId);
        if (files.isEmpty()) throw new IllegalArgumentException("Fichier d’export introuvable");
        return files.getFirst();
    }

    private Dashboard dashboard(UUID tenantId) {
        List<AdapterView> adapterViews =
                jdbc.query(
                        "SELECT * FROM integration_adapter WHERE tenant_id=? ORDER BY"
                                + " category,display_name",
                        (rs, row) -> mapAdapter(rs, tenantId),
                        tenantId);
        List<TransferJobView> jobs =
                jdbc.query(
                        "SELECT id FROM integration_transfer_job WHERE tenant_id=? ORDER BY"
                                + " created_at DESC FETCH FIRST 40 ROWS ONLY",
                        (rs, row) -> job(tenantId, rs.getObject(1, UUID.class)),
                        tenantId);
        long imported =
                jobs.stream()
                        .filter(job -> "IMPORT".equals(job.direction()))
                        .mapToLong(TransferJobView::successCount)
                        .sum();
        long exported =
                jobs.stream()
                        .filter(job -> "EXPORT".equals(job.direction()))
                        .mapToLong(TransferJobView::successCount)
                        .sum();
        return new Dashboard(
                clock.instant(),
                new Statistics(
                        adapterViews.size(),
                        adapterViews.stream()
                                .filter(adapter -> "READY".equals(adapter.status()))
                                .count(),
                        jobs.size(),
                        imported,
                        exported,
                        jobs.stream().mapToLong(TransferJobView::errorCount).sum()),
                adapterViews,
                jobs,
                new CompliancePolicy(
                        false,
                        false,
                        "Massar reste en mode fichiers. Aucune API officielle n’est déclarée ni"
                                + " simulée."));
    }

    private AdapterView mapAdapter(ResultSet rs, UUID tenantId) throws SQLException {
        String key = rs.getString("adapter_key");
        IntegrationAdapter runtime = adapter(key);
        UUID id = rs.getObject("id", UUID.class);
        List<MappingProfileView> profiles =
                jdbc.query(
                        "SELECT * FROM integration_mapping_profile WHERE tenant_id=? AND"
                                + " adapter_id=? ORDER BY dataset_type,file_format,direction",
                        (inner, row) ->
                                new MappingProfileView(
                                        inner.getObject("id", UUID.class),
                                        inner.getString("code"),
                                        inner.getString("name"),
                                        inner.getString("dataset_type"),
                                        inner.getString("direction"),
                                        inner.getString("file_format"),
                                        readMap(inner.getString("field_mapping_json")),
                                        inner.getBoolean("active")),
                        tenantId,
                        id);
        return new AdapterView(
                id,
                key,
                rs.getString("display_name"),
                rs.getString("category"),
                rs.getString("status"),
                rs.getString("transport_mode"),
                readList(rs.getString("capabilities_json")),
                rs.getString("disclaimer"),
                runtime.schemas(),
                profiles);
    }

    private TransferJobView job(UUID tenantId, UUID id) {
        return jdbc.queryForObject(
                """
                SELECT j.*,a.adapter_key,a.display_name,
                       CASE WHEN f.id IS NULL THEN FALSE ELSE TRUE END AS file_available
                FROM integration_transfer_job j
                JOIN integration_adapter a ON a.id=j.adapter_id AND a.tenant_id=j.tenant_id
                LEFT JOIN integration_export_file f ON f.transfer_job_id=j.id AND f.tenant_id=j.tenant_id
                WHERE j.tenant_id=? AND j.id=?
                """,
                (rs, row) ->
                        new TransferJobView(
                                rs.getObject("id", UUID.class),
                                rs.getString("adapter_key"),
                                rs.getString("display_name"),
                                rs.getString("direction"),
                                rs.getString("dataset_type"),
                                rs.getString("file_format"),
                                rs.getString("status"),
                                rs.getString("original_file_name"),
                                rs.getInt("row_count"),
                                rs.getInt("success_count"),
                                rs.getInt("error_count"),
                                rs.getString("sha256"),
                                rs.getBoolean("file_available"),
                                instant(rs, "created_at"),
                                instant(rs, "completed_at")),
                tenantId,
                id);
    }

    private List<Map<String, String>> exportRows(
            UUID tenantId, String dataset, List<String> headers) {
        if (!"STUDENTS".equals(dataset)) return List.of();
        return jdbc.query(
                """
                SELECT i.identifier_value,p.last_name,p.first_name,p.birth_date,p.gender,
                       c.code AS class_code,y.code AS academic_year
                FROM student_profile s
                JOIN person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id
                LEFT JOIN student_identifier i ON i.student_id=s.id AND i.tenant_id=s.tenant_id
                    AND i.primary_identifier=TRUE
                LEFT JOIN student_enrollment e ON e.student_id=s.id AND e.tenant_id=s.tenant_id
                    AND e.status='ACTIVE'
                LEFT JOIN classroom c ON c.id=e.classroom_id AND c.tenant_id=e.tenant_id
                LEFT JOIN academic_year y ON y.id=e.academic_year_id AND y.tenant_id=e.tenant_id
                WHERE s.tenant_id=? ORDER BY p.last_name,p.first_name
                """,
                (rs, row) -> {
                    Map<String, String> value = new LinkedHashMap<>();
                    value.put("student_identifier", text(rs, "identifier_value"));
                    value.put("last_name", text(rs, "last_name"));
                    value.put("first_name", text(rs, "first_name"));
                    value.put("birth_date", text(rs, "birth_date"));
                    value.put("gender", text(rs, "gender"));
                    value.put("class_code", text(rs, "class_code"));
                    value.put("academic_year", text(rs, "academic_year"));
                    return value;
                },
                tenantId);
    }

    private IntegrationAdapter adapter(String key) {
        IntegrationAdapter value = adapters.get(key);
        if (value == null) throw new IllegalArgumentException("Adaptateur inconnu");
        return value;
    }

    private UUID adapterId(UUID tenantId, String key) {
        List<UUID> ids =
                jdbc.query(
                        "SELECT id FROM integration_adapter WHERE tenant_id=? AND adapter_key=?",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        key);
        if (ids.isEmpty())
            throw new IllegalArgumentException("Adaptateur non configuré pour ce tenant");
        return ids.getFirst();
    }

    private UUID profileId(
            UUID tenantId, UUID adapterId, String dataset, String format, String direction) {
        List<UUID> ids =
                jdbc.query(
                        "SELECT id FROM integration_mapping_profile WHERE tenant_id=? AND"
                            + " adapter_id=? AND dataset_type=? AND file_format=? AND active=TRUE"
                            + " AND (direction=? OR direction='BOTH') ORDER BY created_at FETCH"
                            + " FIRST 1 ROW ONLY",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        adapterId,
                        dataset,
                        format,
                        direction);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static String normalizeFormat(String format) {
        String value = format == null ? "" : format.strip().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(value))
            throw new IllegalArgumentException("Format attendu : CSV ou XLSX");
        return value;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Configuration JSON invalide", exception);
        }
    }

    private Map<String, String> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private List<String> readList(String value) {
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void record(
            SchoolPrincipal actor, String action, UUID jobId, String ipAddress, Object after) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                "integration-transfer",
                jobId.toString(),
                ipAddress,
                Map.of("module", "integrations"),
                null,
                after);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String text(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? "" : String.valueOf(value);
    }

    public record Dashboard(
            Instant generatedAt,
            Statistics statistics,
            List<AdapterView> adapters,
            List<TransferJobView> jobs,
            CompliancePolicy compliance) {}

    public record Statistics(
            long adapterCount,
            long readyAdapters,
            long transferCount,
            long importedRows,
            long exportedRows,
            long errorRows) {}

    public record CompliancePolicy(
            boolean massarScraping, boolean officialMassarApi, String statement) {}

    public record AdapterView(
            UUID id,
            String key,
            String name,
            String category,
            String status,
            String transportMode,
            List<String> capabilities,
            String disclaimer,
            List<DatasetSchema> schemas,
            List<MappingProfileView> profiles) {}

    public record MappingProfileView(
            UUID id,
            String code,
            String name,
            String dataset,
            String direction,
            String format,
            Map<String, String> fieldMapping,
            boolean active) {}

    public record TransferJobView(
            UUID id,
            String adapterKey,
            String adapterName,
            String direction,
            String dataset,
            String format,
            String status,
            String fileName,
            int rowCount,
            int successCount,
            int errorCount,
            String sha256,
            boolean fileAvailable,
            Instant createdAt,
            Instant completedAt) {}

    public record ExportCommand(String dataset, String format) {}

    public record FileDownload(
            String fileName, String contentType, byte[] content, String sha256) {}
}
