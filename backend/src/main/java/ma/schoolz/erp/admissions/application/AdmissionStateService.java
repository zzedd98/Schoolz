package ma.schoolz.erp.admissions.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdmissionStateService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AdmissionStateService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public AdmissionStage current(UUID tenantId, UUID applicationId) {
        return jdbc
                .query(
                        "SELECT current_status FROM admission_application WHERE tenant_id=? AND"
                                + " id=?",
                        (result, row) -> AdmissionStage.valueOf(result.getString(1)),
                        tenantId,
                        applicationId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AdmissionNotFoundException("Dossier introuvable"));
    }

    public void transition(
            UUID tenantId,
            UUID applicationId,
            AdmissionStage target,
            String comment,
            UUID userId,
            UUID portalAccountId) {
        AdmissionStage current = current(tenantId, applicationId);
        if (!current.canTransitionTo(target)) {
            throw new AdmissionValidationException(
                    "Transition non autorisée de " + current + " vers " + target);
        }
        if (current == target) return;
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE admission_application
                SET current_status=?,updated_at=?,version=version+1,
                    submitted_at=CASE WHEN ?='APPLICATION_COMPLETE' THEN ? ELSE submitted_at END,
                    enrolled_at=CASE WHEN ?='ENROLLED' THEN ? ELSE enrolled_at END
                WHERE tenant_id=? AND id=?
                """,
                target.name(),
                timestamp(now),
                target.name(),
                timestamp(now),
                target.name(),
                timestamp(now),
                tenantId,
                applicationId);
        jdbc.update(
                """
                INSERT INTO admission_application_status
                    (id,tenant_id,application_id,from_status,to_status,comment,
                     changed_by_user_id,changed_by_portal_account_id,occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                applicationId,
                current.name(),
                target.name(),
                blankToNull(comment),
                userId,
                portalAccountId,
                timestamp(now));
        notifyParent(tenantId, applicationId, target, now);
    }

    public void initial(
            UUID tenantId,
            UUID applicationId,
            AdmissionStage status,
            UUID portalAccountId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO admission_application_status
                    (id,tenant_id,application_id,to_status,comment,
                     changed_by_portal_account_id,occurred_at)
                VALUES (?, ?, ?, ?, 'Dossier créé', ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                applicationId,
                status.name(),
                portalAccountId,
                timestamp(now));
    }

    public void notification(
            UUID tenantId,
            UUID accountId,
            UUID applicationId,
            String type,
            String title,
            String message) {
        jdbc.update(
                """
                INSERT INTO admission_notification
                    (id,tenant_id,portal_account_id,application_id,notification_type,
                     title,message,created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                accountId,
                applicationId,
                type,
                title,
                message,
                timestamp(clock.instant()));
    }

    private void notifyParent(
            UUID tenantId, UUID applicationId, AdmissionStage target, Instant now) {
        jdbc
                .query(
                        """
                        SELECT p.portal_account_id,a.reference_number
                        FROM admission_application a
                        JOIN admission_applicant p
                          ON p.id=a.applicant_id AND p.tenant_id=a.tenant_id
                        WHERE a.tenant_id=? AND a.id=?
                        """,
                        (result, row) ->
                                new ParentApplication(
                                        result.getObject(1, UUID.class), result.getString(2)),
                        tenantId,
                        applicationId)
                .stream()
                .findFirst()
                .ifPresent(
                        owner ->
                                jdbc.update(
                                        """
                                        INSERT INTO admission_notification
                                            (id,tenant_id,portal_account_id,application_id,
                                             notification_type,title,message,created_at)
                                        VALUES (?, ?, ?, ?, 'STATUS_CHANGED', ?, ?, ?)
                                        """,
                                        UUID.randomUUID(),
                                        tenantId,
                                        owner.accountId(),
                                        applicationId,
                                        "Mise à jour du dossier " + owner.reference(),
                                        "Votre dossier est maintenant à l'étape " + target.name(),
                                        timestamp(now)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private record ParentApplication(UUID accountId, String reference) {}
}
