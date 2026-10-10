package ma.schoolz.erp.admissions.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.CampaignView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ProspectCommand;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.StepView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdmissionCrmService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AdmissionStateService states;
    private final AdmissionPortalService portal;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public AdmissionCrmService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            AdmissionStateService states,
            AdmissionPortalService portal,
            IdentityAuditTrail audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.states = states;
        this.portal = portal;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('admissions.read')")
    public List<CampaignView> campaigns(SchoolPrincipal actor) {
        return jdbc.query(
                "SELECT * FROM admission_campaign WHERE tenant_id=? ORDER BY opens_on DESC,name",
                (result, row) -> campaignView(result),
                actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('admissions.write')")
    public CampaignView createCampaign(
            SchoolPrincipal actor, CampaignCommand command, String ipAddress) {
        if (blank(command.code()) || blank(command.name())) {
            throw new AdmissionValidationException(
                    "Le code et le nom de la campagne sont obligatoires");
        }
        if (command.opensOn() == null
                || command.closesOn() == null
                || command.opensOn().isAfter(command.closesOn())) {
            throw new AdmissionValidationException("Les dates de la campagne sont invalides");
        }
        BigDecimal fee =
                command.applicationFee() == null ? BigDecimal.ZERO : command.applicationFee();
        if (fee.signum() < 0 || fee.scale() > 2) {
            throw new AdmissionValidationException("Les frais de dossier sont invalides");
        }
        if (command.academicYearId() != null) {
            requireReference("academic_year", actor.tenantId(), command.academicYearId());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO admission_campaign
                        (id,tenant_id,academic_year_id,code,name,description,opens_on,closes_on,
                         application_fee,currency_code,required_document_types,status,
                         created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    actor.tenantId(),
                    command.academicYearId(),
                    command.code().strip().toUpperCase(Locale.ROOT),
                    command.name().strip(),
                    blankToNull(command.description()),
                    command.opensOn(),
                    command.closesOn(),
                    fee,
                    blank(command.currencyCode())
                            ? "MAD"
                            : command.currencyCode().strip().toUpperCase(Locale.ROOT),
                    writeJson(
                            command.requiredDocuments() == null
                                    ? List.of()
                                    : command.requiredDocuments()),
                    blank(command.status())
                            ? "DRAFT"
                            : command.status().strip().toUpperCase(Locale.ROOT),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new AdmissionConflictException("Ce code de campagne existe déjà");
        }
        List<StepCommand> steps =
                command.steps() == null || command.steps().isEmpty()
                        ? defaultSteps()
                        : command.steps();
        for (int index = 0; index < steps.size(); index++) {
            StepCommand step = steps.get(index);
            jdbc.update(
                    """
                    INSERT INTO admission_step
                        (id,tenant_id,campaign_id,code,name,instructions,sequence_number,required,created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    id,
                    step.code().strip().toUpperCase(Locale.ROOT),
                    step.name().strip(),
                    blankToNull(step.instructions()),
                    index + 1,
                    step.required(),
                    timestamp(now));
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "admissions.campaign.created",
                "AdmissionCampaign",
                id.toString(),
                ipAddress,
                Map.of("code", command.code()),
                null,
                command);
        return campaigns(actor).stream()
                .filter(campaign -> campaign.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Campagne introuvable"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('admissions.write')")
    public ProspectView createProspect(
            SchoolPrincipal actor, ProspectCommand command, String ipAddress) {
        UUID id = portal.createPublicProspect(tenantSlug(actor.tenantId()), command);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "admissions.prospect.created",
                "Prospect",
                id.toString(),
                ipAddress,
                Map.of("source", blank(command.source()) ? "INTERNAL" : command.source()),
                null,
                command);
        return prospect(actor.tenantId(), id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('admissions.read')")
    public AdmissionBoard board(SchoolPrincipal actor, UUID campaignId) {
        requireCampaign(actor.tenantId(), campaignId);
        List<BoardCard> prospects =
                jdbc.query(
                        """
                        SELECT id,first_name,last_name,child_name,email,phone,status,source,
                               desired_grade_code,created_at
                        FROM admission_prospect
                        WHERE tenant_id=? AND campaign_id=? AND converted_at IS NULL
                        ORDER BY updated_at DESC
                        """,
                        (result, row) ->
                                new BoardCard(
                                        result.getObject("id", UUID.class),
                                        "PROSPECT",
                                        result.getString("first_name")
                                                + " "
                                                + result.getString("last_name"),
                                        result.getString("child_name"),
                                        null,
                                        AdmissionStage.valueOf(result.getString("status")),
                                        result.getString("source"),
                                        result.getString("desired_grade_code"),
                                        0,
                                        result.getString("email"),
                                        result.getString("phone"),
                                        result.getTimestamp("created_at").toInstant()),
                        actor.tenantId(),
                        campaignId);
        List<BoardCard> applications =
                jdbc.query(
                        """
                        SELECT a.id,a.reference_number,a.current_status,a.completeness_percent,
                               a.created_at,p.first_name,p.last_name,g.name AS grade_name,
                               pa.display_name,pa.email,pa.phone
                        FROM admission_application a
                        JOIN admission_applicant p
                          ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                        JOIN admission_portal_account pa
                          ON pa.id=p.portal_account_id AND pa.tenant_id=p.tenant_id
                        LEFT JOIN grade_level g
                          ON g.id=a.desired_grade_level_id AND g.tenant_id=a.tenant_id
                        WHERE a.tenant_id=? AND a.campaign_id=? ORDER BY a.updated_at DESC
                        """,
                        (result, row) ->
                                new BoardCard(
                                        result.getObject("id", UUID.class),
                                        "APPLICATION",
                                        result.getString("first_name")
                                                + " "
                                                + result.getString("last_name"),
                                        result.getString("display_name"),
                                        result.getString("reference_number"),
                                        AdmissionStage.valueOf(result.getString("current_status")),
                                        "PORTAL",
                                        result.getString("grade_name"),
                                        result.getInt("completeness_percent"),
                                        result.getString("email"),
                                        result.getString("phone"),
                                        result.getTimestamp("created_at").toInstant()),
                        actor.tenantId(),
                        campaignId);
        List<BoardCard> cards =
                java.util.stream.Stream.concat(prospects.stream(), applications.stream()).toList();
        List<BoardColumn> columns =
                Arrays.stream(AdmissionStage.values())
                        .map(
                                stage ->
                                        new BoardColumn(
                                                stage,
                                                cards.stream()
                                                        .filter(card -> card.status() == stage)
                                                        .toList()))
                        .toList();
        return new AdmissionBoard(campaignId, columns);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('admissions.read')")
    public Dashboard dashboard(SchoolPrincipal actor, UUID campaignId) {
        requireCampaign(actor.tenantId(), campaignId);
        int prospects = count("admission_prospect", actor.tenantId(), campaignId);
        int applications = count("admission_application", actor.tenantId(), campaignId);
        int incomplete =
                scalar(
                        """
                        SELECT count(*) FROM admission_application
                        WHERE tenant_id=? AND campaign_id=? AND completeness_percent<100
                          AND current_status NOT IN ('REJECTED','ENROLLED')
                        """,
                        actor.tenantId(),
                        campaignId);
        int accepted =
                scalar(
                        """
                        SELECT count(*) FROM admission_application
                        WHERE tenant_id=? AND campaign_id=? AND current_status IN ('ACCEPTED','ENROLLED')
                        """,
                        actor.tenantId(),
                        campaignId);
        int enrolled =
                scalar(
                        """
                        SELECT count(*) FROM admission_application
                        WHERE tenant_id=? AND campaign_id=? AND current_status='ENROLLED'
                        """,
                        actor.tenantId(),
                        campaignId);
        List<SourceMetric> sources =
                jdbc.query(
                        """
                        SELECT source,count(*) AS total FROM admission_prospect
                        WHERE tenant_id=? AND campaign_id=? GROUP BY source ORDER BY total DESC
                        """,
                        (result, row) ->
                                new SourceMetric(
                                        result.getString("source"), result.getInt("total")),
                        actor.tenantId(),
                        campaignId);
        double conversionRate = prospects == 0 ? 0 : applications * 100.0 / prospects;
        double acceptanceRate = applications == 0 ? 0 : accepted * 100.0 / applications;
        return new Dashboard(
                prospects,
                applications,
                round(conversionRate),
                sources,
                incomplete,
                accepted,
                enrolled,
                round(acceptanceRate));
    }

    @Transactional
    @PreAuthorize("hasAuthority('admissions.write')")
    public void move(
            SchoolPrincipal actor,
            String cardType,
            UUID cardId,
            AdmissionStage target,
            String comment,
            String ipAddress) {
        if ("PROSPECT".equalsIgnoreCase(cardType)) {
            moveProspect(actor, cardId, target);
        } else if ("APPLICATION".equalsIgnoreCase(cardType)) {
            moveApplication(actor, cardId, target, comment);
        } else {
            throw new AdmissionValidationException("Type de carte invalide");
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "admissions.pipeline.moved",
                cardType,
                cardId.toString(),
                ipAddress,
                Map.of("target", target.name()),
                null,
                null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('admissions.write')")
    public ActivityView schedule(
            SchoolPrincipal actor, UUID applicationId, ActivityCommand command) {
        requireApplication(actor.tenantId(), applicationId);
        if (command.scheduledAt() == null) {
            throw new AdmissionValidationException("La date et l'heure sont obligatoires");
        }
        String type = command.type() == null ? "" : command.type().strip().toUpperCase(Locale.ROOT);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        switch (type) {
            case "VISIT" -> {
                requireReference("school_campus", actor.tenantId(), command.campusId());
                jdbc.update(
                        """
                        INSERT INTO admission_visit
                            (id,tenant_id,application_id,campus_id,scheduled_at,status,notes,created_at)
                        VALUES (?, ?, ?, ?, ?, 'SCHEDULED', ?, ?)
                        """,
                        id,
                        actor.tenantId(),
                        applicationId,
                        command.campusId(),
                        timestamp(command.scheduledAt().toInstant()),
                        blankToNull(command.notes()),
                        timestamp(now));
            }
            case "INTERVIEW" ->
                    jdbc.update(
                            """
                            INSERT INTO admission_interview
                                (id,tenant_id,application_id,scheduled_at,interviewer_user_id,
                                 status,notes,created_at)
                            VALUES (?, ?, ?, ?, ?, 'SCHEDULED', ?, ?)
                            """,
                            id,
                            actor.tenantId(),
                            applicationId,
                            timestamp(command.scheduledAt().toInstant()),
                            actor.accountId(),
                            blankToNull(command.notes()),
                            timestamp(now));
            case "TEST" -> {
                jdbc.update(
                        """
                        INSERT INTO admission_test
                            (id,tenant_id,application_id,test_type,scheduled_at,status,notes,created_at)
                        VALUES (?, ?, ?, ?, ?, 'SCHEDULED', ?, ?)
                        """,
                        id,
                        actor.tenantId(),
                        applicationId,
                        blank(command.testType()) ? "GENERAL" : command.testType().strip(),
                        timestamp(command.scheduledAt().toInstant()),
                        blankToNull(command.notes()),
                        timestamp(now));
                AdmissionStage current = states.current(actor.tenantId(), applicationId);
                if (current == AdmissionStage.APPLICATION_COMPLETE) {
                    states.transition(
                            actor.tenantId(),
                            applicationId,
                            AdmissionStage.TEST_SCHEDULED,
                            "Test planifié",
                            actor.accountId(),
                            null);
                }
            }
            default -> throw new AdmissionValidationException("Activité invalide");
        }
        return new ActivityView(id, type, command.scheduledAt(), "SCHEDULED");
    }

    @Transactional
    @PreAuthorize("hasAuthority('admissions.write')")
    public DecisionView decide(
            SchoolPrincipal actor, UUID applicationId, DecisionCommand command, String ipAddress) {
        AdmissionStage target = AdmissionStage.from(command.decision());
        if (!List.of(AdmissionStage.ACCEPTED, AdmissionStage.WAITLISTED, AdmissionStage.REJECTED)
                .contains(target)) {
            throw new AdmissionValidationException("Décision invalide");
        }
        AdmissionStage current = states.current(actor.tenantId(), applicationId);
        if (!current.canTransitionTo(target)) {
            throw new AdmissionValidationException(
                    "Le dossier doit être en revue avant la décision");
        }
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO admission_decision
                        (id,tenant_id,application_id,decision,reason,decided_by_user_id,decided_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    actor.tenantId(),
                    applicationId,
                    target.name(),
                    blankToNull(command.reason()),
                    actor.accountId(),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new AdmissionConflictException("Une décision existe déjà pour ce dossier");
        }
        states.transition(
                actor.tenantId(), applicationId, target, command.reason(), actor.accountId(), null);
        UUID offerId = null;
        if (target == AdmissionStage.ACCEPTED) {
            offerId = createOffer(actor.tenantId(), applicationId, command, now);
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "admissions.decision.recorded",
                "Application",
                applicationId.toString(),
                ipAddress,
                Map.of("decision", target.name()),
                null,
                command);
        return new DecisionView(applicationId, target, now, offerId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('admissions.write')")
    public DocumentReview reviewDocument(
            SchoolPrincipal actor, UUID documentId, DocumentReviewCommand command) {
        String status =
                command.status() == null ? "" : command.status().strip().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "REJECTED").contains(status)) {
            throw new AdmissionValidationException("Décision documentaire invalide");
        }
        if ("REJECTED".equals(status) && blank(command.reason())) {
            throw new AdmissionValidationException("Le motif de rejet est obligatoire");
        }
        Instant now = clock.instant();
        int changed =
                jdbc.update(
                        """
                        UPDATE admission_document SET status=?,reviewed_by_user_id=?,reviewed_at=?,
                            rejection_reason=? WHERE tenant_id=? AND id=?
                        """,
                        status,
                        actor.accountId(),
                        timestamp(now),
                        "REJECTED".equals(status) ? command.reason().strip() : null,
                        actor.tenantId(),
                        documentId);
        if (changed == 0) throw new AdmissionNotFoundException("Document introuvable");
        return new DocumentReview(documentId, status, now);
    }

    private void moveProspect(SchoolPrincipal actor, UUID prospectId, AdmissionStage target) {
        ProspectView prospect = prospect(actor.tenantId(), prospectId);
        if (target.ordinal() > AdmissionStage.SCHOOL_VISIT.ordinal()
                || !prospect.status().canTransitionTo(target)) {
            throw new AdmissionValidationException(
                    "Créez un dossier parent pour poursuivre après la visite");
        }
        jdbc.update(
                """
                UPDATE admission_prospect SET status=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=?
                """,
                target.name(),
                timestamp(clock.instant()),
                actor.tenantId(),
                prospectId);
    }

    private void moveApplication(
            SchoolPrincipal actor, UUID applicationId, AdmissionStage target, String comment) {
        if (List.of(AdmissionStage.ACCEPTED, AdmissionStage.WAITLISTED, AdmissionStage.REJECTED)
                .contains(target)) {
            decide(
                    actor,
                    applicationId,
                    new DecisionCommand(target.name(), comment, null, null),
                    null);
            return;
        }
        states.transition(
                actor.tenantId(), applicationId, target, comment, actor.accountId(), null);
    }

    private UUID createOffer(
            UUID tenantId, UUID applicationId, DecisionCommand command, Instant now) {
        UUID id = UUID.randomUUID();
        LocalDate offeredOn = LocalDate.now(clock);
        LocalDate expiresOn =
                command.offerExpiresOn() == null
                        ? offeredOn.plusDays(14)
                        : command.offerExpiresOn();
        String reference = "OFF-" + id.toString().substring(0, 10).toUpperCase(Locale.ROOT);
        jdbc.update(
                """
                INSERT INTO admission_enrollment_offer
                    (id,tenant_id,application_id,offer_reference,offered_on,expires_on,status,
                     conditions,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OFFERED', ?, ?, ?)
                """,
                id,
                tenantId,
                applicationId,
                reference,
                offeredOn,
                expiresOn,
                blankToNull(command.offerConditions()),
                timestamp(now),
                timestamp(now));
        return id;
    }

    private CampaignView campaignView(ResultSet result) throws SQLException {
        UUID tenantId = result.getObject("tenant_id", UUID.class);
        UUID campaignId = result.getObject("id", UUID.class);
        List<StepView> steps =
                jdbc.query(
                        """
                        SELECT code,name,instructions,sequence_number,required
                        FROM admission_step WHERE tenant_id=? AND campaign_id=? ORDER BY sequence_number
                        """,
                        (stepResult, row) ->
                                new StepView(
                                        stepResult.getString("code"),
                                        stepResult.getString("name"),
                                        stepResult.getString("instructions"),
                                        stepResult.getInt("sequence_number"),
                                        stepResult.getBoolean("required")),
                        tenantId,
                        campaignId);
        List<String> required;
        try {
            required =
                    json.readValue(
                            result.getString("required_document_types"),
                            json.getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read campaign documents", exception);
        }
        return new CampaignView(
                campaignId,
                result.getString("code"),
                result.getString("name"),
                result.getString("description"),
                result.getObject("opens_on", LocalDate.class),
                result.getObject("closes_on", LocalDate.class),
                result.getBigDecimal("application_fee"),
                result.getString("currency_code"),
                required,
                result.getString("status"),
                steps);
    }

    private ProspectView prospect(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        """
                        SELECT id,campaign_id,first_name,last_name,email,phone,child_name,
                               desired_grade_code,source,status,notes,created_at
                        FROM admission_prospect WHERE tenant_id=? AND id=?
                        """,
                        (result, row) ->
                                new ProspectView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("campaign_id", UUID.class),
                                        result.getString("first_name"),
                                        result.getString("last_name"),
                                        result.getString("email"),
                                        result.getString("phone"),
                                        result.getString("child_name"),
                                        result.getString("desired_grade_code"),
                                        result.getString("source"),
                                        AdmissionStage.valueOf(result.getString("status")),
                                        result.getString("notes"),
                                        result.getTimestamp("created_at").toInstant()),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Prospect introuvable"));
    }

    private void requireCampaign(UUID tenantId, UUID campaignId) {
        requireReference("admission_campaign", tenantId, campaignId);
    }

    private void requireApplication(UUID tenantId, UUID applicationId) {
        requireReference("admission_application", tenantId, applicationId);
    }

    private void requireReference(String table, UUID tenantId, UUID id) {
        if (id == null) throw new AdmissionValidationException("Référence obligatoire");
        int count =
                scalar(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                        tenantId,
                        id);
        if (count == 0) throw new AdmissionNotFoundException("Référence introuvable");
    }

    private String tenantSlug(UUID tenantId) {
        return jdbc.queryForObject(
                "SELECT slug FROM identity_tenant WHERE id=?", String.class, tenantId);
    }

    private int count(String table, UUID tenantId, UUID campaignId) {
        return scalar(
                "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND campaign_id=?",
                tenantId,
                campaignId);
    }

    private int scalar(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AdmissionValidationException("Configuration de campagne invalide");
        }
    }

    private static List<StepCommand> defaultSteps() {
        return List.of(
                new StepCommand("CHILD", "Enfant", "Informations de l'enfant", true),
                new StepCommand("FORM", "Dossier", "Formulaire d'admission", true),
                new StepCommand("DOCUMENTS", "Pièces", "Documents justificatifs", true),
                new StepCommand("REVIEW", "Suivi", "Décision de l'école", true));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.strip();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record CampaignCommand(
            UUID academicYearId,
            String code,
            String name,
            String description,
            LocalDate opensOn,
            LocalDate closesOn,
            BigDecimal applicationFee,
            String currencyCode,
            List<String> requiredDocuments,
            String status,
            List<StepCommand> steps) {}

    public record StepCommand(String code, String name, String instructions, boolean required) {}

    public record ProspectView(
            UUID id,
            UUID campaignId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String childName,
            String desiredGradeCode,
            String source,
            AdmissionStage status,
            String notes,
            Instant createdAt) {}

    public record AdmissionBoard(UUID campaignId, List<BoardColumn> columns) {}

    public record BoardColumn(AdmissionStage status, List<BoardCard> cards) {}

    public record BoardCard(
            UUID id,
            String cardType,
            String primaryName,
            String secondaryName,
            String reference,
            AdmissionStage status,
            String source,
            String grade,
            int completenessPercent,
            String email,
            String phone,
            Instant createdAt) {}

    public record Dashboard(
            int prospects,
            int applications,
            double conversionRate,
            List<SourceMetric> sources,
            int incompleteApplications,
            int acceptances,
            int enrollments,
            double acceptanceRate) {}

    public record SourceMetric(String source, int count) {}

    public record MoveCommand(String cardType, UUID cardId, String targetStatus, String comment) {}

    public record ActivityCommand(
            String type,
            OffsetDateTime scheduledAt,
            UUID campusId,
            String testType,
            String notes) {}

    public record ActivityView(UUID id, String type, OffsetDateTime scheduledAt, String status) {}

    public record DecisionCommand(
            String decision, String reason, LocalDate offerExpiresOn, String offerConditions) {}

    public record DecisionView(
            UUID applicationId, AdmissionStage decision, Instant decidedAt, UUID offerId) {}

    public record DocumentReviewCommand(String status, String reason) {}

    public record DocumentReview(UUID documentId, String status, Instant reviewedAt) {}
}
