package ma.schoolz.erp.communications.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.communications.application.CommunicationsService.AutomatedNotificationCommand;
import ma.schoolz.erp.communications.application.CommunicationsService.PublishAnnouncementCommand;
import ma.schoolz.erp.communications.application.CommunicationsService.StartConversationCommand;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
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
@Order(140)
public class DevCommunicationsSeeder implements ApplicationRunner {
    private static final List<String> CHANNELS =
            List.of("IN_APP", "PUSH", "EMAIL", "SMS", "WHATSAPP");
    private static final List<String> CATEGORIES =
            List.of("ANNOUNCEMENT", "DIRECT_MESSAGE", "AUTOMATED_NOTIFICATION");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CommunicationsService communications;
    private final boolean enabled;
    private final String tenantSlug;

    public DevCommunicationsSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            CommunicationsService communications,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.communications = communications;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = singleId("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        UUID adminId =
                singleId(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?"
                                + " AND normalized_email='admin@schoolz.ma'",
                        tenantId);
        UUID parentId =
                singleId(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=?"
                                + " AND normalized_email='parent.demo@schoolz.ma'",
                        tenantId);
        UUID studentId =
                singleId(
                        "SELECT student_id FROM enrollment_portal_student_link"
                                + " WHERE tenant_id=? AND portal_account_id=? ORDER BY created_at",
                        tenantId,
                        parentId);
        seedTemplates(tenantId);
        seedPreferences(tenantId, parentId);

        SchoolPrincipal admin =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Administrateur SchoolZ",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        Set.of(
                                "communication.read",
                                "communication.write",
                                "communication.announce",
                                "communication.manage"));

        if (count("SELECT count(*) FROM communication_announcement WHERE tenant_id=?", tenantId)
                == 0) {
            communications.publishAnnouncement(
                    admin,
                    new PublishAnnouncementCommand(
                            "Bienvenue dans le centre de communications",
                            "SchoolZ réunit désormais annonces, messages directs et notifications"
                                    + " multicanales dans un historique unique.",
                            "SCHOOL",
                            null,
                            null),
                    "seed");
        }
        if (count("SELECT count(*) FROM communication_conversation WHERE tenant_id=?", tenantId)
                == 0) {
            communications.startConversation(
                    admin,
                    new StartConversationCommand(
                            parentId,
                            studentId,
                            "Rentrée scolaire de Yasmine",
                            "Bonjour, les informations de rentrée sont disponibles. Vous pouvez"
                                    + " répondre directement dans ce fil."),
                    "seed");
        }
        if (count(
                        "SELECT count(*) FROM communication_notification WHERE tenant_id=?"
                                + " AND category='AUTOMATED_NOTIFICATION'",
                        tenantId)
                == 0) {
            communications.sendAutomated(
                    admin,
                    new AutomatedNotificationCommand(
                            "attendance-follow-up",
                            parentId,
                            studentId,
                            Map.of("student", "Yasmine")),
                    "seed");
        }
    }

    private void seedTemplates(UUID tenantId) {
        for (String channel : CHANNELS) {
            insertTemplate(
                    tenantId,
                    channel,
                    "fr-MA",
                    "Suivi de présence · {{student}}",
                    "Une mise à jour de présence concerne {{student}}. Consultez SchoolZ pour le"
                            + " détail.");
            insertTemplate(
                    tenantId,
                    channel,
                    "ar-MA",
                    "تتبع الحضور · {{student}}",
                    "هناك تحديث يخص حضور {{student}}. المرجو الاطلاع على التفاصيل في SchoolZ.");
        }
    }

    private void insertTemplate(
            UUID tenantId, String channel, String locale, String subject, String body) {
        if (count(
                        "SELECT count(*) FROM communication_notification_template"
                                + " WHERE tenant_id=? AND template_key='attendance-follow-up'"
                                + " AND locale=? AND channel=?",
                        tenantId,
                        locale,
                        channel)
                > 0) return;
        jdbc.update(
                "INSERT INTO communication_notification_template"
                        + " (id,tenant_id,template_key,locale,channel,category,subject_template,"
                        + " body_template,active,created_at,updated_at)"
                        + " VALUES (?,?,'attendance-follow-up',?,?,'AUTOMATED_NOTIFICATION',"
                        + " ?,?,TRUE,?,?)",
                UUID.randomUUID(),
                tenantId,
                locale,
                channel,
                subject,
                body,
                timestamp(clock.instant()),
                timestamp(clock.instant()));
    }

    private void seedPreferences(UUID tenantId, UUID parentId) {
        for (String category : CATEGORIES) {
            for (String channel : CHANNELS) {
                if (count(
                                "SELECT count(*) FROM communication_notification_preference"
                                        + " WHERE tenant_id=? AND recipient_kind='PARENT'"
                                        + " AND recipient_key=? AND category=? AND channel=?",
                                tenantId,
                                parentId.toString(),
                                category,
                                channel)
                        > 0) continue;
                boolean enabled = Set.of("IN_APP", "PUSH", "EMAIL").contains(channel);
                jdbc.update(
                        "INSERT INTO communication_notification_preference"
                                + " (id,tenant_id,recipient_kind,recipient_portal_account_id,"
                                + " recipient_key,category,channel,enabled,updated_at)"
                                + " VALUES (?,?,'PARENT',?,?,?,?,?,?)",
                        UUID.randomUUID(),
                        tenantId,
                        parentId,
                        parentId.toString(),
                        category,
                        channel,
                        enabled,
                        timestamp(clock.instant()));
            }
        }
    }

    private UUID singleId(String sql, Object... args) {
        List<UUID> values = jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
        if (values.isEmpty()) {
            throw new IllegalStateException("Donnée de démonstration requise introuvable");
        }
        return values.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return Timestamp.from(value);
    }
}
