package ma.schoolz.erp.dataimport.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.dataimport.application.ImportWorkflowService.ImportJobView;
import ma.schoolz.erp.dataimport.application.ImportWorkflowService.StoredRow;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import ma.schoolz.erp.people.StudentImportOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportExecutionService {

    private final JdbcTemplate jdbc;
    private final ImportWorkflowService workflow;
    private final StudentImportOperations students;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public ImportExecutionService(
            JdbcTemplate jdbc,
            ImportWorkflowService workflow,
            StudentImportOperations students,
            IdentityAuditTrail audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.workflow = workflow;
        this.students = students;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void execute(SchoolPrincipal actor, UUID jobId, String ipAddress) {
        ImportJobView job = workflow.executableJob(actor.tenantId(), jobId);
        if (job.status() != ImportStatus.VALIDATED || job.invalidRows() > 0) {
            throw new ImportValidationException(
                    "Toutes les lignes doivent être valides avant l'import");
        }
        List<StoredRow> rows = workflow.executableRows(actor.tenantId(), jobId);
        Instant now = clock.instant();
        int imported = 0;
        for (StoredRow row : rows) {
            if (!"VALID".equals(row.status())) {
                throw new ImportValidationException("La validation doit être relancée");
            }
            UUID targetId = executeRow(actor, job, row, ipAddress, now);
            jdbc.update(
                    """
                    UPDATE data_import_row
                    SET status='IMPORTED',target_record_id=?,updated_at=?
                    WHERE id=? AND tenant_id=?
                    """,
                    targetId,
                    databaseTime(now),
                    row.id(),
                    actor.tenantId());
            imported++;
        }
        jdbc.update(
                """
                UPDATE data_import_job
                SET status='IMPORTED',imported_rows=?,error_summary=NULL,
                    updated_at=?,version=version+1
                WHERE id=? AND tenant_id=?
                """,
                imported,
                databaseTime(now),
                jobId,
                actor.tenantId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "data-import.completed",
                "DataImportJob",
                jobId.toString(),
                ipAddress,
                Map.of("type", job.type().name(), "importedRows", imported),
                null,
                Map.of("status", "IMPORTED"));
    }

    private UUID executeRow(
            SchoolPrincipal actor,
            ImportJobView job,
            StoredRow row,
            String ipAddress,
            Instant now) {
        Map<String, String> values = row.normalized();
        return switch (job.type()) {
            case STUDENTS -> importStudent(actor, values, ipAddress);
            case PARENTS -> importParent(actor.tenantId(), values, now);
            case STAFF -> importStaff(actor.tenantId(), values, now);
            case CLASSES -> importClassroom(actor.tenantId(), values, now);
            case PAYMENTS ->
                    importPayment(actor.tenantId(), job.id(), row.rowNumber(), values, now);
            case OPENING_BALANCES ->
                    importOpeningBalance(actor.tenantId(), job.id(), row.rowNumber(), values, now);
        };
    }

    private UUID importStudent(
            SchoolPrincipal actor, Map<String, String> values, String ipAddress) {
        UUID campusId =
                requiredId(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code=? AND active=TRUE",
                        actor.tenantId(),
                        value(values, "campus_code"));
        UUID yearId =
                requiredId(
                        "SELECT id FROM academic_year WHERE tenant_id=? AND (code=? OR name=?)",
                        actor.tenantId(),
                        value(values, "academic_year"),
                        value(values, "academic_year"));
        UUID classroomId = null;
        if (!value(values, "classroom_code").isBlank()) {
            classroomId =
                    requiredId(
                            "SELECT id FROM classroom WHERE tenant_id=? AND campus_id=? AND"
                                    + " academic_year_id=? AND code=?",
                            actor.tenantId(),
                            campusId,
                            yearId,
                            value(values, "classroom_code"));
        }
        return students.create(
                actor,
                new StudentImportOperations.ImportedStudent(
                        value(values, "first_name"),
                        value(values, "last_name"),
                        nullable(values, "preferred_name"),
                        LocalDate.parse(value(values, "birth_date")),
                        nullable(values, "gender"),
                        upperNullable(values, "nationality_code"),
                        value(values, "school_identifier"),
                        value(values, "household_name"),
                        value(values, "address_line1"),
                        nullable(values, "address_line2"),
                        value(values, "city"),
                        nullable(values, "region"),
                        nullable(values, "postal_code"),
                        value(values, "country_code").toUpperCase(Locale.ROOT),
                        value(values, "guardian_first_name"),
                        value(values, "guardian_last_name"),
                        value(values, "guardian_relationship").toUpperCase(Locale.ROOT),
                        value(values, "guardian_email"),
                        value(values, "guardian_phone"),
                        nullable(values, "guardian_occupation"),
                        upperNullable(values, "guardian_nationality_code"),
                        ImportWorkflowService.booleanValue(value(values, "financial_responsible")),
                        campusId,
                        yearId,
                        classroomId,
                        LocalDate.parse(value(values, "enrolled_on"))),
                ipAddress);
    }

    private UUID importParent(UUID tenantId, Map<String, String> values, Instant now) {
        StudentHousehold target = studentHousehold(tenantId, value(values, "student_identifier"));
        UUID personId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        insertPerson(
                tenantId,
                personId,
                value(values, "first_name"),
                value(values, "last_name"),
                upperNullable(values, "nationality_code"),
                now);
        jdbc.update(
                """
                INSERT INTO guardian_profile
                    (id,tenant_id,person_id,occupation,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                guardianId,
                tenantId,
                personId,
                nullable(values, "occupation"),
                databaseTime(now),
                databaseTime(now));
        insertContact(tenantId, personId, "EMAIL", nullable(values, "email"), now);
        insertContact(tenantId, personId, "PHONE", nullable(values, "phone"), now);
        jdbc.update(
                """
                INSERT INTO household_relationship
                    (id,tenant_id,household_id,person_id,relationship_type,legal_guardian,
                     financial_responsible,lives_with_student,created_at)
                VALUES (?, ?, ?, ?, ?, TRUE, ?, TRUE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                target.householdId(),
                personId,
                value(values, "relationship").toUpperCase(Locale.ROOT),
                ImportWorkflowService.booleanValue(value(values, "financial_responsible")),
                databaseTime(now));
        return guardianId;
    }

    private UUID importStaff(UUID tenantId, Map<String, String> values, Instant now) {
        UUID personId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        insertPerson(
                tenantId,
                personId,
                value(values, "first_name"),
                value(values, "last_name"),
                upperNullable(values, "nationality_code"),
                now);
        insertContact(tenantId, personId, "EMAIL", nullable(values, "email"), now);
        insertContact(tenantId, personId, "PHONE", nullable(values, "phone"), now);
        jdbc.update(
                """
                INSERT INTO staff_profile
                    (id,tenant_id,person_id,employee_number,job_title,employment_status,
                     hired_on,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                staffId,
                tenantId,
                personId,
                value(values, "employee_number"),
                value(values, "job_title"),
                LocalDate.parse(value(values, "hired_on")),
                databaseTime(now),
                databaseTime(now));
        return staffId;
    }

    private UUID importClassroom(UUID tenantId, Map<String, String> values, Instant now) {
        UUID campusId =
                requiredId(
                        "SELECT id FROM school_campus WHERE tenant_id=? AND code=? AND active=TRUE",
                        tenantId,
                        value(values, "campus_code"));
        UUID yearId =
                requiredId(
                        "SELECT id FROM academic_year WHERE tenant_id=? AND (code=? OR name=?)",
                        tenantId,
                        value(values, "academic_year"),
                        value(values, "academic_year"));
        UUID gradeId =
                requiredId(
                        "SELECT id FROM grade_level WHERE tenant_id=? AND code=?",
                        tenantId,
                        value(values, "grade_code"));
        UUID roomId = null;
        if (!value(values, "room_code").isBlank()) {
            roomId =
                    requiredId(
                            "SELECT id FROM physical_room WHERE tenant_id=? AND campus_id=? AND"
                                    + " code=?",
                            tenantId,
                            campusId,
                            value(values, "room_code"));
        }
        UUID classroomId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO classroom
                    (id,tenant_id,campus_id,academic_year_id,grade_level_id,physical_room_id,
                     code,name,capacity,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                classroomId,
                tenantId,
                campusId,
                yearId,
                gradeId,
                roomId,
                value(values, "code"),
                value(values, "name"),
                Integer.parseInt(value(values, "capacity")),
                databaseTime(now),
                databaseTime(now));
        return classroomId;
    }

    private UUID importPayment(
            UUID tenantId, UUID jobId, int rowNumber, Map<String, String> values, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO imported_legacy_payment
                    (id,tenant_id,import_job_id,source_row_number,student_id,amount,paid_on,
                     payment_method,external_reference,currency_code,reconciliation_status,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """,
                id,
                tenantId,
                jobId,
                rowNumber,
                studentId(tenantId, value(values, "student_identifier")),
                new BigDecimal(ImportWorkflowService.decimalValue(value(values, "amount"))),
                LocalDate.parse(value(values, "paid_on")),
                value(values, "payment_method").toUpperCase(Locale.ROOT),
                nullable(values, "external_reference"),
                currency(values),
                databaseTime(now));
        return id;
    }

    private UUID importOpeningBalance(
            UUID tenantId, UUID jobId, int rowNumber, Map<String, String> values, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO imported_opening_balance
                    (id,tenant_id,import_job_id,source_row_number,student_id,amount,as_of_date,
                     description,currency_code,reconciliation_status,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """,
                id,
                tenantId,
                jobId,
                rowNumber,
                studentId(tenantId, value(values, "student_identifier")),
                new BigDecimal(ImportWorkflowService.decimalValue(value(values, "amount"))),
                LocalDate.parse(value(values, "as_of_date")),
                nullable(values, "description"),
                currency(values),
                databaseTime(now));
        return id;
    }

    private void insertPerson(
            UUID tenantId,
            UUID personId,
            String firstName,
            String lastName,
            String nationality,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO person
                    (id,tenant_id,first_name,last_name,nationality_code,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                personId,
                tenantId,
                firstName,
                lastName,
                nationality,
                databaseTime(now),
                databaseTime(now));
    }

    private void insertContact(
            UUID tenantId, UUID personId, String type, String contact, Instant now) {
        if (contact == null) {
            return;
        }
        String normalized =
                "EMAIL".equals(type)
                        ? contact.toLowerCase(Locale.ROOT)
                        : contact.replaceAll("[\\s()-]", "");
        jdbc.update(
                """
                INSERT INTO person_contact_method
                    (id,tenant_id,person_id,contact_type,contact_value,normalized_value,
                     primary_contact,created_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                personId,
                type,
                contact,
                normalized,
                databaseTime(now));
    }

    private StudentHousehold studentHousehold(UUID tenantId, String identifier) {
        List<StudentHousehold> results =
                jdbc.query(
                        """
                        SELECT s.id,h.id AS household_id
                        FROM student_profile s
                        JOIN student_identifier i ON i.student_id=s.id AND i.tenant_id=s.tenant_id
                        JOIN household_relationship r ON r.person_id=s.person_id AND r.tenant_id=s.tenant_id
                        JOIN household h ON h.id=r.household_id AND h.tenant_id=r.tenant_id
                        WHERE s.tenant_id=? AND i.identifier_type='SCHOOL'
                          AND i.identifier_value=? AND r.relationship_type='CHILD'
                        """,
                        (result, row) ->
                                new StudentHousehold(
                                        result.getObject("id", UUID.class),
                                        result.getObject("household_id", UUID.class)),
                        tenantId,
                        identifier);
        if (results.isEmpty()) {
            throw new ImportValidationException("Élève ou foyer introuvable");
        }
        return results.getFirst();
    }

    private UUID studentId(UUID tenantId, String identifier) {
        return requiredId(
                """
                SELECT s.id FROM student_profile s
                JOIN student_identifier i ON i.student_id=s.id AND i.tenant_id=s.tenant_id
                WHERE s.tenant_id=? AND i.identifier_type='SCHOOL' AND i.identifier_value=?
                """,
                tenantId,
                identifier);
    }

    private UUID requiredId(String sql, Object... args) {
        List<UUID> results =
                jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
        if (results.isEmpty()) {
            throw new ImportValidationException(
                    "Une référence métier a changé depuis la validation");
        }
        return results.getFirst();
    }

    private static String currency(Map<String, String> values) {
        String currency = value(values, "currency");
        return currency.isBlank() ? "MAD" : currency.toUpperCase(Locale.ROOT);
    }

    private static String nullable(Map<String, String> values, String key) {
        String value = value(values, key);
        return value.isBlank() ? null : value;
    }

    private static String upperNullable(Map<String, String> values, String key) {
        String value = nullable(values, key);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String value(Map<String, String> values, String key) {
        return ImportWorkflowService.value(values, key);
    }

    private static Timestamp databaseTime(Instant instant) {
        return Timestamp.from(instant);
    }

    private record StudentHousehold(UUID studentId, UUID householdId) {}
}
