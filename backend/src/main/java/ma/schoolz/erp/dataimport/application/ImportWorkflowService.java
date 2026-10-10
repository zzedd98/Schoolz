package ma.schoolz.erp.dataimport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import ma.schoolz.erp.dataimport.application.ImportDefinitionCatalog.Field;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportWorkflowService {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ValidationIssue>> ISSUE_LIST = new TypeReference<>() {};
    private static final Pattern EMAIL =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> FIELD_LIMITS =
            Map.ofEntries(
                    Map.entry("first_name", 120),
                    Map.entry("last_name", 120),
                    Map.entry("preferred_name", 120),
                    Map.entry("school_identifier", 120),
                    Map.entry("student_identifier", 120),
                    Map.entry("household_name", 180),
                    Map.entry("address_line1", 220),
                    Map.entry("address_line2", 220),
                    Map.entry("city", 120),
                    Map.entry("region", 120),
                    Map.entry("postal_code", 20),
                    Map.entry("guardian_first_name", 120),
                    Map.entry("guardian_last_name", 120),
                    Map.entry("guardian_relationship", 50),
                    Map.entry("relationship", 50),
                    Map.entry("guardian_email", 254),
                    Map.entry("email", 254),
                    Map.entry("guardian_phone", 40),
                    Map.entry("phone", 40),
                    Map.entry("guardian_occupation", 160),
                    Map.entry("occupation", 160),
                    Map.entry("employee_number", 80),
                    Map.entry("job_title", 160),
                    Map.entry("code", 60),
                    Map.entry("name", 140),
                    Map.entry("payment_method", 50),
                    Map.entry("external_reference", 120),
                    Map.entry("description", 300));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ImportFileParser parser;
    private final ImportDefinitionCatalog catalog;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final int previewRows;

    public ImportWorkflowService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            ImportFileParser parser,
            ImportDefinitionCatalog catalog,
            IdentityAuditTrail audit,
            Clock clock,
            @Value("${schoolz.imports.preview-rows:100}") int previewRows) {
        this.jdbc = jdbc;
        this.json = json;
        this.parser = parser;
        this.catalog = catalog;
        this.audit = audit;
        this.clock = clock;
        this.previewRows = previewRows;
    }

    @Transactional
    @PreAuthorize("hasAuthority('data-import.write')")
    public ImportJobView upload(
            SchoolPrincipal actor, ImportType type, MultipartFile file, String ipAddress) {
        ImportFileParser.ParsedFile parsed = parser.parse(file);
        UUID jobId = UUID.randomUUID();
        Instant now = clock.instant();
        Map<String, String> suggestions = catalog.suggestedMapping(type, parsed.headers());
        String fileName =
                file.getOriginalFilename() == null
                        ? "import." + parsed.format().toLowerCase(Locale.ROOT)
                        : file.getOriginalFilename();
        jdbc.update(
                """
                INSERT INTO data_import_job
                    (id,tenant_id,import_type,file_name,file_format,status,source_headers,
                     column_mapping,total_rows,created_by,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'UPLOADED', ?, ?, ?, ?, ?, ?)
                """,
                jobId,
                actor.tenantId(),
                type.name(),
                fileName,
                parsed.format(),
                writeJson(parsed.headers()),
                writeJson(suggestions),
                parsed.rows().size(),
                actor.accountId(),
                databaseTime(now),
                databaseTime(now));
        for (ImportFileParser.ParsedRow row : parsed.rows()) {
            jdbc.update(
                    """
                    INSERT INTO data_import_row
                        (id,tenant_id,job_id,row_number,raw_data,normalized_data,
                         validation_errors,status,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, '{}', '[]', 'PENDING', ?, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    jobId,
                    row.rowNumber(),
                    writeJson(row.values()),
                    databaseTime(now),
                    databaseTime(now));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "data-import.uploaded",
                "DataImportJob",
                jobId.toString(),
                ipAddress,
                Map.of(
                        "type",
                        type.name(),
                        "rows",
                        parsed.rows().size(),
                        "format",
                        parsed.format()),
                null,
                Map.of("fileName", fileName));
        return job(actor, jobId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('data-import.read')")
    public List<ImportJobView> jobs(SchoolPrincipal actor) {
        return jdbc.query(
                """
                SELECT * FROM data_import_job
                WHERE tenant_id=? ORDER BY created_at DESC FETCH FIRST 30 ROWS ONLY
                """,
                (result, row) -> jobView(result),
                actor.tenantId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('data-import.read')")
    public ImportJobView job(SchoolPrincipal actor, UUID jobId) {
        List<ImportJobView> jobs =
                jdbc.query(
                        "SELECT * FROM data_import_job WHERE id=? AND tenant_id=?",
                        (result, row) -> jobView(result),
                        jobId,
                        actor.tenantId());
        if (jobs.isEmpty()) {
            throw new ImportNotFoundException();
        }
        return jobs.getFirst();
    }

    @Transactional
    @PreAuthorize("hasAuthority('data-import.write')")
    public ImportJobView map(
            SchoolPrincipal actor, UUID jobId, Map<String, String> mapping, String ipAddress) {
        ImportJobView job = mutableJob(actor, jobId);
        validateMapping(job, mapping);
        Instant now = clock.instant();
        for (StoredRow row : storedRows(actor.tenantId(), jobId)) {
            Map<String, String> normalized = new LinkedHashMap<>();
            mapping.forEach(
                    (field, header) ->
                            normalized.put(field, row.raw().getOrDefault(header, "").strip()));
            jdbc.update(
                    """
                    UPDATE data_import_row
                    SET normalized_data=?, validation_errors='[]', status='PENDING',
                        target_record_id=NULL, updated_at=?
                    WHERE id=? AND tenant_id=?
                    """,
                    writeJson(normalized),
                    databaseTime(now),
                    row.id(),
                    actor.tenantId());
        }
        jdbc.update(
                """
                UPDATE data_import_job
                SET column_mapping=?,status='MAPPED',valid_rows=0,invalid_rows=0,
                    imported_rows=0,error_summary=NULL,updated_at=?,version=version+1
                WHERE id=? AND tenant_id=?
                """,
                writeJson(mapping),
                databaseTime(now),
                jobId,
                actor.tenantId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "data-import.mapped",
                "DataImportJob",
                jobId.toString(),
                ipAddress,
                Map.of("mappedFields", mapping.size()),
                job.mapping(),
                mapping);
        return job(actor, jobId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('data-import.write')")
    public ImportPreview validate(SchoolPrincipal actor, UUID jobId, String ipAddress) {
        ImportJobView job = mutableJob(actor, jobId);
        if (job.status() != ImportStatus.MAPPED && job.status() != ImportStatus.VALIDATED) {
            throw new ImportValidationException(
                    "Le mapping doit être confirmé avant la validation");
        }
        List<StoredRow> rows = storedRows(actor.tenantId(), jobId);
        Map<String, Long> duplicateKeys = duplicateKeys(job.type(), rows);
        int valid = 0;
        int invalid = 0;
        Instant now = clock.instant();
        for (StoredRow row : rows) {
            List<ValidationIssue> issues =
                    validateRow(actor.tenantId(), job.type(), row.normalized(), duplicateKeys);
            String status = issues.isEmpty() ? "VALID" : "INVALID";
            valid += issues.isEmpty() ? 1 : 0;
            invalid += issues.isEmpty() ? 0 : 1;
            jdbc.update(
                    """
                    UPDATE data_import_row
                    SET validation_errors=?,status=?,updated_at=?
                    WHERE id=? AND tenant_id=?
                    """,
                    writeJson(issues),
                    status,
                    databaseTime(now),
                    row.id(),
                    actor.tenantId());
        }
        jdbc.update(
                """
                UPDATE data_import_job
                SET status='VALIDATED',valid_rows=?,invalid_rows=?,imported_rows=0,
                    error_summary=?,updated_at=?,version=version+1
                WHERE id=? AND tenant_id=?
                """,
                valid,
                invalid,
                invalid == 0 ? null : invalid + " ligne(s) à corriger",
                databaseTime(now),
                jobId,
                actor.tenantId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "data-import.validated",
                "DataImportJob",
                jobId.toString(),
                ipAddress,
                Map.of("validRows", valid, "invalidRows", invalid),
                null,
                null);
        return preview(actor, jobId, previewRows);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('data-import.read')")
    public ImportPreview preview(SchoolPrincipal actor, UUID jobId, int requestedLimit) {
        ImportJobView job = job(actor, jobId);
        int limit = Math.max(1, Math.min(requestedLimit, previewRows));
        List<ImportRowView> rows =
                jdbc.query(
                        """
                        SELECT row_number,normalized_data,validation_errors,status,target_record_id
                        FROM data_import_row WHERE job_id=? AND tenant_id=?
                        ORDER BY row_number FETCH FIRST ? ROWS ONLY
                        """,
                        (result, row) ->
                                new ImportRowView(
                                        result.getInt("row_number"),
                                        readStringMap(result.getString("normalized_data")),
                                        readIssues(result.getString("validation_errors")),
                                        result.getString("status"),
                                        result.getObject("target_record_id", UUID.class)),
                        jobId,
                        actor.tenantId(),
                        limit);
        return new ImportPreview(job, rows, rows.size() < job.totalRows());
    }

    List<StoredRow> executableRows(UUID tenantId, UUID jobId) {
        return storedRows(tenantId, jobId);
    }

    ImportJobView executableJob(UUID tenantId, UUID jobId) {
        List<ImportJobView> jobs =
                jdbc.query(
                        "SELECT * FROM data_import_job WHERE id=? AND tenant_id=?",
                        (result, row) -> jobView(result),
                        jobId,
                        tenantId);
        if (jobs.isEmpty()) {
            throw new ImportNotFoundException();
        }
        return jobs.getFirst();
    }

    private ImportJobView mutableJob(SchoolPrincipal actor, UUID jobId) {
        ImportJobView job = job(actor, jobId);
        if (job.status() == ImportStatus.IMPORTED) {
            throw new ImportValidationException("Un import terminé ne peut plus être modifié");
        }
        return job;
    }

    private void validateMapping(ImportJobView job, Map<String, String> mapping) {
        if (mapping == null) {
            throw new ImportValidationException("Le mapping des colonnes est obligatoire");
        }
        ImportDefinitionCatalog.Definition definition = catalog.definition(job.type());
        List<String> unknownFields =
                mapping.keySet().stream().filter(key -> !definition.keys().contains(key)).toList();
        if (!unknownFields.isEmpty()) {
            throw new ImportValidationException("Champs de destination inconnus: " + unknownFields);
        }
        List<String> unknownHeaders =
                mapping.values().stream().filter(value -> !job.headers().contains(value)).toList();
        if (!unknownHeaders.isEmpty()) {
            throw new ImportValidationException("Colonnes source inconnues: " + unknownHeaders);
        }
        if (new HashSet<>(mapping.values()).size() != mapping.size()) {
            throw new ImportValidationException(
                    "Une colonne source ne peut être utilisée qu'une fois");
        }
        List<String> missing =
                definition.requiredKeys().stream()
                        .filter(key -> !mapping.containsKey(key))
                        .toList();
        if (!missing.isEmpty()) {
            throw new ImportValidationException("Champs obligatoires non mappés: " + missing);
        }
    }

    private List<ValidationIssue> validateRow(
            UUID tenantId,
            ImportType type,
            Map<String, String> values,
            Map<String, Long> duplicateKeys) {
        List<ValidationIssue> issues = new ArrayList<>();
        ImportDefinitionCatalog.Definition definition = catalog.definition(type);
        for (Field field : definition.fields()) {
            String value = value(values, field.key());
            if (field.required() && value.isBlank()) {
                issues.add(issue(field.key(), "Valeur obligatoire"));
            }
            int limit = FIELD_LIMITS.getOrDefault(field.key(), 500);
            if (value.length() > limit) {
                issues.add(issue(field.key(), "La valeur dépasse " + limit + " caractères"));
            }
        }
        switch (type) {
            case STUDENTS -> validateStudent(tenantId, values, duplicateKeys, issues);
            case PARENTS -> validateParent(tenantId, values, duplicateKeys, issues);
            case STAFF -> validateStaff(tenantId, values, duplicateKeys, issues);
            case CLASSES -> validateClassroom(tenantId, values, duplicateKeys, issues);
            case PAYMENTS -> validatePayment(tenantId, values, issues);
            case OPENING_BALANCES -> validateOpeningBalance(tenantId, values, issues);
        }
        return issues;
    }

    private void validateStudent(
            UUID tenantId,
            Map<String, String> values,
            Map<String, Long> duplicates,
            List<ValidationIssue> issues) {
        date(values, "birth_date", issues, true);
        date(values, "enrolled_on", issues, true);
        iso2(values, "country_code", issues, true);
        iso2(values, "nationality_code", issues, false);
        iso2(values, "guardian_nationality_code", issues, false);
        email(values, "guardian_email", issues, true);
        bool(values, "financial_responsible", issues, false);
        uniqueInFile("school_identifier", values, duplicates, issues);
        uniqueInFile("guardian_email", values, duplicates, issues);
        uniqueInFile("guardian_phone", values, duplicates, issues);
        if (exists(
                "SELECT count(*) FROM student_identifier WHERE tenant_id=? AND"
                        + " identifier_type='SCHOOL' AND identifier_value=?",
                tenantId,
                value(values, "school_identifier"))) {
            issues.add(issue("school_identifier", "Ce matricule existe déjà"));
        }
        if (!value(values, "guardian_email").isBlank()
                && exists(
                        "SELECT count(*) FROM person_contact_method WHERE tenant_id=? AND"
                                + " contact_type='EMAIL' AND normalized_value=?",
                        tenantId,
                        value(values, "guardian_email").toLowerCase(Locale.ROOT))) {
            issues.add(issue("guardian_email", "Cette adresse e-mail est déjà utilisée"));
        }
        contactExists(tenantId, values, "guardian_phone", "PHONE", issues);
        AcademicReferences references = academicReferences(tenantId, values, issues, true);
        if (references != null && !value(values, "classroom_code").isBlank()) {
            UUID classroom =
                    optionalId(
                            "SELECT id FROM classroom WHERE tenant_id=? AND campus_id=? AND"
                                    + " academic_year_id=? AND code=?",
                            tenantId,
                            references.campusId(),
                            references.yearId(),
                            value(values, "classroom_code"));
            if (classroom == null) {
                issues.add(
                        issue(
                                "classroom_code",
                                "Classe introuvable dans ce campus et cette année"));
            }
        }
    }

    private void validateParent(
            UUID tenantId,
            Map<String, String> values,
            Map<String, Long> duplicates,
            List<ValidationIssue> issues) {
        email(values, "email", issues, false);
        iso2(values, "nationality_code", issues, false);
        bool(values, "financial_responsible", issues, false);
        uniqueInFile("email", values, duplicates, issues);
        uniqueInFile("phone", values, duplicates, issues);
        requireStudent(tenantId, values, issues);
        if (!value(values, "email").isBlank()
                && exists(
                        "SELECT count(*) FROM person_contact_method WHERE tenant_id=? AND"
                                + " contact_type='EMAIL' AND normalized_value=?",
                        tenantId,
                        value(values, "email").toLowerCase(Locale.ROOT))) {
            issues.add(issue("email", "Cette adresse e-mail est déjà utilisée"));
        }
        contactExists(tenantId, values, "phone", "PHONE", issues);
    }

    private void validateStaff(
            UUID tenantId,
            Map<String, String> values,
            Map<String, Long> duplicates,
            List<ValidationIssue> issues) {
        date(values, "hired_on", issues, true);
        email(values, "email", issues, false);
        iso2(values, "nationality_code", issues, false);
        uniqueInFile("employee_number", values, duplicates, issues);
        uniqueInFile("email", values, duplicates, issues);
        uniqueInFile("phone", values, duplicates, issues);
        if (exists(
                "SELECT count(*) FROM staff_profile WHERE tenant_id=? AND employee_number=?",
                tenantId,
                value(values, "employee_number"))) {
            issues.add(issue("employee_number", "Ce matricule personnel existe déjà"));
        }
        if (!value(values, "email").isBlank()
                && exists(
                        "SELECT count(*) FROM person_contact_method WHERE tenant_id=? AND"
                                + " contact_type='EMAIL' AND normalized_value=?",
                        tenantId,
                        value(values, "email").toLowerCase(Locale.ROOT))) {
            issues.add(issue("email", "Cette adresse e-mail est déjà utilisée"));
        }
        contactExists(tenantId, values, "phone", "PHONE", issues);
    }

    private void validateClassroom(
            UUID tenantId,
            Map<String, String> values,
            Map<String, Long> duplicates,
            List<ValidationIssue> issues) {
        positiveInteger(values, "capacity", issues);
        uniqueInFile("code", values, duplicates, issues);
        AcademicReferences references = academicReferences(tenantId, values, issues, true);
        UUID gradeId =
                optionalId(
                        "SELECT id FROM grade_level WHERE tenant_id=? AND code=?",
                        tenantId,
                        value(values, "grade_code"));
        if (gradeId == null && !value(values, "grade_code").isBlank()) {
            issues.add(issue("grade_code", "Niveau introuvable"));
        }
        if (references != null
                && exists(
                        "SELECT count(*) FROM classroom WHERE tenant_id=? AND campus_id=? AND"
                                + " academic_year_id=? AND code=?",
                        tenantId,
                        references.campusId(),
                        references.yearId(),
                        value(values, "code"))) {
            issues.add(issue("code", "Cette classe existe déjà pour le campus et l'année"));
        }
        if (!value(values, "room_code").isBlank()
                && references != null
                && optionalId(
                                "SELECT id FROM physical_room WHERE tenant_id=? AND campus_id=? AND"
                                        + " code=?",
                                tenantId,
                                references.campusId(),
                                value(values, "room_code"))
                        == null) {
            issues.add(issue("room_code", "Salle introuvable dans ce campus"));
        }
    }

    private void validatePayment(
            UUID tenantId, Map<String, String> values, List<ValidationIssue> issues) {
        requireStudent(tenantId, values, issues);
        decimal(values, "amount", issues, true);
        date(values, "paid_on", issues, true);
        iso3(values, "currency", issues, false);
    }

    private void validateOpeningBalance(
            UUID tenantId, Map<String, String> values, List<ValidationIssue> issues) {
        requireStudent(tenantId, values, issues);
        decimal(values, "amount", issues, false);
        date(values, "as_of_date", issues, true);
        iso3(values, "currency", issues, false);
    }

    private AcademicReferences academicReferences(
            UUID tenantId,
            Map<String, String> values,
            List<ValidationIssue> issues,
            boolean required) {
        String campusCode = value(values, "campus_code");
        String academicYear = value(values, "academic_year");
        if (!required && campusCode.isBlank() && academicYear.isBlank()) {
            return null;
        }
        UUID campusId =
                optionalId(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code=? AND active=TRUE",
                        tenantId,
                        campusCode);
        if (campusId == null && !campusCode.isBlank()) {
            issues.add(issue("campus_code", "Campus actif introuvable"));
        }
        UUID yearId =
                optionalId(
                        "SELECT id FROM academic_year WHERE tenant_id=? AND (code=? OR name=?)",
                        tenantId,
                        academicYear,
                        academicYear);
        if (yearId == null && !academicYear.isBlank()) {
            issues.add(issue("academic_year", "Année scolaire introuvable"));
        }
        return campusId == null || yearId == null ? null : new AcademicReferences(campusId, yearId);
    }

    private void requireStudent(
            UUID tenantId, Map<String, String> values, List<ValidationIssue> issues) {
        String identifier = value(values, "student_identifier");
        if (!identifier.isBlank()
                && optionalId(
                                """
                                SELECT s.id FROM student_profile s
                                JOIN student_identifier i ON i.student_id=s.id AND i.tenant_id=s.tenant_id
                                WHERE s.tenant_id=? AND i.identifier_type='SCHOOL' AND i.identifier_value=?
                                """,
                                tenantId,
                                identifier)
                        == null) {
            issues.add(issue("student_identifier", "Élève introuvable"));
        }
    }

    private static Map<String, Long> duplicateKeys(ImportType type, List<StoredRow> rows) {
        List<String> keys =
                switch (type) {
                    case STUDENTS ->
                            List.of("school_identifier", "guardian_email", "guardian_phone");
                    case PARENTS -> List.of("email", "phone");
                    case STAFF -> List.of("employee_number", "email", "phone");
                    case CLASSES -> List.of("code");
                    default -> List.of();
                };
        Map<String, Long> counts = new HashMap<>();
        for (StoredRow row : rows) {
            for (String key : keys) {
                String value = value(row.normalized(), key);
                if (!value.isBlank()) {
                    counts.merge(duplicateKey(key, value), 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    private static void uniqueInFile(
            String key,
            Map<String, String> values,
            Map<String, Long> duplicates,
            List<ValidationIssue> issues) {
        String current = value(values, key);
        if (!current.isBlank() && duplicates.getOrDefault(duplicateKey(key, current), 0L) > 1) {
            issues.add(issue(key, "Valeur dupliquée dans le fichier"));
        }
    }

    private static String duplicateKey(String field, String current) {
        String normalized =
                field.contains("phone")
                        ? current.replaceAll("[\\s()-]", "")
                        : current.toLowerCase(Locale.ROOT);
        return field + "\u0000" + normalized;
    }

    private void contactExists(
            UUID tenantId,
            Map<String, String> values,
            String field,
            String type,
            List<ValidationIssue> issues) {
        String current = value(values, field);
        if (current.isBlank()) {
            return;
        }
        String normalized =
                "EMAIL".equals(type)
                        ? current.toLowerCase(Locale.ROOT)
                        : current.replaceAll("[\\s()-]", "");
        if (exists(
                "SELECT count(*) FROM person_contact_method WHERE tenant_id=? AND contact_type=?"
                        + " AND normalized_value=?",
                tenantId,
                type,
                normalized)) {
            issues.add(issue(field, "Ce moyen de contact est déjà utilisé"));
        }
    }

    private static void date(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean required) {
        String value = value(values, key);
        if (value.isBlank() && !required) {
            return;
        }
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            issues.add(issue(key, "Date attendue au format AAAA-MM-JJ"));
        }
    }

    private static void email(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean required) {
        String value = value(values, key);
        if (value.isBlank() && !required) {
            return;
        }
        if (!EMAIL.matcher(value).matches()) {
            issues.add(issue(key, "Adresse e-mail invalide"));
        }
    }

    private static void iso2(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean required) {
        iso(values, key, issues, required, 2);
    }

    private static void iso3(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean required) {
        iso(values, key, issues, required, 3);
    }

    private static void iso(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean required,
            int size) {
        String value = value(values, key);
        if (value.isBlank() && !required) {
            return;
        }
        if (!value.matches("[A-Za-z]{" + size + "}")) {
            issues.add(issue(key, "Code ISO attendu sur " + size + " lettres"));
        }
    }

    private static void bool(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean required) {
        String value = value(values, key);
        if (value.isBlank() && !required) {
            return;
        }
        if (!Set.of("true", "false", "1", "0", "yes", "no", "oui", "non")
                .contains(value.toLowerCase(Locale.ROOT))) {
            issues.add(issue(key, "Booléen attendu: true/false, oui/non ou 1/0"));
        }
    }

    private static void positiveInteger(
            Map<String, String> values, String key, List<ValidationIssue> issues) {
        try {
            if (Integer.parseInt(value(values, key)) <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            issues.add(issue(key, "Entier strictement positif attendu"));
        }
    }

    private static void decimal(
            Map<String, String> values,
            String key,
            List<ValidationIssue> issues,
            boolean positive) {
        try {
            BigDecimal amount = new BigDecimal(decimalValue(value(values, key)));
            if ((positive && amount.signum() <= 0) || (!positive && amount.signum() == 0)) {
                throw new NumberFormatException();
            }
            if (amount.scale() > 2 || amount.precision() > 14) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            issues.add(
                    issue(
                            key,
                            positive
                                    ? "Montant positif avec deux décimales maximum attendu"
                                    : "Montant non nul avec deux décimales maximum attendu"));
        }
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private UUID optionalId(String sql, Object... args) {
        List<UUID> result = jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), args);
        return result.isEmpty() ? null : result.getFirst();
    }

    private List<StoredRow> storedRows(UUID tenantId, UUID jobId) {
        return jdbc.query(
                """
                SELECT id,row_number,raw_data,normalized_data,status
                FROM data_import_row WHERE job_id=? AND tenant_id=? ORDER BY row_number
                """,
                (result, row) ->
                        new StoredRow(
                                result.getObject("id", UUID.class),
                                result.getInt("row_number"),
                                readStringMap(result.getString("raw_data")),
                                readStringMap(result.getString("normalized_data")),
                                result.getString("status")),
                jobId,
                tenantId);
    }

    private ImportJobView jobView(ResultSet result) throws SQLException {
        ImportType type = ImportType.valueOf(result.getString("import_type"));
        return new ImportJobView(
                result.getObject("id", UUID.class),
                type,
                catalog.definition(type).label(),
                result.getString("file_name"),
                result.getString("file_format"),
                ImportStatus.valueOf(result.getString("status")),
                readStringList(result.getString("source_headers")),
                catalog.definition(type).fields(),
                readStringMap(result.getString("column_mapping")),
                result.getInt("total_rows"),
                result.getInt("valid_rows"),
                result.getInt("invalid_rows"),
                result.getInt("imported_rows"),
                result.getString("error_summary"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize import data", exception);
        }
    }

    private Map<String, String> readStringMap(String source) {
        try {
            return json.readValue(source, STRING_MAP);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read import mapping", exception);
        }
    }

    private List<String> readStringList(String source) {
        try {
            return json.readValue(source, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read import headers", exception);
        }
    }

    private List<ValidationIssue> readIssues(String source) {
        try {
            return json.readValue(source, ISSUE_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read import issues", exception);
        }
    }

    static String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "").strip();
    }

    static String decimalValue(String value) {
        return value.contains(",") && !value.contains(".") ? value.replace(',', '.') : value;
    }

    static boolean booleanValue(String value) {
        return Set.of("true", "1", "yes", "oui").contains(value.toLowerCase(Locale.ROOT));
    }

    private static ValidationIssue issue(String field, String message) {
        return new ValidationIssue(field, message);
    }

    private static Timestamp databaseTime(Instant instant) {
        return Timestamp.from(instant);
    }

    private record AcademicReferences(UUID campusId, UUID yearId) {}

    public record ValidationIssue(String field, String message) {}

    public record StoredRow(
            UUID id,
            int rowNumber,
            Map<String, String> raw,
            Map<String, String> normalized,
            String status) {}

    public record ImportJobView(
            UUID id,
            ImportType type,
            String typeLabel,
            String fileName,
            String fileFormat,
            ImportStatus status,
            List<String> headers,
            List<Field> fields,
            Map<String, String> mapping,
            int totalRows,
            int validRows,
            int invalidRows,
            int importedRows,
            String errorSummary,
            Instant createdAt,
            Instant updatedAt) {}

    public record ImportRowView(
            int rowNumber,
            Map<String, String> values,
            List<ValidationIssue> errors,
            String status,
            UUID targetRecordId) {}

    public record ImportPreview(ImportJobView job, List<ImportRowView> rows, boolean truncated) {}
}
