package ma.schoolz.erp.childcare.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.enrollment.PortalStudentAccess;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChildcareService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Casablanca");
    private static final Set<String> EVENT_TYPES =
            Set.of(
                    "MEAL",
                    "BOTTLE",
                    "SLEEP",
                    "DIAPER",
                    "TOILET",
                    "ACTIVITY",
                    "MOOD",
                    "OBSERVATION",
                    "INCIDENT",
                    "TEMPERATURE",
                    "MEDICATION");
    private static final Set<String> MOODS = Set.of("HAPPY", "CALM", "TIRED", "SENSITIVE", "UPSET");

    private final JdbcTemplate jdbc;
    private final PortalStudentAccess portalStudents;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public ChildcareService(
            JdbcTemplate jdbc,
            PortalStudentAccess portalStudents,
            IdentityAuditTrail audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.portalStudents = portalStudents;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('childcare.read')")
    public Dashboard dashboard(SchoolPrincipal actor, LocalDate date, UUID groupId) {
        LocalDate selectedDate = date == null ? today() : date;
        List<GroupView> groups = groups(actor);
        String profilesSql =
                """
                SELECT cp.id FROM childcare_profile cp
                JOIN childcare_group cg ON cg.id=cp.group_id AND cg.tenant_id=cp.tenant_id
                WHERE cp.tenant_id=? AND cp.active=TRUE
                """
                        + (groupId == null ? "" : " AND cp.group_id=?")
                        + " ORDER BY cg.name,cp.created_at";
        Object[] profileArguments =
                groupId == null
                        ? new Object[] {actor.tenantId()}
                        : new Object[] {actor.tenantId(), groupId};
        List<UUID> profileIds =
                jdbc.query(
                        profilesSql,
                        (result, row) -> result.getObject(1, UUID.class),
                        profileArguments);
        List<ChildView> children =
                profileIds.stream()
                        .map(profileId -> child(actor.tenantId(), profileId, selectedDate))
                        .toList();
        return new Dashboard(selectedDate, groups, children, policy(actor.tenantId(), groupId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('childcare.read')")
    public List<GroupView> groups(SchoolPrincipal actor) {
        return jdbc.query(
                """
                SELECT cg.id,cg.code,cg.name,cg.campus_id,cg.min_age_months,cg.max_age_months,
                       cg.capacity,CONCAT(p.first_name,' ',p.last_name) AS educator_name,
                       (SELECT count(*) FROM childcare_profile cp
                         WHERE cp.tenant_id=cg.tenant_id AND cp.group_id=cg.id AND cp.active=TRUE) AS child_count
                FROM childcare_group cg
                LEFT JOIN staff_profile sp ON sp.id=cg.reference_educator_id AND sp.tenant_id=cg.tenant_id
                LEFT JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                WHERE cg.tenant_id=? AND cg.active=TRUE ORDER BY cg.name
                """,
                (result, row) ->
                        new GroupView(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("name"),
                                result.getObject("campus_id", UUID.class),
                                result.getInt("min_age_months"),
                                result.getInt("max_age_months"),
                                result.getInt("capacity"),
                                result.getString("educator_name"),
                                result.getInt("child_count")),
                actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('childcare.write')")
    public ChildView createProfile(
            SchoolPrincipal actor, ProfileCommand command, String ipAddress) {
        requireReference("student_profile", actor.tenantId(), command.studentId());
        requireReference("childcare_group", actor.tenantId(), command.groupId());
        if (command.expectedArrivalTime() == null
                || command.expectedDepartureTime() == null
                || !command.expectedArrivalTime().isBefore(command.expectedDepartureTime())) {
            throw new ChildcareValidationException(
                    "Les horaires d'arrivée et de départ sont invalides");
        }
        if (command.pickupPeople() == null
                || command.pickupPeople().stream()
                        .noneMatch(person -> "AUTHORIZED".equals(person.authorizationStatus()))) {
            throw new ChildcareValidationException(
                    "Au moins une personne autorisée à récupérer l'enfant est requise");
        }
        UUID profileId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO childcare_profile
                        (id,tenant_id,student_id,group_id,expected_arrival_time,
                         expected_departure_time,meal_instructions,bottle_instructions,
                         sleep_instructions,toilet_stage,active,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """,
                    profileId,
                    actor.tenantId(),
                    command.studentId(),
                    command.groupId(),
                    command.expectedArrivalTime(),
                    command.expectedDepartureTime(),
                    nullable(command.mealInstructions()),
                    nullable(command.bottleInstructions()),
                    nullable(command.sleepInstructions()),
                    nullable(command.toiletStage()),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new ChildcareValidationException(
                    "Cet enfant possède déjà un profil petite enfance");
        }
        for (PickupCommand person : command.pickupPeople()) {
            addPickup(actor.tenantId(), profileId, person, now);
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "childcare.profile.created",
                "ChildcareProfile",
                profileId.toString(),
                ipAddress,
                Map.of("studentId", command.studentId(), "groupId", command.groupId()),
                null,
                command);
        return child(actor.tenantId(), profileId, today());
    }

    @Transactional
    @PreAuthorize("hasAuthority('childcare.write')")
    public ChildView arrival(
            SchoolPrincipal actor, UUID studentId, TimeCommand command, String ipAddress) {
        ProfileRow profile = profileByStudent(actor.tenantId(), studentId);
        Instant at = command == null || command.at() == null ? clock.instant() : command.at();
        UUID logId = log(actor.tenantId(), profile.id(), date(at), at);
        requireDraft(actor.tenantId(), logId);
        jdbc.update(
                "UPDATE childcare_daily_log SET arrival_at=?,updated_at=?,version=version+1"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(at),
                timestamp(clock.instant()),
                actor.tenantId(),
                logId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "childcare.arrival.recorded",
                "ChildcareDailyLog",
                logId.toString(),
                ipAddress,
                Map.of("studentId", studentId),
                null,
                Map.of("at", at));
        return child(actor.tenantId(), profile.id(), date(at));
    }

    @Transactional
    @PreAuthorize("hasAuthority('childcare.write')")
    public ChildView departure(
            SchoolPrincipal actor, UUID studentId, DepartureCommand command, String ipAddress) {
        if (command == null || command.pickupPersonId() == null) {
            throw new ChildcareValidationException("La personne qui récupère l'enfant est requise");
        }
        ProfileRow profile = profileByStudent(actor.tenantId(), studentId);
        int allowed =
                count(
                        """
                        SELECT count(*) FROM childcare_pickup_person
                        WHERE tenant_id=? AND id=? AND childcare_profile_id=?
                          AND authorization_status='AUTHORIZED'
                        """,
                        actor.tenantId(),
                        command.pickupPersonId(),
                        profile.id());
        if (allowed == 0) {
            throw new ChildcareValidationException(
                    "Cette personne n'est pas autorisée à récupérer l'enfant");
        }
        Instant at = command.at() == null ? clock.instant() : command.at();
        UUID logId = log(actor.tenantId(), profile.id(), date(at), at);
        requireDraft(actor.tenantId(), logId);
        jdbc.update(
                """
                UPDATE childcare_daily_log SET departure_at=?,departure_pickup_person_id=?,
                    updated_at=?,version=version+1 WHERE tenant_id=? AND id=?
                """,
                timestamp(at),
                command.pickupPersonId(),
                timestamp(clock.instant()),
                actor.tenantId(),
                logId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "childcare.departure.recorded",
                "ChildcareDailyLog",
                logId.toString(),
                ipAddress,
                Map.of("studentId", studentId, "pickupPersonId", command.pickupPersonId()),
                null,
                Map.of("at", at));
        return child(actor.tenantId(), profile.id(), date(at));
    }

    @Transactional
    @PreAuthorize("hasAuthority('childcare.write')")
    public ChildView addEvent(
            SchoolPrincipal actor, UUID studentId, EventCommand command, String ipAddress) {
        if (command == null || command.eventType() == null) {
            throw new ChildcareValidationException("Le type d'événement est requis");
        }
        String eventType = command.eventType().strip().toUpperCase(Locale.ROOT);
        if (!EVENT_TYPES.contains(eventType)) {
            throw new ChildcareValidationException("Type d'événement petite enfance invalide");
        }
        ProfileRow profile = profileByStudent(actor.tenantId(), studentId);
        Instant occurredAt = command.occurredAt() == null ? clock.instant() : command.occurredAt();
        validateEvent(actor, profile, eventType, command, occurredAt);
        UUID logId = log(actor.tenantId(), profile.id(), date(occurredAt), occurredAt);
        requireDraft(actor.tenantId(), logId);
        UUID eventId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO childcare_daily_event
                    (id,tenant_id,daily_log_id,event_type,occurred_at,ended_at,label,quantity,
                     unit,outcome,notes,temperature_celsius,incident_severity,medication_name,
                     medication_dose,authorization_reference,created_by_user_id,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventId,
                actor.tenantId(),
                logId,
                eventType,
                timestamp(occurredAt),
                command.endedAt() == null ? null : timestamp(command.endedAt()),
                nullable(command.label()),
                command.quantity(),
                nullable(command.unit()),
                nullable(command.outcome()),
                nullable(command.notes()),
                command.temperatureCelsius(),
                nullable(command.incidentSeverity()),
                nullable(command.medicationName()),
                nullable(command.medicationDose()),
                nullable(command.authorizationReference()),
                actor.accountId(),
                timestamp(clock.instant()));
        if ("MOOD".equals(eventType)) {
            jdbc.update(
                    "UPDATE childcare_daily_log SET mood=?,updated_at=? WHERE tenant_id=? AND id=?",
                    command.outcome().strip().toUpperCase(Locale.ROOT),
                    timestamp(clock.instant()),
                    actor.tenantId(),
                    logId);
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "childcare.event.recorded",
                "ChildcareDailyEvent",
                eventId.toString(),
                ipAddress,
                Map.of("studentId", studentId, "eventType", eventType),
                null,
                Map.of("occurredAt", occurredAt));
        return child(actor.tenantId(), profile.id(), date(occurredAt));
    }

    @Transactional
    @PreAuthorize("hasAuthority('childcare.publish')")
    public ChildView publish(
            SchoolPrincipal actor, UUID studentId, PublishCommand command, String ipAddress) {
        ProfileRow profile = profileByStudent(actor.tenantId(), studentId);
        LocalDate selectedDate =
                command == null || command.date() == null ? today() : command.date();
        UUID logId = existingLog(actor.tenantId(), profile.id(), selectedDate);
        if (count(
                        "SELECT count(*) FROM childcare_daily_log WHERE tenant_id=? AND id=? AND"
                                + " arrival_at IS NOT NULL",
                        actor.tenantId(),
                        logId)
                == 0) {
            throw new ChildcareValidationException(
                    "L'arrivée doit être enregistrée avant la publication");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE childcare_daily_log SET summary=?,status='PUBLISHED',published_at=?,
                    published_by_user_id=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=? AND status='DRAFT'
                """,
                nullable(command.summary()),
                timestamp(now),
                actor.accountId(),
                timestamp(now),
                actor.tenantId(),
                logId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "childcare.daily-report.published",
                "ChildcareDailyLog",
                logId.toString(),
                ipAddress,
                Map.of("studentId", studentId, "date", selectedDate),
                null,
                Map.of("status", "PUBLISHED"));
        return child(actor.tenantId(), profile.id(), selectedDate);
    }

    @Transactional(readOnly = true)
    public List<DailyReport> parentReports(PortalIdentity parent, LocalDate date) {
        Set<UUID> studentIds = portalStudents.linkedStudentIds(parent);
        if (studentIds.isEmpty()) return List.of();
        LocalDate selectedDate = date == null ? today() : date;
        return studentIds.stream()
                .map(studentId -> publishedReport(parent.tenantId(), studentId, selectedDate))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private ChildView child(UUID tenantId, UUID profileId, LocalDate date) {
        ProfileSummary summary =
                jdbc
                        .query(
                                """
                                SELECT cp.id,cp.student_id,cp.group_id,cp.expected_arrival_time,
                                       cp.expected_departure_time,cp.meal_instructions,
                                       cp.bottle_instructions,cp.sleep_instructions,cp.toilet_stage,
                                       CONCAT(p.first_name,' ',p.last_name) AS student_name,
                                       p.birth_date,cg.name AS group_name
                                FROM childcare_profile cp
                                JOIN student_profile s ON s.id=cp.student_id AND s.tenant_id=cp.tenant_id
                                JOIN person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id
                                JOIN childcare_group cg ON cg.id=cp.group_id AND cg.tenant_id=cp.tenant_id
                                WHERE cp.tenant_id=? AND cp.id=?
                                """,
                                (result, row) -> profileSummary(result),
                                tenantId,
                                profileId)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () -> new ChildcareNotFoundException("Profil crèche introuvable"));
        List<PickupView> pickupPeople = pickupPeople(tenantId, profileId);
        DailyLogView daily = dailyLog(tenantId, profileId, date);
        return new ChildView(
                summary.id(),
                summary.studentId(),
                summary.studentName(),
                summary.birthDate(),
                summary.groupId(),
                summary.groupName(),
                summary.expectedArrivalTime(),
                summary.expectedDepartureTime(),
                summary.mealInstructions(),
                summary.bottleInstructions(),
                summary.sleepInstructions(),
                summary.toiletStage(),
                pickupPeople,
                daily);
    }

    private DailyLogView dailyLog(UUID tenantId, UUID profileId, LocalDate date) {
        List<DailyLogView> rows =
                jdbc.query(
                        """
                        SELECT dl.id,dl.log_date,dl.arrival_at,dl.departure_at,dl.mood,dl.summary,
                               dl.status,dl.published_at,pp.display_name AS pickup_name
                        FROM childcare_daily_log dl
                        LEFT JOIN childcare_pickup_person pp ON pp.id=dl.departure_pickup_person_id
                             AND pp.tenant_id=dl.tenant_id
                        WHERE dl.tenant_id=? AND dl.childcare_profile_id=? AND dl.log_date=?
                        """,
                        (result, row) ->
                                new DailyLogView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("log_date", LocalDate.class),
                                        instant(result, "arrival_at"),
                                        instant(result, "departure_at"),
                                        result.getString("pickup_name"),
                                        result.getString("mood"),
                                        result.getString("summary"),
                                        result.getString("status"),
                                        instant(result, "published_at"),
                                        events(tenantId, result.getObject("id", UUID.class))),
                        tenantId,
                        profileId,
                        date);
        return rows.isEmpty()
                ? new DailyLogView(
                        null, date, null, null, null, null, null, "NOT_STARTED", null, List.of())
                : rows.getFirst();
    }

    private List<EventView> events(UUID tenantId, UUID logId) {
        return jdbc.query(
                """
                SELECT id,event_type,occurred_at,ended_at,label,quantity,unit,outcome,notes,
                       temperature_celsius,incident_severity,medication_name,medication_dose
                FROM childcare_daily_event WHERE tenant_id=? AND daily_log_id=?
                ORDER BY occurred_at,id
                """,
                (result, row) ->
                        new EventView(
                                result.getObject("id", UUID.class),
                                result.getString("event_type"),
                                result.getTimestamp("occurred_at").toInstant(),
                                instant(result, "ended_at"),
                                result.getString("label"),
                                result.getBigDecimal("quantity"),
                                result.getString("unit"),
                                result.getString("outcome"),
                                result.getString("notes"),
                                result.getBigDecimal("temperature_celsius"),
                                result.getString("incident_severity"),
                                result.getString("medication_name"),
                                result.getString("medication_dose")),
                tenantId,
                logId);
    }

    private List<PickupView> pickupPeople(UUID tenantId, UUID profileId) {
        return jdbc.query(
                """
                SELECT id,display_name,relationship_type,phone,authorization_status,
                       identity_reference,notes FROM childcare_pickup_person
                WHERE tenant_id=? AND childcare_profile_id=? ORDER BY authorization_status,display_name
                """,
                (result, row) ->
                        new PickupView(
                                result.getObject("id", UUID.class),
                                result.getString("display_name"),
                                result.getString("relationship_type"),
                                result.getString("phone"),
                                result.getString("authorization_status"),
                                result.getString("identity_reference"),
                                result.getString("notes")),
                tenantId,
                profileId);
    }

    private java.util.Optional<DailyReport> publishedReport(
            UUID tenantId, UUID studentId, LocalDate date) {
        ProfileRow profile = nullableProfileByStudent(tenantId, studentId);
        if (profile == null) return java.util.Optional.empty();
        ChildView child = child(tenantId, profile.id(), date);
        if (!"PUBLISHED".equals(child.dailyLog().status())) return java.util.Optional.empty();
        return java.util.Optional.of(
                new DailyReport(
                        child.studentId(),
                        child.studentName(),
                        child.groupName(),
                        child.dailyLog()));
    }

    private PolicyView policy(UUID tenantId, UUID groupId) {
        String sql =
                """
                SELECT p.medication_recording_enabled,p.medication_authorization_required,
                       p.instructions
                FROM childcare_policy p
                JOIN childcare_group g ON g.campus_id=p.campus_id AND g.tenant_id=p.tenant_id
                WHERE p.tenant_id=?
                """
                        + (groupId == null
                                ? " ORDER BY p.created_at FETCH FIRST 1 ROWS ONLY"
                                : " AND g.id=?");
        Object[] args =
                groupId == null ? new Object[] {tenantId} : new Object[] {tenantId, groupId};
        return jdbc
                .query(
                        sql,
                        (result, row) ->
                                new PolicyView(
                                        result.getBoolean("medication_recording_enabled"),
                                        result.getBoolean("medication_authorization_required"),
                                        result.getString("instructions")),
                        args)
                .stream()
                .findFirst()
                .orElse(new PolicyView(false, true, null));
    }

    private void validateEvent(
            SchoolPrincipal actor,
            ProfileRow profile,
            String eventType,
            EventCommand command,
            Instant occurredAt) {
        if (command.endedAt() != null && command.endedAt().isBefore(occurredAt)) {
            throw new ChildcareValidationException("L'heure de fin précède le début");
        }
        if ("MOOD".equals(eventType)
                && (command.outcome() == null
                        || !MOODS.contains(command.outcome().toUpperCase(Locale.ROOT)))) {
            throw new ChildcareValidationException("Humeur invalide");
        }
        if ("TEMPERATURE".equals(eventType) && command.temperatureCelsius() == null) {
            throw new ChildcareValidationException("La température manuelle est requise");
        }
        if ("MEDICATION".equals(eventType)) {
            PolicyView policy = policyForProfile(actor.tenantId(), profile.id());
            if (!actor.permissions().contains("childcare.medication")) {
                throw new ChildcareValidationException(
                        "La permission médicament est requise pour cet événement");
            }
            if (!policy.medicationRecordingEnabled()) {
                throw new ChildcareValidationException(
                        "La politique de l'établissement interdit la saisie de médicaments");
            }
            if (blank(command.medicationName()) || blank(command.medicationDose())) {
                throw new ChildcareValidationException("Le médicament et la dose sont requis");
            }
            if (policy.medicationAuthorizationRequired()
                    && blank(command.authorizationReference())) {
                throw new ChildcareValidationException(
                        "La référence d'autorisation est obligatoire");
            }
        }
    }

    private PolicyView policyForProfile(UUID tenantId, UUID profileId) {
        return jdbc
                .query(
                        """
                        SELECT p.medication_recording_enabled,p.medication_authorization_required,
                               p.instructions
                        FROM childcare_profile cp
                        JOIN childcare_group g ON g.id=cp.group_id AND g.tenant_id=cp.tenant_id
                        JOIN childcare_policy p ON p.campus_id=g.campus_id AND p.tenant_id=g.tenant_id
                        WHERE cp.tenant_id=? AND cp.id=?
                        """,
                        (result, row) ->
                                new PolicyView(
                                        result.getBoolean(1),
                                        result.getBoolean(2),
                                        result.getString(3)),
                        tenantId,
                        profileId)
                .stream()
                .findFirst()
                .orElse(new PolicyView(false, true, null));
    }

    private UUID log(UUID tenantId, UUID profileId, LocalDate date, Instant now) {
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM childcare_daily_log WHERE tenant_id=? AND"
                                + " childcare_profile_id=? AND log_date=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        profileId,
                        date);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    """
                    INSERT INTO childcare_daily_log
                        (id,tenant_id,childcare_profile_id,log_date,status,created_at,updated_at)
                    VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)
                    """,
                    id,
                    tenantId,
                    profileId,
                    date,
                    timestamp(now),
                    timestamp(now));
            return id;
        } catch (DuplicateKeyException exception) {
            return existingLog(tenantId, profileId, date);
        }
    }

    private UUID existingLog(UUID tenantId, UUID profileId, LocalDate date) {
        return jdbc
                .query(
                        "SELECT id FROM childcare_daily_log WHERE tenant_id=? AND"
                                + " childcare_profile_id=? AND log_date=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        profileId,
                        date)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ChildcareNotFoundException("Journal quotidien introuvable"));
    }

    private void requireDraft(UUID tenantId, UUID logId) {
        if (count(
                        "SELECT count(*) FROM childcare_daily_log WHERE tenant_id=? AND id=? AND"
                                + " status='DRAFT'",
                        tenantId,
                        logId)
                == 0) {
            throw new ChildcareValidationException("Un journal publié ne peut plus être modifié");
        }
    }

    private ProfileRow profileByStudent(UUID tenantId, UUID studentId) {
        ProfileRow profile = nullableProfileByStudent(tenantId, studentId);
        if (profile == null) throw new ChildcareNotFoundException("Profil crèche introuvable");
        return profile;
    }

    private ProfileRow nullableProfileByStudent(UUID tenantId, UUID studentId) {
        return jdbc
                .query(
                        "SELECT id,student_id,group_id FROM childcare_profile WHERE tenant_id=?"
                                + " AND student_id=? AND active=TRUE",
                        (result, row) ->
                                new ProfileRow(
                                        result.getObject("id", UUID.class),
                                        result.getObject("student_id", UUID.class),
                                        result.getObject("group_id", UUID.class)),
                        tenantId,
                        studentId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void addPickup(UUID tenantId, UUID profileId, PickupCommand command, Instant now) {
        if (command == null
                || blank(command.displayName())
                || !Set.of("AUTHORIZED", "PROHIBITED").contains(command.authorizationStatus())) {
            throw new ChildcareValidationException("Personne de récupération invalide");
        }
        jdbc.update(
                """
                INSERT INTO childcare_pickup_person
                    (id,tenant_id,childcare_profile_id,display_name,relationship_type,phone,
                     authorization_status,identity_reference,notes,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                profileId,
                command.displayName().strip(),
                blank(command.relationshipType()) ? "OTHER" : command.relationshipType().strip(),
                nullable(command.phone()),
                command.authorizationStatus(),
                nullable(command.identityReference()),
                nullable(command.notes()),
                timestamp(now),
                timestamp(now));
    }

    private void requireReference(String table, UUID tenantId, UUID id) {
        if (id == null
                || count(
                                "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                                tenantId,
                                id)
                        == 0) {
            throw new ChildcareNotFoundException("Référence petite enfance introuvable");
        }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private ProfileSummary profileSummary(ResultSet result) throws SQLException {
        return new ProfileSummary(
                result.getObject("id", UUID.class),
                result.getObject("student_id", UUID.class),
                result.getString("student_name"),
                result.getObject("birth_date", LocalDate.class),
                result.getObject("group_id", UUID.class),
                result.getString("group_name"),
                result.getObject("expected_arrival_time", LocalTime.class),
                result.getObject("expected_departure_time", LocalTime.class),
                result.getString("meal_instructions"),
                result.getString("bottle_instructions"),
                result.getString("sleep_instructions"),
                result.getString("toilet_stage"));
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SCHOOL_ZONE));
    }

    private static LocalDate date(Instant instant) {
        return instant.atZone(SCHOOL_ZONE).toLocalDate();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullable(String value) {
        return blank(value) ? null : value.strip();
    }

    public record Dashboard(
            LocalDate date, List<GroupView> groups, List<ChildView> children, PolicyView policy) {}

    public record GroupView(
            UUID id,
            String code,
            String name,
            UUID campusId,
            int minAgeMonths,
            int maxAgeMonths,
            int capacity,
            String referenceEducator,
            int childCount) {}

    public record PolicyView(
            boolean medicationRecordingEnabled,
            boolean medicationAuthorizationRequired,
            String instructions) {}

    public record ChildView(
            UUID profileId,
            UUID studentId,
            String studentName,
            LocalDate birthDate,
            UUID groupId,
            String groupName,
            LocalTime expectedArrivalTime,
            LocalTime expectedDepartureTime,
            String mealInstructions,
            String bottleInstructions,
            String sleepInstructions,
            String toiletStage,
            List<PickupView> pickupPeople,
            DailyLogView dailyLog) {}

    public record PickupView(
            UUID id,
            String displayName,
            String relationshipType,
            String phone,
            String authorizationStatus,
            String identityReference,
            String notes) {}

    public record DailyLogView(
            UUID id,
            LocalDate date,
            Instant arrivalAt,
            Instant departureAt,
            String pickupPerson,
            String mood,
            String summary,
            String status,
            Instant publishedAt,
            List<EventView> events) {}

    public record EventView(
            UUID id,
            String eventType,
            Instant occurredAt,
            Instant endedAt,
            String label,
            BigDecimal quantity,
            String unit,
            String outcome,
            String notes,
            BigDecimal temperatureCelsius,
            String incidentSeverity,
            String medicationName,
            String medicationDose) {}

    public record DailyReport(
            UUID studentId, String studentName, String groupName, DailyLogView dailyLog) {}

    public record ProfileCommand(
            UUID studentId,
            UUID groupId,
            LocalTime expectedArrivalTime,
            LocalTime expectedDepartureTime,
            String mealInstructions,
            String bottleInstructions,
            String sleepInstructions,
            String toiletStage,
            List<PickupCommand> pickupPeople) {}

    public record PickupCommand(
            String displayName,
            String relationshipType,
            String phone,
            String authorizationStatus,
            String identityReference,
            String notes) {}

    public record TimeCommand(Instant at) {}

    public record DepartureCommand(Instant at, UUID pickupPersonId) {}

    public record EventCommand(
            String eventType,
            Instant occurredAt,
            Instant endedAt,
            String label,
            BigDecimal quantity,
            String unit,
            String outcome,
            String notes,
            BigDecimal temperatureCelsius,
            String incidentSeverity,
            String medicationName,
            String medicationDose,
            String authorizationReference) {}

    public record PublishCommand(LocalDate date, String summary) {}

    private record ProfileRow(UUID id, UUID studentId, UUID groupId) {}

    private record ProfileSummary(
            UUID id,
            UUID studentId,
            String studentName,
            LocalDate birthDate,
            UUID groupId,
            String groupName,
            LocalTime expectedArrivalTime,
            LocalTime expectedDepartureTime,
            String mealInstructions,
            String bottleInstructions,
            String sleepInstructions,
            String toiletStage) {}
}
