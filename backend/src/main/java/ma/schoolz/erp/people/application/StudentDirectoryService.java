package ma.schoolz.erp.people.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDirectoryService {

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public StudentDirectoryService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('student.read')")
    public List<StudentSummary> students(SchoolPrincipal actor, String search) {
        return studentPage(actor, search, 0, 100).items();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('student.read')")
    public StudentPage studentPage(SchoolPrincipal actor, String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Pagination invalide");
        String normalized = search == null ? "" : search.strip().toLowerCase(java.util.Locale.ROOT);
        List<StudentSummary> rows =
                jdbc.query(
                        """
                        SELECT s.id, p.first_name, p.last_name, p.preferred_name, p.birth_date,
                               s.status, s.photo_object_key, i.identifier_value,
                               c.name AS campus_name, cl.name AS classroom_name, y.name AS year_name
                          FROM student_profile s
                          JOIN person p ON p.id = s.person_id AND p.tenant_id = s.tenant_id
                          LEFT JOIN student_identifier i ON i.student_id = s.id
                               AND i.tenant_id = s.tenant_id AND i.primary_identifier = TRUE
                          LEFT JOIN student_enrollment e ON e.student_id = s.id
                               AND e.tenant_id = s.tenant_id AND e.status = 'ACTIVE'
                          LEFT JOIN school_campus c ON c.id = e.campus_id AND c.tenant_id = e.tenant_id
                          LEFT JOIN classroom cl ON cl.id = e.classroom_id AND cl.tenant_id = e.tenant_id
                          LEFT JOIN academic_year y ON y.id = e.academic_year_id AND y.tenant_id = e.tenant_id
                         WHERE s.tenant_id = ?
                           AND (? = '' OR LOWER(p.first_name) LIKE ? OR LOWER(p.last_name) LIKE ?
                                OR LOWER(COALESCE(i.identifier_value, '')) LIKE ?)
                         ORDER BY p.last_name, p.first_name, s.id
                         LIMIT ? OFFSET ?
                        """,
                        (result, row) -> studentSummary(result),
                        actor.tenantId(),
                        normalized,
                        "%" + normalized + "%",
                        "%" + normalized + "%",
                        "%" + normalized + "%",
                        size + 1,
                        page * size);
        boolean hasNext = rows.size() > size;
        return new StudentPage(
                page, size, hasNext, hasNext ? List.copyOf(rows.subList(0, size)) : rows);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('student.read')")
    public StudentRecord student(SchoolPrincipal actor, UUID studentId) {
        StudentIdentity identity = identity(actor.tenantId(), studentId);
        List<AddressView> addresses =
                jdbc.query(
                        """
                        SELECT address_type, line1, line2, city, region, postal_code,
                               country_code, primary_address
                          FROM person_address WHERE tenant_id = ? AND person_id = ?
                         ORDER BY primary_address DESC, created_at
                        """,
                        (rs, row) ->
                                new AddressView(
                                        rs.getString("address_type"), rs.getString("line1"),
                                        rs.getString("line2"), rs.getString("city"),
                                        rs.getString("region"), rs.getString("postal_code"),
                                        rs.getString("country_code"),
                                                rs.getBoolean("primary_address")),
                        actor.tenantId(),
                        identity.personId());
        List<GuardianView> guardians = guardians(actor.tenantId(), identity.personId());
        List<EmergencyContactView> emergencyContacts =
                emergencyContacts(actor.tenantId(), studentId);
        List<EnrollmentView> enrollments = enrollments(actor.tenantId(), studentId);
        List<IdentifierView> identifiers = identifiers(actor.tenantId(), studentId);
        List<DocumentView> documents = documents(actor.tenantId(), identity.personId());
        Map<String, String> customFields = customFields(actor.tenantId(), studentId);
        List<HistoryView> history = history(actor.tenantId(), studentId);
        return new StudentRecord(
                identity,
                addresses,
                guardians,
                emergencyContacts,
                enrollments,
                identifiers,
                customFields,
                documents,
                List.of(),
                List.of(),
                SectionAvailability.pending("finance"),
                SectionAvailability.pending("transport"),
                SectionAvailability.available("health"),
                history);
    }

    @Transactional
    @PreAuthorize("hasAuthority('student.write') and hasAuthority('person.write')")
    public StudentRecord create(SchoolPrincipal actor, CreateStudent command, String ipAddress) {
        validateAcademicScope(actor.tenantId(), command);
        if (count(
                        "SELECT count(*) FROM student_identifier WHERE tenant_id = ? AND"
                                + " identifier_type = 'SCHOOL' AND identifier_value = ?",
                        actor.tenantId(),
                        command.schoolIdentifier())
                > 0) {
            throw new StudentConflictException("Student identifier already exists");
        }
        Instant now = clock.instant();
        UUID studentPersonId = UUID.randomUUID();
        UUID guardianPersonId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        insertPerson(
                actor.tenantId(),
                studentPersonId,
                command.studentFirstName(),
                command.studentLastName(),
                command.preferredName(),
                command.birthDate(),
                command.gender(),
                command.nationalityCode(),
                now);
        jdbc.update(
                """
                INSERT INTO person_address
                    (id, tenant_id, person_id, address_type, line1, line2, city, region,
                     postal_code, country_code, primary_address, created_at)
                VALUES (?, ?, ?, 'HOME', ?, ?, ?, ?, ?, ?, TRUE, ?)
                """,
                UUID.randomUUID(),
                actor.tenantId(),
                studentPersonId,
                command.addressLine1(),
                nullable(command.addressLine2()),
                command.city(),
                nullable(command.region()),
                nullable(command.postalCode()),
                command.countryCode().toUpperCase(),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO household (id, tenant_id, name, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                householdId,
                actor.tenantId(),
                command.householdName().strip(),
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                "INSERT INTO student_profile (id, tenant_id, person_id, status, created_at,"
                        + " updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                studentId,
                actor.tenantId(),
                studentPersonId,
                databaseTime(now),
                databaseTime(now));
        jdbc.update(
                """
                INSERT INTO student_identifier
                    (id, tenant_id, student_id, identifier_type, identifier_value, issuer,
                     primary_identifier, created_at)
                VALUES (?, ?, ?, 'SCHOOL', ?, 'SchoolZ', TRUE, ?)
                """,
                UUID.randomUUID(),
                actor.tenantId(),
                studentId,
                command.schoolIdentifier().strip(),
                databaseTime(now));
        insertPerson(
                actor.tenantId(),
                guardianPersonId,
                command.guardianFirstName(),
                command.guardianLastName(),
                null,
                null,
                null,
                command.guardianNationalityCode(),
                now);
        jdbc.update(
                "INSERT INTO guardian_profile (id, tenant_id, person_id, occupation, status,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                guardianId,
                actor.tenantId(),
                guardianPersonId,
                nullable(command.guardianOccupation()),
                databaseTime(now),
                databaseTime(now));
        insertContact(
                actor.tenantId(), guardianPersonId, "EMAIL", command.guardianEmail(), true, now);
        insertContact(
                actor.tenantId(), guardianPersonId, "PHONE", command.guardianPhone(), true, now);
        insertRelationship(
                actor.tenantId(), householdId, studentPersonId, "CHILD", false, false, now);
        insertRelationship(
                actor.tenantId(),
                householdId,
                guardianPersonId,
                command.guardianRelationship(),
                true,
                command.financialResponsible(),
                now);
        jdbc.update(
                """
                INSERT INTO emergency_contact
                    (id, tenant_id, student_id, person_id, display_name, relationship_type,
                     phone, priority, authorized_pickup, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, TRUE, ?)
                """,
                UUID.randomUUID(),
                actor.tenantId(),
                studentId,
                guardianPersonId,
                command.guardianFirstName().strip() + " " + command.guardianLastName().strip(),
                command.guardianRelationship(),
                command.guardianPhone().strip(),
                databaseTime(now));
        jdbc.update(
                """
                INSERT INTO student_enrollment
                    (id, tenant_id, student_id, campus_id, academic_year_id, classroom_id,
                     status, enrolled_on, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                enrollmentId,
                actor.tenantId(),
                studentId,
                command.campusId(),
                command.academicYearId(),
                command.classroomId(),
                command.enrolledOn(),
                databaseTime(now),
                databaseTime(now));
        addHistory(actor, studentId, "STUDENT_CREATED", "Dossier élève créé", null, now);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "student.created",
                "Student",
                studentId.toString(),
                ipAddress,
                Map.of("householdId", householdId, "enrollmentId", enrollmentId),
                null,
                Map.of("studentId", studentId, "personId", studentPersonId));
        return student(actor, studentId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('student.write')")
    public StudentRecord archive(
            SchoolPrincipal actor, UUID studentId, String reason, String ipAddress) {
        StudentIdentity before = identity(actor.tenantId(), studentId);
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE student_profile SET status = 'ARCHIVED', archived_at = ?,"
                                + " archive_reason = ?, updated_at = ?, version = version + 1"
                                + " WHERE id = ? AND tenant_id = ? AND status <> 'ARCHIVED'",
                        databaseTime(now),
                        reason.strip(),
                        databaseTime(now),
                        studentId,
                        actor.tenantId());
        if (updated == 0) throw new StudentConflictException("Student is already archived");
        jdbc.update(
                "UPDATE student_enrollment SET status = 'ENDED',"
                        + " ended_on = CASE WHEN enrolled_on > ? THEN enrolled_on ELSE ? END,"
                        + " updated_at = ?, version = version + 1 WHERE tenant_id = ?"
                        + " AND student_id = ? AND status = 'ACTIVE'",
                LocalDate.now(clock),
                LocalDate.now(clock),
                databaseTime(now),
                actor.tenantId(),
                studentId);
        addHistory(actor, studentId, "STUDENT_ARCHIVED", "Dossier élève archivé", reason, now);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "student.archived",
                "Student",
                studentId.toString(),
                ipAddress,
                Map.of("reason", reason),
                before,
                Map.of("status", "ARCHIVED"));
        return student(actor, studentId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('student.write')")
    public DocumentView addDocument(
            SchoolPrincipal actor, UUID studentId, DocumentCommand command, String ipAddress) {
        StudentIdentity student = identity(actor.tenantId(), studentId);
        UUID documentId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO person_document
                    (id, tenant_id, person_id, document_type, title, object_key, status,
                     expires_on, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'VALID', ?, ?)
                """,
                documentId,
                actor.tenantId(),
                student.personId(),
                command.documentType(),
                command.title(),
                command.objectKey(),
                command.expiresOn(),
                databaseTime(now));
        addHistory(
                actor,
                studentId,
                "DOCUMENT_ADDED",
                "Document ajouté : " + command.title(),
                null,
                now);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "student.document_added",
                "Document",
                documentId.toString(),
                ipAddress,
                Map.of("studentId", studentId),
                null,
                Map.of("title", command.title(), "type", command.documentType()));
        return new DocumentView(
                documentId,
                command.documentType(),
                command.title(),
                command.objectKey(),
                "VALID",
                command.expiresOn(),
                now);
    }

    @Transactional
    @PreAuthorize("hasAuthority('student.write')")
    public StudentRecord updatePhoto(
            SchoolPrincipal actor, UUID studentId, String objectKey, String ipAddress) {
        identity(actor.tenantId(), studentId);
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE student_profile SET photo_object_key = ?, updated_at = ?,"
                        + " version = version + 1 WHERE id = ? AND tenant_id = ?",
                objectKey.strip(),
                databaseTime(now),
                studentId,
                actor.tenantId());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "student.photo_updated",
                "Student",
                studentId.toString(),
                ipAddress,
                Map.of(),
                null,
                Map.of("photoObjectKey", objectKey));
        return student(actor, studentId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('student.write')")
    public StudentRecord updateCustomFields(
            SchoolPrincipal actor, UUID studentId, Map<String, String> fields, String ipAddress) {
        identity(actor.tenantId(), studentId);
        if (fields.size() > 30) throw new StudentConflictException("Too many custom fields");
        jdbc.update(
                "DELETE FROM person_custom_field WHERE tenant_id = ? AND owner_type ="
                        + " 'STUDENT' AND owner_id = ?",
                actor.tenantId(),
                studentId);
        Instant now = clock.instant();
        fields.forEach(
                (key, value) -> {
                    if (key == null
                            || key.isBlank()
                            || key.length() > 80
                            || value == null
                            || value.length() > 2000)
                        throw new StudentConflictException("Invalid custom field");
                    jdbc.update(
                            "INSERT INTO person_custom_field (id, tenant_id, owner_type, owner_id,"
                                + " field_key, field_value, updated_at) VALUES (?, ?, 'STUDENT', ?,"
                                + " ?, ?, ?)",
                            UUID.randomUUID(),
                            actor.tenantId(),
                            studentId,
                            key.strip(),
                            value.strip(),
                            databaseTime(now));
                });
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "student.custom_fields_updated",
                "Student",
                studentId.toString(),
                ipAddress,
                Map.of("fieldCount", fields.size()),
                null,
                fields);
        return student(actor, studentId);
    }

    private StudentIdentity identity(UUID tenantId, UUID studentId) {
        List<StudentIdentity> rows =
                jdbc.query(
                        """
                        SELECT s.id, s.person_id, s.status, s.photo_object_key, s.archived_at,
                               p.first_name, p.last_name, p.preferred_name, p.birth_date, p.gender,
                               p.nationality_code
                          FROM student_profile s JOIN person p
                            ON p.id = s.person_id AND p.tenant_id = s.tenant_id
                         WHERE s.id = ? AND s.tenant_id = ?
                        """,
                        (rs, row) ->
                                new StudentIdentity(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("person_id", UUID.class),
                                        rs.getString("first_name"),
                                        rs.getString("last_name"),
                                        rs.getString("preferred_name"),
                                        rs.getObject("birth_date", LocalDate.class),
                                        rs.getString("gender"),
                                        rs.getString("nationality_code"),
                                        rs.getString("status"),
                                        rs.getString("photo_object_key"),
                                        databaseInstant(rs, "archived_at")),
                        studentId,
                        tenantId);
        if (rows.isEmpty()) throw new StudentNotFoundException();
        return rows.getFirst();
    }

    private List<GuardianView> guardians(UUID tenantId, UUID studentPersonId) {
        return jdbc.query(
                """
                SELECT gp.id, p.id AS person_id, p.first_name, p.last_name, gp.occupation,
                       r.relationship_type, r.legal_guardian, r.financial_responsible,
                       MAX(CASE WHEN cm.contact_type='EMAIL' THEN cm.contact_value END) AS email,
                       MAX(CASE WHEN cm.contact_type='PHONE' THEN cm.contact_value END) AS phone
                  FROM household_relationship child
                  JOIN household_relationship r ON r.household_id=child.household_id
                       AND r.tenant_id=child.tenant_id AND r.person_id<>child.person_id
                  JOIN person p ON p.id=r.person_id AND p.tenant_id=r.tenant_id
                  JOIN guardian_profile gp ON gp.person_id=p.id AND gp.tenant_id=p.tenant_id
                  LEFT JOIN person_contact_method cm ON cm.person_id=p.id AND cm.tenant_id=p.tenant_id
                 WHERE child.tenant_id=? AND child.person_id=?
                 GROUP BY gp.id,p.id,p.first_name,p.last_name,gp.occupation,
                          r.relationship_type,r.legal_guardian,r.financial_responsible
                 ORDER BY p.last_name,p.first_name
                """,
                (rs, row) ->
                        new GuardianView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("person_id", UUID.class),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("relationship_type"),
                                rs.getString("email"),
                                rs.getString("phone"),
                                rs.getString("occupation"),
                                rs.getBoolean("legal_guardian"),
                                rs.getBoolean("financial_responsible")),
                tenantId,
                studentPersonId);
    }

    private List<EmergencyContactView> emergencyContacts(UUID tenantId, UUID studentId) {
        return jdbc.query(
                "SELECT id,display_name,relationship_type,phone,priority,authorized_pickup FROM"
                        + " emergency_contact WHERE tenant_id=? AND student_id=? ORDER BY priority",
                (rs, row) ->
                        new EmergencyContactView(
                                rs.getObject("id", UUID.class),
                                rs.getString("display_name"),
                                rs.getString("relationship_type"),
                                rs.getString("phone"),
                                rs.getInt("priority"),
                                rs.getBoolean("authorized_pickup")),
                tenantId,
                studentId);
    }

    private List<EnrollmentView> enrollments(UUID tenantId, UUID studentId) {
        return jdbc.query(
                """
                SELECT e.id,e.status,e.enrolled_on,e.ended_on,c.id campus_id,c.name campus_name,
                       y.id year_id,y.name year_name,cl.id classroom_id,cl.name classroom_name,
                       g.id grade_id,g.name grade_name
                  FROM student_enrollment e
                  JOIN school_campus c ON c.id=e.campus_id AND c.tenant_id=e.tenant_id
                  JOIN academic_year y ON y.id=e.academic_year_id AND y.tenant_id=e.tenant_id
                  LEFT JOIN classroom cl ON cl.id=e.classroom_id AND cl.tenant_id=e.tenant_id
                  LEFT JOIN grade_level g ON g.id=cl.grade_level_id AND g.tenant_id=cl.tenant_id
                 WHERE e.tenant_id=? AND e.student_id=? ORDER BY e.enrolled_on DESC
                """,
                (rs, row) ->
                        new EnrollmentView(
                                rs.getObject("id", UUID.class),
                                rs.getString("status"),
                                rs.getObject("enrolled_on", LocalDate.class),
                                rs.getObject("ended_on", LocalDate.class),
                                rs.getObject("campus_id", UUID.class),
                                rs.getString("campus_name"),
                                rs.getObject("year_id", UUID.class),
                                rs.getString("year_name"),
                                rs.getObject("classroom_id", UUID.class),
                                rs.getString("classroom_name"),
                                rs.getObject("grade_id", UUID.class),
                                rs.getString("grade_name")),
                tenantId,
                studentId);
    }

    private List<IdentifierView> identifiers(UUID tenantId, UUID studentId) {
        return jdbc.query(
                "SELECT id,identifier_type,identifier_value,issuer,primary_identifier"
                        + " FROM student_identifier WHERE tenant_id=? AND student_id=?",
                (rs, row) ->
                        new IdentifierView(
                                rs.getObject("id", UUID.class),
                                rs.getString("identifier_type"),
                                rs.getString("identifier_value"),
                                rs.getString("issuer"),
                                rs.getBoolean("primary_identifier")),
                tenantId,
                studentId);
    }

    private List<DocumentView> documents(UUID tenantId, UUID personId) {
        return jdbc.query(
                "SELECT id,document_type,title,object_key,status,expires_on,created_at FROM"
                    + " person_document WHERE tenant_id=? AND person_id=? ORDER BY created_at DESC",
                (rs, row) ->
                        new DocumentView(
                                rs.getObject("id", UUID.class),
                                rs.getString("document_type"),
                                rs.getString("title"),
                                rs.getString("object_key"),
                                rs.getString("status"),
                                rs.getObject("expires_on", LocalDate.class),
                                databaseInstant(rs, "created_at")),
                tenantId,
                personId);
    }

    private Map<String, String> customFields(UUID tenantId, UUID studentId) {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query(
                "SELECT field_key,field_value FROM person_custom_field WHERE tenant_id=?"
                        + " AND owner_type='STUDENT' AND owner_id=? ORDER BY field_key",
                rs -> {
                    result.put(rs.getString(1), rs.getString(2));
                },
                tenantId,
                studentId);
        return Map.copyOf(result);
    }

    private List<HistoryView> history(UUID tenantId, UUID studentId) {
        return jdbc.query(
                "SELECT id,event_type,summary,details,occurred_at,actor_user_id FROM"
                        + " student_history WHERE tenant_id=? AND student_id=? ORDER BY occurred_at"
                        + " DESC",
                (rs, row) ->
                        new HistoryView(
                                rs.getObject("id", UUID.class),
                                rs.getString("event_type"),
                                rs.getString("summary"),
                                rs.getString("details"),
                                databaseInstant(rs, "occurred_at"),
                                rs.getObject("actor_user_id", UUID.class)),
                tenantId,
                studentId);
    }

    private static StudentSummary studentSummary(ResultSet rs) throws SQLException {
        return new StudentSummary(
                rs.getObject("id", UUID.class),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("preferred_name"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getString("status"),
                rs.getString("photo_object_key"),
                rs.getString("identifier_value"),
                rs.getString("campus_name"),
                rs.getString("classroom_name"),
                rs.getString("year_name"));
    }

    private void validateAcademicScope(UUID tenantId, CreateStudent command) {
        if (count(
                        "SELECT count(*) FROM school_campus WHERE id=? AND tenant_id=? AND"
                                + " active=TRUE",
                        command.campusId(),
                        tenantId)
                == 0) throw new StudentConflictException("Campus not found");
        if (count(
                        "SELECT count(*) FROM academic_year WHERE id=? AND tenant_id=?",
                        command.academicYearId(),
                        tenantId)
                == 0) throw new StudentConflictException("Academic year not found");
        if (command.classroomId() != null
                && count(
                                "SELECT count(*) FROM classroom WHERE id=?"
                                        + " AND tenant_id=? AND campus_id=? AND academic_year_id=?",
                                command.classroomId(),
                                tenantId,
                                command.campusId(),
                                command.academicYearId())
                        == 0)
            throw new StudentConflictException("Classroom does not match campus and year");
    }

    private void insertPerson(
            UUID tenantId,
            UUID id,
            String first,
            String last,
            String preferred,
            LocalDate birth,
            String gender,
            String nationality,
            Instant now) {
        jdbc.update(
                "INSERT INTO person (id,tenant_id,first_name,last_name,preferred_name,birth_date,"
                        + "gender,nationality_code,status,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?,?)",
                id,
                tenantId,
                first.strip(),
                last.strip(),
                nullable(preferred),
                birth,
                nullable(gender),
                nationality == null ? null : nationality.toUpperCase(),
                databaseTime(now),
                databaseTime(now));
    }

    private void insertContact(
            UUID tenantId, UUID personId, String type, String value, boolean primary, Instant now) {
        if (value == null || value.isBlank()) return;
        String clean = value.strip();
        jdbc.update(
                "INSERT INTO person_contact_method"
                    + " (id,tenant_id,person_id,contact_type,contact_value,normalized_value,primary_contact,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                personId,
                type,
                clean,
                clean.toLowerCase(java.util.Locale.ROOT),
                primary,
                databaseTime(now));
    }

    private void insertRelationship(
            UUID tenantId,
            UUID householdId,
            UUID personId,
            String type,
            boolean legal,
            boolean financial,
            Instant now) {
        jdbc.update(
                "INSERT INTO household_relationship (id,tenant_id,household_id,person_id,"
                    + "relationship_type,legal_guardian,financial_responsible,lives_with_student,created_at)"
                    + " VALUES (?,?,?,?,?,?,?,TRUE,?)",
                UUID.randomUUID(),
                tenantId,
                householdId,
                personId,
                type,
                legal,
                financial,
                databaseTime(now));
    }

    private void addHistory(
            SchoolPrincipal actor,
            UUID studentId,
            String type,
            String summary,
            String details,
            Instant now) {
        jdbc.update(
                "INSERT INTO student_history (id,tenant_id,student_id,event_type,summary,details,"
                        + "occurred_at,actor_user_id) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.tenantId(),
                studentId,
                type,
                summary,
                details,
                databaseTime(now),
                actor.accountId());
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static Timestamp databaseTime(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant databaseInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record CreateStudent(
            String studentFirstName,
            String studentLastName,
            String preferredName,
            LocalDate birthDate,
            String gender,
            String nationalityCode,
            String schoolIdentifier,
            String householdName,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String guardianFirstName,
            String guardianLastName,
            String guardianRelationship,
            String guardianEmail,
            String guardianPhone,
            String guardianOccupation,
            String guardianNationalityCode,
            boolean financialResponsible,
            UUID campusId,
            UUID academicYearId,
            UUID classroomId,
            LocalDate enrolledOn) {}

    public record DocumentCommand(
            String documentType, String title, String objectKey, LocalDate expiresOn) {}

    public record StudentSummary(
            UUID id,
            String firstName,
            String lastName,
            String preferredName,
            LocalDate birthDate,
            String status,
            String photoObjectKey,
            String schoolIdentifier,
            String campusName,
            String classroomName,
            String academicYearName) {}

    public record StudentPage(int page, int size, boolean hasNext, List<StudentSummary> items) {}

    public record StudentIdentity(
            UUID id,
            UUID personId,
            String firstName,
            String lastName,
            String preferredName,
            LocalDate birthDate,
            String gender,
            String nationalityCode,
            String status,
            String photoObjectKey,
            Instant archivedAt) {}

    public record AddressView(
            String type,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            boolean primary) {}

    public record GuardianView(
            UUID id,
            UUID personId,
            String firstName,
            String lastName,
            String relationship,
            String email,
            String phone,
            String occupation,
            boolean legalGuardian,
            boolean financialResponsible) {}

    public record EmergencyContactView(
            UUID id,
            String displayName,
            String relationship,
            String phone,
            int priority,
            boolean authorizedPickup) {}

    public record EnrollmentView(
            UUID id,
            String status,
            LocalDate enrolledOn,
            LocalDate endedOn,
            UUID campusId,
            String campusName,
            UUID academicYearId,
            String academicYearName,
            UUID classroomId,
            String classroomName,
            UUID gradeLevelId,
            String gradeLevelName) {}

    public record IdentifierView(
            UUID id, String type, String value, String issuer, boolean primary) {}

    public record DocumentView(
            UUID id,
            String type,
            String title,
            String objectKey,
            String status,
            LocalDate expiresOn,
            Instant createdAt) {}

    public record HistoryView(
            UUID id,
            String type,
            String summary,
            String details,
            Instant occurredAt,
            UUID actorUserId) {}

    public record SectionAvailability(String status, String module) {
        static SectionAvailability pending(String module) {
            return new SectionAvailability("PENDING_MODULE", module);
        }

        static SectionAvailability available(String module) {
            return new SectionAvailability("AVAILABLE", module);
        }
    }

    public record StudentRecord(
            StudentIdentity identity,
            List<AddressView> addresses,
            List<GuardianView> guardians,
            List<EmergencyContactView> emergencyContacts,
            List<EnrollmentView> enrollments,
            List<IdentifierView> identifiers,
            Map<String, String> customFields,
            List<DocumentView> documents,
            List<Object> attendance,
            List<Object> grades,
            SectionAvailability finance,
            SectionAvailability transport,
            SectionAvailability health,
            List<HistoryView> history) {}
}
