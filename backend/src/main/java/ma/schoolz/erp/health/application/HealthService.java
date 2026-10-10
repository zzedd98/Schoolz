package ma.schoolz.erp.health.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthService {
    public static final String SAFETY_NOTICE =
            "SchoolZ consigne des faits et des actions de prise en charge. Il ne produit aucun"
                    + " diagnostic médical.";

    private static final Set<String> SEVERITIES = Set.of("LOW", "MODERATE", "HIGH", "CRITICAL");
    private static final Set<String> INCIDENT_STATUSES =
            Set.of("OPEN", "MONITORING", "CLOSED", "TRANSFERRED");
    private static final Set<String> EMERGENCY_ACTION_TYPES =
            Set.of("FIRST_AID", "GUARDIAN_CALL", "EMERGENCY_SERVICE", "TRANSFER", "OTHER");
    private static final Set<String> VACCINATION_STATUSES =
            Set.of("RECORDED", "VERIFIED", "EXEMPTED");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;

    public HealthService(JdbcTemplate jdbc, Clock clock, IdentityAuditTrail audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public Dashboard dashboard(SchoolPrincipal actor, String ipAddress) {
        require(actor, "health.read");
        UUID tenantId = actor.tenantId();
        List<StudentSummary> students =
                jdbc.query(
                        """
                        SELECT sp.id AS student_id,p.first_name,p.last_name,
                            EXISTS(SELECT 1 FROM health_profile hp WHERE hp.tenant_id=sp.tenant_id AND hp.student_id=sp.id) AS profile_ready,
                            (SELECT count(*) FROM health_allergy a JOIN health_profile hp ON hp.id=a.health_profile_id
                                WHERE hp.tenant_id=sp.tenant_id AND hp.student_id=sp.id AND a.active=TRUE) AS active_allergies,
                            (SELECT count(*) FROM health_medication_instruction m JOIN health_profile hp ON hp.id=m.health_profile_id
                                WHERE hp.tenant_id=sp.tenant_id AND hp.student_id=sp.id AND m.active=TRUE) AS active_medications,
                            (SELECT count(*) FROM health_incident i WHERE i.tenant_id=sp.tenant_id AND i.student_id=sp.id
                                AND i.status IN ('OPEN','MONITORING')) AS open_incidents,
                            (SELECT max(v.visited_at) FROM health_nurse_visit v WHERE v.tenant_id=sp.tenant_id AND v.student_id=sp.id) AS last_visit_at
                        FROM student_profile sp JOIN person p ON p.id=sp.person_id
                        WHERE sp.tenant_id=? AND sp.status='ACTIVE'
                        ORDER BY p.last_name,p.first_name
                        """,
                        (result, row) ->
                                new StudentSummary(
                                        result.getObject("student_id", UUID.class),
                                        result.getString("first_name")
                                                + " "
                                                + result.getString("last_name"),
                                        result.getBoolean("profile_ready"),
                                        result.getInt("active_allergies"),
                                        result.getInt("active_medications"),
                                        result.getInt("open_incidents"),
                                        instant(result, "last_visit_at")),
                        tenantId);
        List<IncidentView> incidents = recentIncidents(tenantId, 20);
        HealthPolicyView policy = policy(tenantId);
        audit(
                actor,
                "health.dashboard.viewed",
                "HealthDashboard",
                tenantId,
                ipAddress,
                Map.of("studentCount", students.size()));
        return new Dashboard(clock.instant(), SAFETY_NOTICE, policy, students, incidents);
    }

    @Transactional
    public StudentHealthRecord student(SchoolPrincipal actor, UUID studentId, String ipAddress) {
        require(actor, "health.sensitive.read");
        StudentIdentity identity = studentIdentity(actor.tenantId(), studentId);
        UUID profileId = existingProfileId(actor.tenantId(), studentId);
        ProfileView profile =
                profileId == null ? ProfileView.empty() : profile(actor.tenantId(), profileId);
        List<AllergyView> allergies =
                profileId == null ? List.of() : allergies(actor.tenantId(), profileId);
        List<MedicationView> medications =
                profileId == null ? List.of() : medications(actor.tenantId(), profileId);
        List<IncidentView> incidents = incidents(actor.tenantId(), studentId);
        List<NurseVisitView> visits = visits(actor.tenantId(), studentId);
        List<VaccinationView> vaccinations =
                profileId == null ? List.of() : vaccinations(actor.tenantId(), profileId);
        audit(
                actor,
                "health.profile.viewed",
                "StudentHealthRecord",
                studentId,
                ipAddress,
                Map.of(
                        "allergyCount",
                        allergies.size(),
                        "medicationCount",
                        medications.size(),
                        "incidentCount",
                        incidents.size()));
        return new StudentHealthRecord(
                SAFETY_NOTICE,
                identity,
                policy(actor.tenantId()),
                profile,
                allergies,
                medications,
                incidents,
                visits,
                vaccinations);
    }

    @Transactional
    public StudentHealthRecord saveProfile(
            SchoolPrincipal actor, UUID studentId, ProfileCommand command, String ipAddress) {
        require(actor, "health.write");
        studentIdentity(actor.tenantId(), studentId);
        if (command == null) throw new HealthValidationException("Profil de santé requis");
        UUID profileId = existingProfileId(actor.tenantId(), studentId);
        Instant now = clock.instant();
        if (profileId == null) {
            profileId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO health_profile(
                        id,tenant_id,student_id,blood_group,insurance_provider,insurance_number,
                        physician_name,physician_phone,emergency_notes,created_at,updated_at,version)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,0)
                    """,
                    profileId,
                    actor.tenantId(),
                    studentId,
                    nullable(command.bloodGroup()),
                    nullable(command.insuranceProvider()),
                    nullable(command.insuranceNumber()),
                    nullable(command.physicianName()),
                    nullable(command.physicianPhone()),
                    nullable(command.emergencyNotes()),
                    timestamp(now),
                    timestamp(now));
        } else {
            jdbc.update(
                    """
                    UPDATE health_profile SET blood_group=?,insurance_provider=?,insurance_number=?,
                        physician_name=?,physician_phone=?,emergency_notes=?,updated_at=?,version=version+1
                    WHERE tenant_id=? AND id=?
                    """,
                    nullable(command.bloodGroup()),
                    nullable(command.insuranceProvider()),
                    nullable(command.insuranceNumber()),
                    nullable(command.physicianName()),
                    nullable(command.physicianPhone()),
                    nullable(command.emergencyNotes()),
                    timestamp(now),
                    actor.tenantId(),
                    profileId);
        }
        audit(
                actor,
                "health.profile.updated",
                "HealthProfile",
                profileId,
                ipAddress,
                Map.of("studentId", studentId));
        return student(actor, studentId, ipAddress);
    }

    @Transactional
    public AllergyView addAllergy(
            SchoolPrincipal actor, UUID studentId, AllergyCommand command, String ipAddress) {
        require(actor, "health.write");
        if (command == null || blank(command.allergen()))
            throw new HealthValidationException("L'allergène est requis");
        String severity = enumValue(command.severity(), SEVERITIES, "Sévérité invalide");
        UUID profileId = ensureProfile(actor.tenantId(), studentId);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO health_allergy(
                    id,tenant_id,health_profile_id,allergen,reaction_description,severity,
                    emergency_instructions,active,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                profileId,
                required(command.allergen(), "L'allergène est requis"),
                nullable(command.reactionDescription()),
                severity,
                nullable(command.emergencyInstructions()),
                command.active() == null || command.active(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        audit(
                actor,
                "health.allergy.created",
                "HealthAllergy",
                id,
                ipAddress,
                Map.of("studentId", studentId, "severity", severity));
        return allergies(actor.tenantId(), profileId).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public MedicationView addMedication(
            SchoolPrincipal actor, UUID studentId, MedicationCommand command, String ipAddress) {
        require(actor, "health.write");
        if (command == null)
            throw new HealthValidationException("Instruction de médicament requise");
        if (command.startsOn() != null
                && command.endsOn() != null
                && command.endsOn().isBefore(command.startsOn())) {
            throw new HealthValidationException("La date de fin précède la date de début");
        }
        UUID profileId = ensureProfile(actor.tenantId(), studentId);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO health_medication_instruction(
                    id,tenant_id,health_profile_id,medication_name,dosage_text,administration_route,
                    schedule_text,starts_on,ends_on,prescriber_name,authorization_reference,
                    instructions,active,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                profileId,
                required(command.medicationName(), "Le nom du médicament est requis"),
                required(command.dosageText(), "La posologie transmise est requise"),
                nullable(command.administrationRoute()),
                nullable(command.scheduleText()),
                command.startsOn(),
                command.endsOn(),
                nullable(command.prescriberName()),
                nullable(command.authorizationReference()),
                nullable(command.instructions()),
                command.active() == null || command.active(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        audit(
                actor,
                "health.medication-instruction.created",
                "MedicationInstruction",
                id,
                ipAddress,
                Map.of("studentId", studentId));
        return medications(actor.tenantId(), profileId).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public IncidentView addIncident(
            SchoolPrincipal actor, UUID studentId, IncidentCommand command, String ipAddress) {
        require(actor, "health.write");
        studentIdentity(actor.tenantId(), studentId);
        if (command == null) throw new HealthValidationException("Incident requis");
        String severity = enumValue(command.severity(), SEVERITIES, "Sévérité invalide");
        String status =
                enumValue(
                        command.status() == null ? "OPEN" : command.status(),
                        INCIDENT_STATUSES,
                        "Statut d'incident invalide");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO health_incident(
                    id,tenant_id,student_id,occurred_at,incident_type,severity,location,description,
                    action_taken,guardian_notified_at,status,recorded_by_user_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                studentId,
                timestamp(command.occurredAt() == null ? now : command.occurredAt()),
                required(command.incidentType(), "Le type d'incident est requis")
                        .toUpperCase(Locale.ROOT),
                severity,
                nullable(command.location()),
                required(command.description(), "La description factuelle est requise"),
                nullable(command.actionTaken()),
                timestamp(command.guardianNotifiedAt()),
                status,
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        audit(
                actor,
                "health.incident.created",
                "HealthIncident",
                id,
                ipAddress,
                Map.of("studentId", studentId, "severity", severity, "status", status));
        return incident(actor.tenantId(), id);
    }

    @Transactional
    public NurseVisitView addVisit(
            SchoolPrincipal actor, UUID studentId, VisitCommand command, String ipAddress) {
        require(actor, "health.write");
        studentIdentity(actor.tenantId(), studentId);
        if (command == null) throw new HealthValidationException("Visite infirmière requise");
        if (command.temperatureCelsius() != null
                && (command.temperatureCelsius().compareTo(new BigDecimal("30")) < 0
                        || command.temperatureCelsius().compareTo(new BigDecimal("45")) > 0)) {
            throw new HealthValidationException("Température hors plage de saisie");
        }
        if (command.incidentId() != null) incident(actor.tenantId(), command.incidentId());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO health_nurse_visit(
                    id,tenant_id,student_id,incident_id,visited_at,reason,observations,
                    temperature_celsius,action_taken,return_to_class,referred_to,
                    recorded_by_user_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                studentId,
                command.incidentId(),
                timestamp(command.visitedAt() == null ? now : command.visitedAt()),
                required(command.reason(), "Le motif factuel est requis"),
                nullable(command.observations()),
                command.temperatureCelsius(),
                nullable(command.actionTaken()),
                command.returnToClass(),
                nullable(command.referredTo()),
                actor.accountId(),
                timestamp(now));
        audit(
                actor,
                "health.nurse-visit.created",
                "NurseVisit",
                id,
                ipAddress,
                Map.of("studentId", studentId));
        return visits(actor.tenantId(), studentId).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public EmergencyActionView addEmergencyAction(
            SchoolPrincipal actor,
            UUID incidentId,
            EmergencyActionCommand command,
            String ipAddress) {
        require(actor, "health.write");
        IncidentView incident = incident(actor.tenantId(), incidentId);
        if (command == null) throw new HealthValidationException("Action d'urgence requise");
        String actionType =
                enumValue(
                        command.actionType(),
                        EMERGENCY_ACTION_TYPES,
                        "Type d'action d'urgence invalide");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO health_emergency_action(
                    id,tenant_id,incident_id,action_type,description,performed_at,
                    performed_by_user_id,guardian_contacted,external_reference,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                incidentId,
                actionType,
                required(command.description(), "La description de l'action est requise"),
                timestamp(command.performedAt() == null ? now : command.performedAt()),
                actor.accountId(),
                command.guardianContacted(),
                nullable(command.externalReference()),
                timestamp(now));
        audit(
                actor,
                "health.emergency-action.created",
                "EmergencyAction",
                id,
                ipAddress,
                Map.of("incidentId", incidentId, "studentId", incident.studentId()));
        return emergencyActions(actor.tenantId(), incidentId).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public VaccinationView addVaccination(
            SchoolPrincipal actor, UUID studentId, VaccinationCommand command, String ipAddress) {
        require(actor, "health.vaccination");
        if (!policy(actor.tenantId()).vaccinationTrackingEnabled()) {
            throw new HealthValidationException(
                    "Le suivi vaccinal n'est pas activé par l'établissement");
        }
        if (command == null) throw new HealthValidationException("Vaccination requise");
        if (command.nextDueOn() != null
                && command.administeredOn() != null
                && command.nextDueOn().isBefore(command.administeredOn())) {
            throw new HealthValidationException("La prochaine échéance est antérieure à l'acte");
        }
        String status =
                enumValue(
                        command.status() == null ? "RECORDED" : command.status(),
                        VACCINATION_STATUSES,
                        "Statut vaccinal invalide");
        UUID profileId = ensureProfile(actor.tenantId(), studentId);
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO health_vaccination_record(
                    id,tenant_id,health_profile_id,vaccine_name,dose_label,administered_on,
                    provider_name,document_reference,next_due_on,status,recorded_by_user_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                profileId,
                required(command.vaccineName(), "Le nom du vaccin est requis"),
                nullable(command.doseLabel()),
                required(command.administeredOn(), "La date d'administration est requise"),
                nullable(command.providerName()),
                nullable(command.documentReference()),
                command.nextDueOn(),
                status,
                actor.accountId(),
                timestamp(clock.instant()));
        audit(
                actor,
                "health.vaccination.created",
                "VaccinationRecord",
                id,
                ipAddress,
                Map.of("studentId", studentId, "status", status));
        return vaccinations(actor.tenantId(), profileId).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public HealthPolicyView updatePolicy(
            SchoolPrincipal actor, PolicyCommand command, String ipAddress) {
        require(actor, "health.configure");
        if (command == null) throw new HealthValidationException("Politique santé requise");
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        """
                        UPDATE health_policy SET vaccination_tracking_enabled=?,updated_by_user_id=?,updated_at=?
                        WHERE tenant_id=?
                        """,
                        command.vaccinationTrackingEnabled(),
                        actor.accountId(),
                        timestamp(now),
                        actor.tenantId());
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO health_policy(
                        id,tenant_id,vaccination_tracking_enabled,updated_by_user_id,created_at,updated_at)
                    VALUES(?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    command.vaccinationTrackingEnabled(),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        }
        audit(
                actor,
                "health.policy.updated",
                "HealthPolicy",
                actor.tenantId(),
                ipAddress,
                Map.of("vaccinationTrackingEnabled", command.vaccinationTrackingEnabled()));
        return policy(actor.tenantId());
    }

    private HealthPolicyView policy(UUID tenantId) {
        List<HealthPolicyView> values =
                jdbc.query(
                        "SELECT vaccination_tracking_enabled,updated_at FROM health_policy WHERE"
                                + " tenant_id=?",
                        (result, row) ->
                                new HealthPolicyView(
                                        result.getBoolean("vaccination_tracking_enabled"),
                                        instant(result, "updated_at")),
                        tenantId);
        return values.isEmpty() ? new HealthPolicyView(false, null) : values.getFirst();
    }

    private ProfileView profile(UUID tenantId, UUID profileId) {
        return jdbc.queryForObject(
                """
                SELECT id,blood_group,insurance_provider,insurance_number,physician_name,
                    physician_phone,emergency_notes,updated_at,version
                FROM health_profile WHERE tenant_id=? AND id=?
                """,
                (result, row) ->
                        new ProfileView(
                                result.getObject("id", UUID.class),
                                result.getString("blood_group"),
                                result.getString("insurance_provider"),
                                result.getString("insurance_number"),
                                result.getString("physician_name"),
                                result.getString("physician_phone"),
                                result.getString("emergency_notes"),
                                instant(result, "updated_at"),
                                result.getLong("version")),
                tenantId,
                profileId);
    }

    private List<AllergyView> allergies(UUID tenantId, UUID profileId) {
        return jdbc.query(
                """
                SELECT id,allergen,reaction_description,severity,emergency_instructions,active,created_at
                FROM health_allergy WHERE tenant_id=? AND health_profile_id=?
                ORDER BY active DESC,severity DESC,created_at DESC
                """,
                (result, row) ->
                        new AllergyView(
                                result.getObject("id", UUID.class),
                                result.getString("allergen"),
                                result.getString("reaction_description"),
                                result.getString("severity"),
                                result.getString("emergency_instructions"),
                                result.getBoolean("active"),
                                instant(result, "created_at")),
                tenantId,
                profileId);
    }

    private List<MedicationView> medications(UUID tenantId, UUID profileId) {
        return jdbc.query(
                """
                SELECT id,medication_name,dosage_text,administration_route,schedule_text,starts_on,
                    ends_on,prescriber_name,authorization_reference,instructions,active,created_at
                FROM health_medication_instruction WHERE tenant_id=? AND health_profile_id=?
                ORDER BY active DESC,created_at DESC
                """,
                (result, row) ->
                        new MedicationView(
                                result.getObject("id", UUID.class),
                                result.getString("medication_name"),
                                result.getString("dosage_text"),
                                result.getString("administration_route"),
                                result.getString("schedule_text"),
                                localDate(result, "starts_on"),
                                localDate(result, "ends_on"),
                                result.getString("prescriber_name"),
                                result.getString("authorization_reference"),
                                result.getString("instructions"),
                                result.getBoolean("active"),
                                instant(result, "created_at")),
                tenantId,
                profileId);
    }

    private List<IncidentView> recentIncidents(UUID tenantId, int limit) {
        return jdbc.query(
                """
                SELECT i.id,i.student_id,p.first_name,p.last_name,i.occurred_at,i.incident_type,
                    i.severity,i.location,i.description,i.action_taken,i.guardian_notified_at,i.status
                FROM health_incident i JOIN student_profile sp ON sp.id=i.student_id
                    JOIN person p ON p.id=sp.person_id
                WHERE i.tenant_id=? ORDER BY i.occurred_at DESC LIMIT ?
                """,
                (result, row) -> incidentView(tenantId, result),
                tenantId,
                limit);
    }

    private List<IncidentView> incidents(UUID tenantId, UUID studentId) {
        return jdbc.query(
                """
                SELECT i.id,i.student_id,p.first_name,p.last_name,i.occurred_at,i.incident_type,
                    i.severity,i.location,i.description,i.action_taken,i.guardian_notified_at,i.status
                FROM health_incident i JOIN student_profile sp ON sp.id=i.student_id
                    JOIN person p ON p.id=sp.person_id
                WHERE i.tenant_id=? AND i.student_id=? ORDER BY i.occurred_at DESC
                """,
                (result, row) -> incidentView(tenantId, result),
                tenantId,
                studentId);
    }

    private IncidentView incident(UUID tenantId, UUID incidentId) {
        List<IncidentView> incidents =
                jdbc.query(
                        """
                        SELECT i.id,i.student_id,p.first_name,p.last_name,i.occurred_at,i.incident_type,
                            i.severity,i.location,i.description,i.action_taken,i.guardian_notified_at,i.status
                        FROM health_incident i JOIN student_profile sp ON sp.id=i.student_id
                            JOIN person p ON p.id=sp.person_id
                        WHERE i.tenant_id=? AND i.id=?
                        """,
                        (result, row) -> incidentView(tenantId, result),
                        tenantId,
                        incidentId);
        if (incidents.isEmpty()) throw new HealthNotFoundException("Incident introuvable");
        return incidents.getFirst();
    }

    private IncidentView incidentView(UUID tenantId, ResultSet result) throws SQLException {
        UUID incidentId = result.getObject("id", UUID.class);
        return new IncidentView(
                incidentId,
                result.getObject("student_id", UUID.class),
                result.getString("first_name") + " " + result.getString("last_name"),
                instant(result, "occurred_at"),
                result.getString("incident_type"),
                result.getString("severity"),
                result.getString("location"),
                result.getString("description"),
                result.getString("action_taken"),
                instant(result, "guardian_notified_at"),
                result.getString("status"),
                emergencyActions(tenantId, incidentId));
    }

    private List<NurseVisitView> visits(UUID tenantId, UUID studentId) {
        return jdbc.query(
                """
                SELECT id,incident_id,visited_at,reason,observations,temperature_celsius,
                    action_taken,return_to_class,referred_to,created_at
                FROM health_nurse_visit WHERE tenant_id=? AND student_id=? ORDER BY visited_at DESC
                """,
                (result, row) ->
                        new NurseVisitView(
                                result.getObject("id", UUID.class),
                                result.getObject("incident_id", UUID.class),
                                instant(result, "visited_at"),
                                result.getString("reason"),
                                result.getString("observations"),
                                result.getBigDecimal("temperature_celsius"),
                                result.getString("action_taken"),
                                result.getObject("return_to_class", Boolean.class),
                                result.getString("referred_to"),
                                instant(result, "created_at")),
                tenantId,
                studentId);
    }

    private List<EmergencyActionView> emergencyActions(UUID tenantId, UUID incidentId) {
        return jdbc.query(
                """
                SELECT id,action_type,description,performed_at,guardian_contacted,external_reference
                FROM health_emergency_action WHERE tenant_id=? AND incident_id=? ORDER BY performed_at
                """,
                (result, row) ->
                        new EmergencyActionView(
                                result.getObject("id", UUID.class),
                                result.getString("action_type"),
                                result.getString("description"),
                                instant(result, "performed_at"),
                                result.getBoolean("guardian_contacted"),
                                result.getString("external_reference")),
                tenantId,
                incidentId);
    }

    private List<VaccinationView> vaccinations(UUID tenantId, UUID profileId) {
        return jdbc.query(
                """
                SELECT id,vaccine_name,dose_label,administered_on,provider_name,document_reference,
                    next_due_on,status,created_at
                FROM health_vaccination_record WHERE tenant_id=? AND health_profile_id=?
                ORDER BY administered_on DESC
                """,
                (result, row) ->
                        new VaccinationView(
                                result.getObject("id", UUID.class),
                                result.getString("vaccine_name"),
                                result.getString("dose_label"),
                                localDate(result, "administered_on"),
                                result.getString("provider_name"),
                                result.getString("document_reference"),
                                localDate(result, "next_due_on"),
                                result.getString("status"),
                                instant(result, "created_at")),
                tenantId,
                profileId);
    }

    private StudentIdentity studentIdentity(UUID tenantId, UUID studentId) {
        List<StudentIdentity> values =
                jdbc.query(
                        """
                        SELECT sp.id,p.first_name,p.last_name,p.birth_date,
                            (SELECT c.name FROM student_enrollment e LEFT JOIN classroom c ON c.id=e.classroom_id
                                WHERE e.tenant_id=sp.tenant_id AND e.student_id=sp.id AND e.status='ENROLLED'
                                ORDER BY e.enrolled_on DESC LIMIT 1) AS classroom_name
                        FROM student_profile sp JOIN person p ON p.id=sp.person_id
                        WHERE sp.tenant_id=? AND sp.id=?
                        """,
                        (result, row) ->
                                new StudentIdentity(
                                        result.getObject("id", UUID.class),
                                        result.getString("first_name")
                                                + " "
                                                + result.getString("last_name"),
                                        localDate(result, "birth_date"),
                                        result.getString("classroom_name")),
                        tenantId,
                        studentId);
        if (values.isEmpty()) throw new HealthNotFoundException("Élève introuvable");
        return values.getFirst();
    }

    private UUID existingProfileId(UUID tenantId, UUID studentId) {
        List<UUID> values =
                jdbc.query(
                        "SELECT id FROM health_profile WHERE tenant_id=? AND student_id=?",
                        (result, row) -> result.getObject(1, UUID.class),
                        tenantId,
                        studentId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private UUID ensureProfile(UUID tenantId, UUID studentId) {
        studentIdentity(tenantId, studentId);
        UUID profileId = existingProfileId(tenantId, studentId);
        if (profileId != null) return profileId;
        profileId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO health_profile(id,tenant_id,student_id,created_at,updated_at,version)
                VALUES(?,?,?,?,?,0)
                """,
                profileId,
                tenantId,
                studentId,
                timestamp(now),
                timestamp(now));
        return profileId;
    }

    private void audit(
            SchoolPrincipal actor,
            String action,
            String resourceType,
            UUID resourceId,
            String ipAddress,
            Map<String, ?> metadata) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                resourceType,
                resourceId.toString(),
                ipAddress,
                metadata,
                null,
                null);
    }

    private static void require(SchoolPrincipal actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new AccessDeniedException("Permission requise : " + permission);
        }
    }

    private static String enumValue(String value, Set<String> allowed, String message) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new HealthValidationException(message);
        return normalized;
    }

    private static String required(String value, String message) {
        if (blank(value)) throw new HealthValidationException(message);
        return value.strip();
    }

    private static <T> T required(T value, String message) {
        if (value == null) throw new HealthValidationException(message);
        return value;
    }

    private static String nullable(String value) {
        return blank(value) ? null : value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet result, String column) throws SQLException {
        java.sql.Date value = result.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    public record ProfileCommand(
            String bloodGroup,
            String insuranceProvider,
            String insuranceNumber,
            String physicianName,
            String physicianPhone,
            String emergencyNotes) {}

    public record AllergyCommand(
            String allergen,
            String reactionDescription,
            String severity,
            String emergencyInstructions,
            Boolean active) {}

    public record MedicationCommand(
            String medicationName,
            String dosageText,
            String administrationRoute,
            String scheduleText,
            LocalDate startsOn,
            LocalDate endsOn,
            String prescriberName,
            String authorizationReference,
            String instructions,
            Boolean active) {}

    public record IncidentCommand(
            Instant occurredAt,
            String incidentType,
            String severity,
            String location,
            String description,
            String actionTaken,
            Instant guardianNotifiedAt,
            String status) {}

    public record VisitCommand(
            UUID incidentId,
            Instant visitedAt,
            String reason,
            String observations,
            BigDecimal temperatureCelsius,
            String actionTaken,
            Boolean returnToClass,
            String referredTo) {}

    public record EmergencyActionCommand(
            String actionType,
            String description,
            Instant performedAt,
            boolean guardianContacted,
            String externalReference) {}

    public record VaccinationCommand(
            String vaccineName,
            String doseLabel,
            LocalDate administeredOn,
            String providerName,
            String documentReference,
            LocalDate nextDueOn,
            String status) {}

    public record PolicyCommand(boolean vaccinationTrackingEnabled) {}

    public record HealthPolicyView(boolean vaccinationTrackingEnabled, Instant updatedAt) {}

    public record StudentSummary(
            UUID studentId,
            String studentName,
            boolean profileReady,
            int activeAllergies,
            int activeMedications,
            int openIncidents,
            Instant lastVisitAt) {}

    public record StudentIdentity(
            UUID id, String displayName, LocalDate birthDate, String classroomName) {}

    public record ProfileView(
            UUID id,
            String bloodGroup,
            String insuranceProvider,
            String insuranceNumber,
            String physicianName,
            String physicianPhone,
            String emergencyNotes,
            Instant updatedAt,
            long version) {
        static ProfileView empty() {
            return new ProfileView(null, null, null, null, null, null, null, null, 0);
        }
    }

    public record AllergyView(
            UUID id,
            String allergen,
            String reactionDescription,
            String severity,
            String emergencyInstructions,
            boolean active,
            Instant createdAt) {}

    public record MedicationView(
            UUID id,
            String medicationName,
            String dosageText,
            String administrationRoute,
            String scheduleText,
            LocalDate startsOn,
            LocalDate endsOn,
            String prescriberName,
            String authorizationReference,
            String instructions,
            boolean active,
            Instant createdAt) {}

    public record IncidentView(
            UUID id,
            UUID studentId,
            String studentName,
            Instant occurredAt,
            String incidentType,
            String severity,
            String location,
            String description,
            String actionTaken,
            Instant guardianNotifiedAt,
            String status,
            List<EmergencyActionView> emergencyActions) {}

    public record NurseVisitView(
            UUID id,
            UUID incidentId,
            Instant visitedAt,
            String reason,
            String observations,
            BigDecimal temperatureCelsius,
            String actionTaken,
            Boolean returnToClass,
            String referredTo,
            Instant createdAt) {}

    public record EmergencyActionView(
            UUID id,
            String actionType,
            String description,
            Instant performedAt,
            boolean guardianContacted,
            String externalReference) {}

    public record VaccinationView(
            UUID id,
            String vaccineName,
            String doseLabel,
            LocalDate administeredOn,
            String providerName,
            String documentReference,
            LocalDate nextDueOn,
            String status,
            Instant createdAt) {}

    public record Dashboard(
            Instant generatedAt,
            String safetyNotice,
            HealthPolicyView policy,
            List<StudentSummary> students,
            List<IncidentView> recentIncidents) {}

    public record StudentHealthRecord(
            String safetyNotice,
            StudentIdentity student,
            HealthPolicyView policy,
            ProfileView profile,
            List<AllergyView> allergies,
            List<MedicationView> medications,
            List<IncidentView> incidents,
            List<NurseVisitView> visits,
            List<VaccinationView> vaccinations) {}
}
