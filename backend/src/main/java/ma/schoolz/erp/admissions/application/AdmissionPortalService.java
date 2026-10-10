package ma.schoolz.erp.admissions.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.application.PortalAuthenticationService.PortalPrincipal;
import ma.schoolz.erp.foundation.security.FileSecurityScanner;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdmissionPortalService {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Set<String> DOCUMENT_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_DOCUMENT_SIZE = 5L * 1024L * 1024L;

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final ObjectMapper json;
    private final AdmissionStateService states;
    private final FileSecurityScanner fileSecurity;
    private final Clock clock;

    public AdmissionPortalService(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            ObjectMapper json,
            AdmissionStateService states,
            FileSecurityScanner fileSecurity,
            Clock clock) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.json = json;
        this.states = states;
        this.fileSecurity = fileSecurity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicCatalog catalog(String tenantSlug) {
        TenantDirectory.TenantSummary tenant =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new AdmissionNotFoundException("École introuvable"));
        List<CampaignView> campaigns =
                jdbc.query(
                        """
                        SELECT * FROM admission_campaign
                        WHERE tenant_id=? AND status='OPEN'
                        ORDER BY opens_on,name
                        """,
                        (result, row) -> campaignView(result),
                        tenant.id());
        List<ReferenceOption> campuses =
                jdbc.query(
                        """
                        SELECT id,code,name FROM school_campus
                        WHERE tenant_id=? AND active=TRUE ORDER BY name
                        """,
                        (result, row) ->
                                new ReferenceOption(
                                        result.getObject("id", UUID.class),
                                        result.getString("code"),
                                        result.getString("name")),
                        tenant.id());
        List<ReferenceOption> grades =
                jdbc.query(
                        """
                        SELECT id,code,name FROM grade_level
                        WHERE tenant_id=? AND active=TRUE ORDER BY sequence_number
                        """,
                        (result, row) ->
                                new ReferenceOption(
                                        result.getObject("id", UUID.class),
                                        result.getString("code"),
                                        result.getString("name")),
                        tenant.id());
        return new PublicCatalog(tenant.slug(), tenant.displayName(), campaigns, campuses, grades);
    }

    @Transactional
    public UUID createPublicProspect(String tenantSlug, ProspectCommand command) {
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new AdmissionNotFoundException("École introuvable"))
                        .id();
        validateName(command.firstName(), "Le prénom du parent est obligatoire");
        validateName(command.lastName(), "Le nom du parent est obligatoire");
        if (blank(command.email()) && blank(command.phone())) {
            throw new AdmissionValidationException("Un e-mail ou un téléphone est obligatoire");
        }
        if (command.campaignId() != null) {
            requireCampaign(tenantId, command.campaignId(), true);
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO admission_prospect
                    (id,tenant_id,campaign_id,first_name,last_name,email,phone,child_name,
                     desired_grade_code,source,status,notes,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEW_LEAD', ?, ?, ?)
                """,
                id,
                tenantId,
                command.campaignId(),
                command.firstName().strip(),
                command.lastName().strip(),
                blankToNull(command.email()),
                blankToNull(command.phone()),
                blankToNull(command.childName()),
                blankToNull(command.desiredGradeCode()),
                source(command.source()),
                blankToNull(command.notes()),
                timestamp(now),
                timestamp(now));
        return id;
    }

    @Transactional(readOnly = true)
    public PortalHome home(PortalPrincipal principal) {
        return new PortalHome(
                principal,
                applicants(principal),
                applications(principal),
                notifications(principal));
    }

    @Transactional(readOnly = true)
    public List<ApplicantView> applicants(PortalPrincipal principal) {
        return jdbc.query(
                """
                SELECT id,first_name,last_name,birth_date,gender,nationality_code,current_school
                FROM admission_applicant
                WHERE tenant_id=? AND portal_account_id=? ORDER BY created_at
                """,
                (result, row) ->
                        new ApplicantView(
                                result.getObject("id", UUID.class),
                                result.getString("first_name"),
                                result.getString("last_name"),
                                result.getObject("birth_date", LocalDate.class),
                                result.getString("gender"),
                                result.getString("nationality_code"),
                                result.getString("current_school")),
                principal.tenantId(),
                principal.accountId());
    }

    @Transactional
    public ApplicantView createApplicant(PortalPrincipal principal, ApplicantCommand command) {
        validateName(command.firstName(), "Le prénom de l'enfant est obligatoire");
        validateName(command.lastName(), "Le nom de l'enfant est obligatoire");
        if (command.birthDate() == null || command.birthDate().isAfter(LocalDate.now(clock))) {
            throw new AdmissionValidationException("Date de naissance invalide");
        }
        if (!blank(command.nationalityCode())
                && !command.nationalityCode().matches("[A-Za-z]{2}")) {
            throw new AdmissionValidationException(
                    "La nationalité doit être un code ISO sur 2 lettres");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO admission_applicant
                    (id,tenant_id,portal_account_id,first_name,last_name,birth_date,gender,
                     nationality_code,current_school,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                principal.tenantId(),
                principal.accountId(),
                command.firstName().strip(),
                command.lastName().strip(),
                command.birthDate(),
                blankToNull(command.gender()),
                upperOrNull(command.nationalityCode()),
                blankToNull(command.currentSchool()),
                timestamp(now),
                timestamp(now));
        return applicants(principal).stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Candidat introuvable"));
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> applications(PortalPrincipal principal) {
        List<UUID> ids =
                jdbc.query(
                        """
                        SELECT a.id FROM admission_application a
                        JOIN admission_applicant p
                          ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                        WHERE a.tenant_id=? AND p.portal_account_id=?
                        ORDER BY a.updated_at DESC
                        """,
                        (result, row) -> result.getObject(1, UUID.class),
                        principal.tenantId(),
                        principal.accountId());
        return ids.stream().map(id -> application(principal, id)).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationView application(PortalPrincipal principal, UUID applicationId) {
        ApplicationViewBase base = requireOwnedApplication(principal, applicationId);
        List<DocumentView> documents =
                jdbc.query(
                        """
                        SELECT id,document_type,original_file_name,content_type,size_bytes,status,
                               rejection_reason,created_at
                        FROM admission_document
                        WHERE tenant_id=? AND application_id=? ORDER BY created_at DESC
                        """,
                        (result, row) ->
                                new DocumentView(
                                        result.getObject("id", UUID.class),
                                        result.getString("document_type"),
                                        result.getString("original_file_name"),
                                        result.getString("content_type"),
                                        result.getLong("size_bytes"),
                                        result.getString("status"),
                                        result.getString("rejection_reason"),
                                        result.getTimestamp("created_at").toInstant()),
                        principal.tenantId(),
                        applicationId);
        List<StatusView> history =
                jdbc.query(
                        """
                        SELECT from_status,to_status,comment,occurred_at
                        FROM admission_application_status
                        WHERE tenant_id=? AND application_id=? ORDER BY occurred_at DESC
                        """,
                        (result, row) ->
                                new StatusView(
                                        result.getString("from_status"),
                                        result.getString("to_status"),
                                        result.getString("comment"),
                                        result.getTimestamp("occurred_at").toInstant()),
                        principal.tenantId(),
                        applicationId);
        OfferView offer = offer(principal.tenantId(), applicationId);
        String paymentStatus =
                jdbc
                        .query(
                                """
                                SELECT status FROM admission_fee_payment
                                WHERE tenant_id=? AND application_id=? ORDER BY created_at DESC
                                FETCH FIRST 1 ROWS ONLY
                                """,
                                (result, row) -> result.getString(1),
                                principal.tenantId(),
                                applicationId)
                        .stream()
                        .findFirst()
                        .orElse(base.applicationFee().signum() == 0 ? "NOT_REQUIRED" : "DUE");
        return new ApplicationView(
                base.id(),
                base.referenceNumber(),
                base.applicantId(),
                base.applicantName(),
                base.campaignId(),
                base.campaignName(),
                base.status(),
                base.completenessPercent(),
                readMap(base.formData()),
                base.applicationFee(),
                base.currencyCode(),
                paymentStatus,
                base.createdAt(),
                base.updatedAt(),
                documents,
                history,
                offer);
    }

    @Transactional
    public ApplicationView createApplication(
            PortalPrincipal principal, ApplicationCommand command) {
        CampaignView campaign = requireCampaign(principal.tenantId(), command.campaignId(), true);
        requireOwnedApplicant(principal, command.applicantId());
        requireReference(
                "school_campus",
                principal.tenantId(),
                command.desiredCampusId(),
                "Campus introuvable");
        requireReference(
                "grade_level",
                principal.tenantId(),
                command.desiredGradeLevelId(),
                "Niveau introuvable");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String reference =
                "ADM-"
                        + now.atZone(java.time.ZoneOffset.UTC).getYear()
                        + "-"
                        + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UUID prospectId = matchingProspect(principal, campaign.id());
        try {
            jdbc.update(
                    """
                    INSERT INTO admission_application
                        (id,tenant_id,campaign_id,applicant_id,prospect_id,desired_campus_id,
                         desired_grade_level_id,reference_number,current_status,form_data,
                         completeness_percent,created_at,updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'APPLICATION_STARTED', '{}', 20, ?, ?)
                    """,
                    id,
                    principal.tenantId(),
                    campaign.id(),
                    command.applicantId(),
                    prospectId,
                    command.desiredCampusId(),
                    command.desiredGradeLevelId(),
                    reference,
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new AdmissionConflictException(
                    "Un dossier existe déjà pour cet enfant et cette campagne");
        }
        states.initial(
                principal.tenantId(),
                id,
                AdmissionStage.APPLICATION_STARTED,
                principal.accountId(),
                now);
        states.notification(
                principal.tenantId(),
                principal.accountId(),
                id,
                "APPLICATION_CREATED",
                "Dossier " + reference + " créé",
                "Vous pouvez maintenant compléter le formulaire et ajouter les pièces demandées.");
        if (prospectId != null) {
            jdbc.update(
                    """
                    UPDATE admission_prospect SET status='APPLICATION_STARTED',converted_at=?,
                        updated_at=?,version=version+1 WHERE tenant_id=? AND id=?
                    """,
                    timestamp(now),
                    timestamp(now),
                    principal.tenantId(),
                    prospectId);
        }
        return application(principal, id);
    }

    @Transactional
    public ApplicationView updateApplication(
            PortalPrincipal principal, UUID applicationId, FormCommand command) {
        ApplicationViewBase application = requireOwnedApplication(principal, applicationId);
        if (application.status().ordinal() >= AdmissionStage.APPLICATION_COMPLETE.ordinal()) {
            throw new AdmissionValidationException("Un dossier soumis ne peut plus être modifié");
        }
        if (command.formData() == null || command.formData().isEmpty()) {
            throw new AdmissionValidationException("Le formulaire ne peut pas être vide");
        }
        String formData = writeJson(command.formData());
        if (formData.length() > 20_000) {
            throw new AdmissionValidationException("Le formulaire dépasse la taille autorisée");
        }
        jdbc.update(
                """
                UPDATE admission_application SET form_data=?,completeness_percent=60,
                    updated_at=?,version=version+1 WHERE tenant_id=? AND id=?
                """,
                formData,
                timestamp(clock.instant()),
                principal.tenantId(),
                applicationId);
        return application(principal, applicationId);
    }

    @Transactional
    public DocumentView uploadDocument(
            PortalPrincipal principal,
            UUID applicationId,
            String documentType,
            MultipartFile file) {
        ApplicationViewBase application = requireOwnedApplication(principal, applicationId);
        if (application.status().ordinal() >= AdmissionStage.REVIEW.ordinal()) {
            throw new AdmissionValidationException(
                    "Les pièces de ce dossier ne sont plus modifiables");
        }
        if (blank(documentType) || documentType.strip().length() > 80) {
            throw new AdmissionValidationException("Type de document invalide");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new AdmissionValidationException("Le document doit peser entre 1 octet et 5 Mo");
        }
        String contentType = file.getContentType();
        if (contentType == null || !DOCUMENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AdmissionValidationException(
                    "Seuls les fichiers PDF, JPEG et PNG sont acceptés");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new AdmissionValidationException("Le document n'a pas pu être lu");
        }
        fileSecurity.inspect(
                file.getOriginalFilename(), contentType, bytes, DOCUMENT_TYPES, MAX_DOCUMENT_SIZE);
        String originalName = safeFileName(file.getOriginalFilename());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO admission_document
                    (id,tenant_id,application_id,document_type,original_file_name,content_type,
                     size_bytes,sha256,object_key,binary_data,status,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED', ?)
                """,
                id,
                principal.tenantId(),
                applicationId,
                documentType.strip().toUpperCase(Locale.ROOT),
                originalName,
                contentType.toLowerCase(Locale.ROOT),
                bytes.length,
                sha256(bytes),
                principal.tenantId() + "/admissions/" + applicationId + "/" + id,
                bytes,
                timestamp(now));
        jdbc.update(
                """
                UPDATE admission_application
                SET completeness_percent=CASE WHEN completeness_percent<80 THEN 80
                                               ELSE completeness_percent END,
                    updated_at=?,version=version+1 WHERE tenant_id=? AND id=?
                """,
                timestamp(now),
                principal.tenantId(),
                applicationId);
        states.notification(
                principal.tenantId(),
                principal.accountId(),
                applicationId,
                "DOCUMENT_UPLOADED",
                "Pièce ajoutée",
                originalName
                        + " a bien été ajouté au dossier "
                        + application.referenceNumber()
                        + ".");
        return application(principal, applicationId).documents().stream()
                .filter(document -> document.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Document introuvable"));
    }

    @Transactional(readOnly = true)
    public DocumentContent document(PortalPrincipal principal, UUID documentId) {
        return jdbc
                .query(
                        """
                        SELECT d.original_file_name,d.content_type,d.binary_data
                        FROM admission_document d
                        JOIN admission_application a
                          ON a.id=d.application_id AND a.tenant_id=d.tenant_id
                        JOIN admission_applicant p
                          ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                        WHERE d.tenant_id=? AND d.id=? AND p.portal_account_id=?
                        """,
                        (result, row) ->
                                new DocumentContent(
                                        result.getString("original_file_name"),
                                        result.getString("content_type"),
                                        result.getBytes("binary_data")),
                        principal.tenantId(),
                        documentId,
                        principal.accountId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Document introuvable"));
    }

    @Transactional
    public ApplicationView submit(PortalPrincipal principal, UUID applicationId) {
        ApplicationViewBase application = requireOwnedApplication(principal, applicationId);
        if (application.status() != AdmissionStage.APPLICATION_STARTED) {
            throw new AdmissionValidationException("Ce dossier ne peut pas être soumis");
        }
        if (readMap(application.formData()).isEmpty()) {
            throw new AdmissionValidationException(
                    "Le formulaire doit être complété avant l'envoi");
        }
        List<String> missing =
                missingDocuments(
                        principal.tenantId(), applicationId, application.requiredDocuments());
        if (!missing.isEmpty()) {
            throw new AdmissionValidationException(
                    "Pièces obligatoires manquantes : " + String.join(", ", missing));
        }
        jdbc.update(
                "UPDATE admission_application SET completeness_percent=100 WHERE tenant_id=? AND"
                        + " id=?",
                principal.tenantId(),
                applicationId);
        states.transition(
                principal.tenantId(),
                applicationId,
                AdmissionStage.APPLICATION_COMPLETE,
                "Dossier soumis par le parent",
                null,
                principal.accountId());
        return application(principal, applicationId);
    }

    @Transactional
    public ApplicationView payFee(
            PortalPrincipal principal, UUID applicationId, PaymentCommand command) {
        ApplicationViewBase application = requireOwnedApplication(principal, applicationId);
        if (application.applicationFee().signum() <= 0) {
            throw new AdmissionValidationException("Aucun frais n'est demandé pour ce dossier");
        }
        if (command.cardLast4() == null || !command.cardLast4().matches("[0-9]{4}")) {
            throw new AdmissionValidationException(
                    "Les quatre derniers chiffres de la carte sont requis");
        }
        Integer paid =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM admission_fee_payment
                        WHERE tenant_id=? AND application_id=? AND status='PAID'
                        """,
                        Integer.class,
                        principal.tenantId(),
                        applicationId);
        if (paid != null && paid > 0) {
            throw new AdmissionConflictException("Les frais de ce dossier sont déjà réglés");
        }
        Instant now = clock.instant();
        String reference =
                "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        jdbc.update(
                """
                INSERT INTO admission_fee_payment
                    (id,tenant_id,application_id,amount,currency_code,status,payment_method,
                     payment_reference,paid_at,created_at)
                VALUES (?, ?, ?, ?, ?, 'PAID', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                principal.tenantId(),
                applicationId,
                application.applicationFee(),
                application.currencyCode(),
                "DEMO_CARD_" + command.cardLast4(),
                reference,
                timestamp(now),
                timestamp(now));
        states.notification(
                principal.tenantId(),
                principal.accountId(),
                applicationId,
                "FEE_PAID",
                "Frais d'admission réglés",
                "Paiement " + reference + " enregistré avec succès.");
        return application(principal, applicationId);
    }

    @Transactional
    public ApplicationView acceptOffer(PortalPrincipal principal, UUID offerId) {
        OfferOwner offer =
                jdbc
                        .query(
                                """
                                SELECT o.application_id,o.status,o.expires_on
                                FROM admission_enrollment_offer o
                                JOIN admission_application a
                                  ON a.id=o.application_id AND a.tenant_id=o.tenant_id
                                JOIN admission_applicant p
                                  ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                                WHERE o.tenant_id=? AND o.id=? AND p.portal_account_id=?
                                """,
                                (result, row) ->
                                        new OfferOwner(
                                                result.getObject("application_id", UUID.class),
                                                result.getString("status"),
                                                result.getObject("expires_on", LocalDate.class)),
                                principal.tenantId(),
                                offerId,
                                principal.accountId())
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AdmissionNotFoundException("Offre introuvable"));
        if (!"OFFERED".equals(offer.status()) || offer.expiresOn().isBefore(LocalDate.now(clock))) {
            throw new AdmissionValidationException("Cette offre ne peut plus être acceptée");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE admission_enrollment_offer
                SET status='ACCEPTED',responded_at=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=?
                """,
                timestamp(now),
                timestamp(now),
                principal.tenantId(),
                offerId);
        states.transition(
                principal.tenantId(),
                offer.applicationId(),
                AdmissionStage.ENROLLED,
                "Offre acceptée par le parent",
                null,
                principal.accountId());
        return application(principal, offer.applicationId());
    }

    @Transactional(readOnly = true)
    public List<NotificationView> notifications(PortalPrincipal principal) {
        return jdbc.query(
                """
                SELECT id,application_id,notification_type,title,message,read_at,created_at
                FROM admission_notification
                WHERE tenant_id=? AND portal_account_id=? ORDER BY created_at DESC
                FETCH FIRST 50 ROWS ONLY
                """,
                (result, row) ->
                        new NotificationView(
                                result.getObject("id", UUID.class),
                                result.getObject("application_id", UUID.class),
                                result.getString("notification_type"),
                                result.getString("title"),
                                result.getString("message"),
                                result.getTimestamp("read_at") == null
                                        ? null
                                        : result.getTimestamp("read_at").toInstant(),
                                result.getTimestamp("created_at").toInstant()),
                principal.tenantId(),
                principal.accountId());
    }

    @Transactional
    public void markNotificationRead(PortalPrincipal principal, UUID notificationId) {
        int changed =
                jdbc.update(
                        """
                        UPDATE admission_notification SET read_at=?
                        WHERE tenant_id=? AND portal_account_id=? AND id=?
                        """,
                        timestamp(clock.instant()),
                        principal.tenantId(),
                        principal.accountId(),
                        notificationId);
        if (changed == 0) throw new AdmissionNotFoundException("Notification introuvable");
    }

    private CampaignView requireCampaign(UUID tenantId, UUID campaignId, boolean requireOpen) {
        CampaignView campaign =
                jdbc
                        .query(
                                "SELECT * FROM admission_campaign WHERE tenant_id=? AND id=?",
                                (result, row) -> campaignView(result),
                                tenantId,
                                campaignId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new AdmissionNotFoundException("Campagne introuvable"));
        LocalDate today = LocalDate.now(clock);
        if (requireOpen
                && (!"OPEN".equals(campaign.status())
                        || today.isBefore(campaign.opensOn())
                        || today.isAfter(campaign.closesOn()))) {
            throw new AdmissionValidationException(
                    "Cette campagne n'accepte pas de dossier aujourd'hui");
        }
        return campaign;
    }

    private CampaignView campaignView(ResultSet result) throws SQLException {
        UUID tenantId = result.getObject("tenant_id", UUID.class);
        UUID campaignId = result.getObject("id", UUID.class);
        List<StepView> steps =
                jdbc.query(
                        """
                        SELECT code,name,instructions,sequence_number,required
                        FROM admission_step WHERE tenant_id=? AND campaign_id=?
                        ORDER BY sequence_number
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
        return new CampaignView(
                campaignId,
                result.getString("code"),
                result.getString("name"),
                result.getString("description"),
                result.getObject("opens_on", LocalDate.class),
                result.getObject("closes_on", LocalDate.class),
                result.getBigDecimal("application_fee"),
                result.getString("currency_code"),
                readStrings(result.getString("required_document_types")),
                result.getString("status"),
                steps);
    }

    private ApplicationViewBase requireOwnedApplication(
            PortalPrincipal principal, UUID applicationId) {
        return jdbc
                .query(
                        """
                        SELECT a.id,a.reference_number,a.applicant_id,
                               p.first_name || ' ' || p.last_name AS applicant_name,
                               a.campaign_id,c.name AS campaign_name,a.current_status,
                               a.completeness_percent,a.form_data,c.application_fee,c.currency_code,
                               c.required_document_types,a.created_at,a.updated_at
                        FROM admission_application a
                        JOIN admission_applicant p
                          ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                        JOIN admission_campaign c
                          ON c.id=a.campaign_id AND c.tenant_id=a.tenant_id
                        WHERE a.tenant_id=? AND a.id=? AND p.portal_account_id=?
                        """,
                        (result, row) ->
                                new ApplicationViewBase(
                                        result.getObject("id", UUID.class),
                                        result.getString("reference_number"),
                                        result.getObject("applicant_id", UUID.class),
                                        result.getString("applicant_name"),
                                        result.getObject("campaign_id", UUID.class),
                                        result.getString("campaign_name"),
                                        AdmissionStage.valueOf(result.getString("current_status")),
                                        result.getInt("completeness_percent"),
                                        result.getString("form_data"),
                                        result.getBigDecimal("application_fee"),
                                        result.getString("currency_code"),
                                        readStrings(result.getString("required_document_types")),
                                        result.getTimestamp("created_at").toInstant(),
                                        result.getTimestamp("updated_at").toInstant()),
                        principal.tenantId(),
                        applicationId,
                        principal.accountId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Dossier introuvable"));
    }

    private void requireOwnedApplicant(PortalPrincipal principal, UUID applicantId) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM admission_applicant
                        WHERE tenant_id=? AND id=? AND portal_account_id=?
                        """,
                        Integer.class,
                        principal.tenantId(),
                        applicantId,
                        principal.accountId());
        if (count == null || count == 0) {
            throw new AdmissionNotFoundException("Candidat introuvable");
        }
    }

    private void requireReference(String table, UUID tenantId, UUID id, String message) {
        if (id == null) throw new AdmissionValidationException(message);
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        id);
        if (count == null || count == 0) throw new AdmissionValidationException(message);
    }

    private UUID matchingProspect(PortalPrincipal principal, UUID campaignId) {
        return jdbc
                .query(
                        """
                        SELECT id FROM admission_prospect
                        WHERE tenant_id=? AND campaign_id=? AND lower(email)=? AND converted_at IS NULL
                        ORDER BY created_at DESC FETCH FIRST 1 ROWS ONLY
                        """,
                        (result, row) -> result.getObject(1, UUID.class),
                        principal.tenantId(),
                        campaignId,
                        principal.email().toLowerCase(Locale.ROOT))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private OfferView offer(UUID tenantId, UUID applicationId) {
        return jdbc
                .query(
                        """
                        SELECT id,offer_reference,offered_on,expires_on,status,conditions,responded_at
                        FROM admission_enrollment_offer
                        WHERE tenant_id=? AND application_id=?
                        """,
                        (result, row) ->
                                new OfferView(
                                        result.getObject("id", UUID.class),
                                        result.getString("offer_reference"),
                                        result.getObject("offered_on", LocalDate.class),
                                        result.getObject("expires_on", LocalDate.class),
                                        result.getString("status"),
                                        result.getString("conditions"),
                                        result.getTimestamp("responded_at") == null
                                                ? null
                                                : result.getTimestamp("responded_at").toInstant()),
                        tenantId,
                        applicationId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<String> missingDocuments(
            UUID tenantId, UUID applicationId, List<String> requiredDocuments) {
        List<String> uploaded =
                jdbc.query(
                        """
                        SELECT DISTINCT document_type FROM admission_document
                        WHERE tenant_id=? AND application_id=? AND status<>'REJECTED'
                        """,
                        (result, row) -> result.getString(1),
                        tenantId,
                        applicationId);
        List<String> missing = new ArrayList<>();
        requiredDocuments.stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !uploaded.contains(value))
                .forEach(missing::add);
        return missing;
    }

    private Map<String, Object> readMap(String source) {
        try {
            return json.readValue(source, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read admission form", exception);
        }
    }

    private List<String> readStrings(String source) {
        try {
            return json.readValue(source, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read admission configuration", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AdmissionValidationException("Le formulaire contient des données invalides");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash admission document", exception);
        }
    }

    private static String safeFileName(String value) {
        String name = value == null ? "document" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        if (name.isBlank()) return "document";
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private static void validateName(String value, String message) {
        if (blank(value) || value.strip().length() > 120) {
            throw new AdmissionValidationException(message);
        }
    }

    private static String source(String value) {
        return blank(value) ? "WEBSITE" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.strip();
    }

    private static String upperOrNull(String value) {
        return blank(value) ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record PublicCatalog(
            String tenantSlug,
            String schoolName,
            List<CampaignView> campaigns,
            List<ReferenceOption> campuses,
            List<ReferenceOption> grades) {}

    public record ReferenceOption(UUID id, String code, String name) {}

    public record CampaignView(
            UUID id,
            String code,
            String name,
            String description,
            LocalDate opensOn,
            LocalDate closesOn,
            BigDecimal applicationFee,
            String currencyCode,
            List<String> requiredDocuments,
            String status,
            List<StepView> steps) {}

    public record StepView(
            String code, String name, String instructions, int sequenceNumber, boolean required) {}

    public record ProspectCommand(
            UUID campaignId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String childName,
            String desiredGradeCode,
            String source,
            String notes) {}

    public record PortalHome(
            PortalPrincipal account,
            List<ApplicantView> applicants,
            List<ApplicationView> applications,
            List<NotificationView> notifications) {}

    public record ApplicantCommand(
            String firstName,
            String lastName,
            LocalDate birthDate,
            String gender,
            String nationalityCode,
            String currentSchool) {}

    public record ApplicantView(
            UUID id,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String gender,
            String nationalityCode,
            String currentSchool) {}

    public record ApplicationCommand(
            UUID campaignId, UUID applicantId, UUID desiredCampusId, UUID desiredGradeLevelId) {}

    public record FormCommand(Map<String, Object> formData) {}

    public record PaymentCommand(String cardLast4) {}

    public record ApplicationView(
            UUID id,
            String referenceNumber,
            UUID applicantId,
            String applicantName,
            UUID campaignId,
            String campaignName,
            AdmissionStage status,
            int completenessPercent,
            Map<String, Object> formData,
            BigDecimal applicationFee,
            String currencyCode,
            String paymentStatus,
            Instant createdAt,
            Instant updatedAt,
            List<DocumentView> documents,
            List<StatusView> history,
            OfferView offer) {}

    private record ApplicationViewBase(
            UUID id,
            String referenceNumber,
            UUID applicantId,
            String applicantName,
            UUID campaignId,
            String campaignName,
            AdmissionStage status,
            int completenessPercent,
            String formData,
            BigDecimal applicationFee,
            String currencyCode,
            List<String> requiredDocuments,
            Instant createdAt,
            Instant updatedAt) {}

    public record DocumentView(
            UUID id,
            String documentType,
            String originalFileName,
            String contentType,
            long sizeBytes,
            String status,
            String rejectionReason,
            Instant createdAt) {}

    public record DocumentContent(String fileName, String contentType, byte[] bytes) {}

    public record StatusView(
            String fromStatus, String toStatus, String comment, Instant occurredAt) {}

    public record OfferView(
            UUID id,
            String reference,
            LocalDate offeredOn,
            LocalDate expiresOn,
            String status,
            String conditions,
            Instant respondedAt) {}

    public record NotificationView(
            UUID id,
            UUID applicationId,
            String type,
            String title,
            String message,
            Instant readAt,
            Instant createdAt) {}

    private record OfferOwner(UUID applicationId, String status, LocalDate expiresOn) {}
}
