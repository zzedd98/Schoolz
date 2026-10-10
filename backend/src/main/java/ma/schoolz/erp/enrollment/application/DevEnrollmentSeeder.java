package ma.schoolz.erp.enrollment.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(50)
public class DevEnrollmentSeeder implements ApplicationRunner {
    private static final String PARENT_EMAIL = "parent.demo@schoolz.ma";
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevEnrollmentSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"))
                        .id();
        UUID accountId =
                jdbc
                        .query(
                                "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                        + " normalized_email=?",
                                (result, row) -> result.getObject(1, UUID.class),
                                tenantId,
                                PARENT_EMAIL)
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (accountId == null) return;

        SeedOrigin origin =
                jdbc
                        .query(
                                """
                                SELECT a.id,a.reference_number,a.desired_campus_id,c.academic_year_id,o.id AS offer_id
                                FROM admission_application a
                                JOIN admission_applicant p ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                                JOIN admission_campaign c ON c.id=a.campaign_id AND c.tenant_id=a.tenant_id
                                JOIN admission_enrollment_offer o ON o.application_id=a.id AND o.tenant_id=a.tenant_id
                                WHERE a.tenant_id=? AND p.portal_account_id=?
                                ORDER BY a.created_at DESC FETCH FIRST 1 ROWS ONLY
                                """,
                                (result, row) ->
                                        new SeedOrigin(
                                                result.getObject("id", UUID.class),
                                                result.getString("reference_number"),
                                                result.getObject("desired_campus_id", UUID.class),
                                                result.getObject("academic_year_id", UUID.class),
                                                result.getObject("offer_id", UUID.class)),
                                tenantId,
                                accountId)
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (origin == null || workflowExists(tenantId, origin.applicationId())) return;

        Instant now = clock.instant();
        jdbc.update(
                "UPDATE admission_enrollment_offer SET"
                        + " status='ACCEPTED',responded_at=?,updated_at=?, version=version+1 WHERE"
                        + " tenant_id=? AND id=?",
                timestamp(now),
                timestamp(now),
                tenantId,
                origin.offerId());
        jdbc.update(
                "UPDATE admission_application SET current_status='ENROLLED',updated_at=? WHERE"
                        + " tenant_id=? AND id=?",
                timestamp(now),
                tenantId,
                origin.applicationId());
        jdbc.update(
                """
                INSERT INTO admission_application_status
                    (id,tenant_id,application_id,from_status,to_status,comment,
                     changed_by_portal_account_id,occurred_at)
                VALUES (?, ?, ?, 'ACCEPTED', 'ENROLLED', 'Offre acceptÃ©e pour la dÃ©monstration', ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                origin.applicationId(),
                accountId,
                timestamp(now));

        UUID workflowId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO enrollment_workflow
                    (id,tenant_id,portal_account_id,application_id,academic_year_id,campus_id,
                     workflow_type,current_step,status,registration_fee,currency_code,
                     required_document_types,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'INITIAL', 'CONTRACT', 'IN_PROGRESS', 1000.00, 'MAD',
                        '["IDENTITY_COPY"]', ?, ?)
                """,
                workflowId,
                tenantId,
                accountId,
                origin.applicationId(),
                origin.academicYearId(),
                origin.campusId(),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO enrollment_contract
                    (id,tenant_id,workflow_id,contract_version,title,body,status,issued_at)
                VALUES (?, ?, ?, '2026.1', ?,
                        'Confirmez les informations, les services choisis et le rÃ¨glement intÃ©rieur.',
                        'ISSUED', ?)
                """,
                UUID.randomUUID(),
                tenantId,
                workflowId,
                "Contrat d'inscription SchoolZ â€” " + origin.reference(),
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO enrollment_family_confirmation
                    (id,tenant_id,workflow_id,family_data,confirmed,updated_at)
                VALUES (?, ?, ?,
                        '{"guardianFirstName":"Famille","guardianLastName":"DÃ©mo","email":"parent.demo@schoolz.ma","phone":"+212600000099","relationship":"PARENT","addressLine1":"12 rue des Ã‰coles","city":"Casablanca"}',
                        FALSE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                workflowId,
                timestamp(now));
        jdbc.update(
                """
                INSERT INTO enrollment_service_selection
                    (id,tenant_id,workflow_id,activities,payment_plan,confirmed,updated_at)
                VALUES (?, ?, ?, '[]', 'MONTHLY', FALSE, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                workflowId,
                timestamp(now));
    }

    private boolean workflowExists(UUID tenantId, UUID applicationId) {
        Integer value =
                jdbc.queryForObject(
                        "SELECT count(*) FROM enrollment_workflow WHERE tenant_id=? AND"
                                + " application_id=?",
                        Integer.class,
                        tenantId,
                        applicationId);
        return value != null && value > 0;
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record SeedOrigin(
            UUID applicationId,
            String reference,
            UUID campusId,
            UUID academicYearId,
            UUID offerId) {}
}
