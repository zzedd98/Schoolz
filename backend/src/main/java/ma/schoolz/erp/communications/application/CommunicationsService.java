package ma.schoolz.erp.communications.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationsService {
    private static final Set<String> CATEGORIES =
            Set.of("ANNOUNCEMENT", "DIRECT_MESSAGE", "AUTOMATED_NOTIFICATION");
    private static final List<String> CHANNELS =
            List.of("IN_APP", "PUSH", "EMAIL", "SMS", "WHATSAPP");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;
    private final NotificationDeliveryService deliveries;

    public CommunicationsService(
            JdbcTemplate jdbc,
            Clock clock,
            IdentityAuditTrail audit,
            NotificationDeliveryService deliveries) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
        this.deliveries = deliveries;
    }

    @Transactional(readOnly = true)
    public CommunicationDashboard dashboard(SchoolPrincipal actor) {
        requirePermission(actor, "communication.read");
        return new CommunicationDashboard(
                clock.instant(),
                isTeacher(actor),
                campuses(actor),
                classrooms(actor),
                parents(actor),
                templates(actor.tenantId()),
                announcements(actor),
                conversations(actor),
                internalNotifications(actor));
    }

    @Transactional(readOnly = true)
    public FamilyCommunicationDashboard familyDashboard(PortalIdentity parent) {
        requireParent(parent);
        return new FamilyCommunicationDashboard(
                clock.instant(),
                parent.displayName(),
                familyAnnouncements(parent),
                familyConversations(parent),
                familyNotifications(parent),
                preferences(parent));
    }

    @Transactional
    public AnnouncementView publishAnnouncement(
            SchoolPrincipal actor, PublishAnnouncementCommand command, String ipAddress) {
        requirePermission(actor, "communication.announce");
        String title = required(command.title(), "Le titre est obligatoire", 220);
        String body = required(command.body(), "Le message est obligatoire", 10_000);
        String scope = normalize(command.audienceScope());
        if (!Set.of("SCHOOL", "CAMPUS", "CLASS").contains(scope)) {
            throw new CommunicationValidationException("Audience d'annonce invalide");
        }
        if (isTeacher(actor) && !"CLASS".equals(scope)) {
            throw new CommunicationValidationException(
                    "Un professeur peut publier uniquement vers ses classes");
        }
        UUID campusId =
                "CAMPUS".equals(scope) ? required(command.campusId(), "Campus requis") : null;
        UUID classroomId =
                "CLASS".equals(scope) ? required(command.classroomId(), "Classe requise") : null;
        validateAnnouncementScope(actor, scope, campusId, classroomId);

        UUID announcementId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO communication_announcement"
                    + " (id,tenant_id,title,body,audience_scope,campus_id,classroom_id,"
                    + " created_by_user_id,published_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                announcementId,
                actor.tenantId(),
                title,
                body,
                scope,
                campusId,
                classroomId,
                actor.accountId(),
                timestamp(now),
                timestamp(now));

        List<ParentRecipient> recipients =
                audienceRecipients(actor.tenantId(), scope, campusId, classroomId);
        for (ParentRecipient recipient : recipients) {
            deliveries.notifyParent(
                    actor.tenantId(),
                    recipient.portalAccountId(),
                    recipient.studentId(),
                    "ANNOUNCEMENT",
                    title,
                    body,
                    "ANNOUNCEMENT",
                    announcementId);
        }
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "communication.announcement.publish",
                "CommunicationAnnouncement",
                announcementId.toString(),
                ipAddress,
                Map.of("scope", scope, "recipients", recipients.size()),
                null,
                Map.of("title", title));
        return announcement(actor.tenantId(), announcementId);
    }

    @Transactional
    public ConversationView startConversation(
            SchoolPrincipal actor, StartConversationCommand command, String ipAddress) {
        requirePermission(actor, "communication.write");
        UUID parentId = required(command.portalAccountId(), "Famille requise");
        ParentAccount parent = parent(actor.tenantId(), parentId);
        UUID studentId = command.studentId();
        UUID classroomId = null;
        if (studentId != null) {
            classroomId = validateParentStudent(actor.tenantId(), parentId, studentId);
        }
        if (isTeacher(actor)) {
            if (studentId == null || classroomId == null) {
                throw new CommunicationValidationException(
                        "Le professeur doit sélectionner un élève de sa classe");
            }
            if (!teacherCanReachClass(actor, classroomId)) {
                throw new CommunicationValidationException(
                        "Cette famille n'appartient pas à une classe attribuée au professeur");
            }
        }
        String subject = required(command.subject(), "L'objet est obligatoire", 220);
        String body = required(command.body(), "Le message est obligatoire", 4_000);
        UUID conversationId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO communication_conversation"
                        + " (id,tenant_id,subject,internal_user_id,portal_account_id,student_id,"
                        + " classroom_id,status,last_message_at,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,'OPEN',?,?,?)",
                conversationId,
                actor.tenantId(),
                subject,
                actor.accountId(),
                parentId,
                studentId,
                classroomId,
                timestamp(now),
                timestamp(now),
                timestamp(now));
        insertMessage(
                actor.tenantId(), conversationId, "INTERNAL", actor.accountId(), null, body, now);
        deliveries.notifyParent(
                actor.tenantId(),
                parentId,
                studentId,
                "DIRECT_MESSAGE",
                subject,
                body,
                "CONVERSATION",
                conversationId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "communication.conversation.create",
                "CommunicationConversation",
                conversationId.toString(),
                ipAddress,
                Map.of("recipient", parent.displayName()),
                null,
                Map.of("subject", subject));
        return conversation(actor.tenantId(), conversationId);
    }

    @Transactional
    public ConversationView reply(
            SchoolPrincipal actor, UUID conversationId, ReplyCommand command, String ipAddress) {
        requirePermission(actor, "communication.write");
        ConversationContext context = conversationContext(actor.tenantId(), conversationId);
        if (!context.internalUserId().equals(actor.accountId())
                && !actor.permissions().contains("communication.manage")) {
            throw new CommunicationNotFoundException("Conversation introuvable");
        }
        if (isTeacher(actor)
                && (context.classroomId() == null
                        || !teacherCanReachClass(actor, context.classroomId()))) {
            throw new CommunicationNotFoundException("Conversation introuvable");
        }
        String body = required(command.body(), "Le message est obligatoire", 4_000);
        Instant now = clock.instant();
        insertMessage(
                actor.tenantId(), conversationId, "INTERNAL", actor.accountId(), null, body, now);
        touchConversation(actor.tenantId(), conversationId, now);
        deliveries.notifyParent(
                actor.tenantId(),
                context.portalAccountId(),
                context.studentId(),
                "DIRECT_MESSAGE",
                context.subject(),
                body,
                "CONVERSATION",
                conversationId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "communication.message.reply",
                "CommunicationConversation",
                conversationId.toString(),
                ipAddress,
                Map.of("sender", "INTERNAL"),
                null,
                Map.of("messageLength", body.length()));
        return conversation(actor.tenantId(), conversationId);
    }

    @Transactional
    public ConversationView familyReply(
            PortalIdentity parent, UUID conversationId, ReplyCommand command, String ipAddress) {
        ConversationContext context = conversationContext(parent.tenantId(), conversationId);
        if (!context.portalAccountId().equals(parent.accountId())) {
            throw new CommunicationNotFoundException("Conversation introuvable");
        }
        String body = required(command.body(), "Le message est obligatoire", 4_000);
        Instant now = clock.instant();
        insertMessage(
                parent.tenantId(), conversationId, "PARENT", null, parent.accountId(), body, now);
        touchConversation(parent.tenantId(), conversationId, now);
        deliveries.notifyInternal(
                parent.tenantId(),
                context.internalUserId(),
                "DIRECT_MESSAGE",
                context.subject(),
                body,
                "CONVERSATION",
                conversationId);
        audit.record(
                parent.tenantId(),
                null,
                "communication.message.parent-reply",
                "CommunicationConversation",
                conversationId.toString(),
                ipAddress,
                Map.of("portalAccountId", parent.accountId().toString()),
                null,
                Map.of("messageLength", body.length()));
        return conversation(parent.tenantId(), conversationId);
    }

    @Transactional
    public NotificationView sendAutomated(
            SchoolPrincipal actor, AutomatedNotificationCommand command, String ipAddress) {
        requirePermission(actor, "communication.manage");
        UUID parentId = required(command.portalAccountId(), "Famille requise");
        ParentAccount parent = parent(actor.tenantId(), parentId);
        UUID studentId = command.studentId();
        if (studentId != null) validateParentStudent(actor.tenantId(), parentId, studentId);
        TemplateContent template =
                template(actor.tenantId(), command.templateKey(), parent.locale());
        Map<String, String> variables =
                command.variables() == null ? Map.of() : command.variables();
        String title = render(template.subject(), variables);
        String body = render(template.body(), variables);
        UUID notificationId =
                deliveries.notifyParent(
                        actor.tenantId(),
                        parentId,
                        studentId,
                        "AUTOMATED_NOTIFICATION",
                        title,
                        body,
                        "TEMPLATE",
                        template.id());
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "communication.notification.automated",
                "CommunicationNotification",
                notificationId.toString(),
                ipAddress,
                Map.of("templateKey", template.key(), "recipient", parent.displayName()),
                null,
                Map.of("title", title));
        return notification(actor.tenantId(), notificationId);
    }

    @Transactional
    public List<PreferenceView> updatePreferences(
            PortalIdentity parent, UpdatePreferencesCommand command) {
        if (command.preferences() == null || command.preferences().isEmpty()) {
            throw new CommunicationValidationException("Au moins une préférence est requise");
        }
        Instant now = clock.instant();
        for (PreferenceUpdate update : command.preferences()) {
            String category = normalize(update.category());
            String channel = normalize(update.channel());
            if (!CATEGORIES.contains(category) || !CHANNELS.contains(channel)) {
                throw new CommunicationValidationException("Préférence de notification invalide");
            }
            int changed =
                    jdbc.update(
                            "UPDATE communication_notification_preference SET"
                                + " enabled=?,updated_at=? WHERE tenant_id=? AND"
                                + " recipient_kind='PARENT' AND recipient_key=? AND category=? AND"
                                + " channel=?",
                            update.enabled(),
                            timestamp(now),
                            parent.tenantId(),
                            parent.accountId().toString(),
                            category,
                            channel);
            if (changed == 0) {
                jdbc.update(
                        "INSERT INTO communication_notification_preference"
                                + " (id,tenant_id,recipient_kind,recipient_portal_account_id,"
                                + " recipient_key,category,channel,enabled,updated_at)"
                                + " VALUES (?,?,'PARENT',?,?,?,?,?,?)",
                        UUID.randomUUID(),
                        parent.tenantId(),
                        parent.accountId(),
                        parent.accountId().toString(),
                        category,
                        channel,
                        update.enabled(),
                        timestamp(now));
            }
        }
        return preferences(parent);
    }

    @Transactional
    public void readFamilyNotification(PortalIdentity parent, UUID notificationId) {
        int changed =
                jdbc.update(
                        "UPDATE communication_notification SET read_at=? WHERE tenant_id=? AND id=?"
                                + " AND recipient_kind='PARENT' AND recipient_portal_account_id=?",
                        timestamp(clock.instant()),
                        parent.tenantId(),
                        notificationId,
                        parent.accountId());
        if (changed == 0) throw new CommunicationNotFoundException("Notification introuvable");
    }

    @Transactional
    public void readInternalNotification(SchoolPrincipal actor, UUID notificationId) {
        requirePermission(actor, "communication.read");
        int changed =
                jdbc.update(
                        "UPDATE communication_notification SET read_at=? WHERE tenant_id=? AND id=?"
                                + " AND recipient_kind='INTERNAL' AND recipient_user_id=?",
                        timestamp(clock.instant()),
                        actor.tenantId(),
                        notificationId,
                        actor.accountId());
        if (changed == 0) throw new CommunicationNotFoundException("Notification introuvable");
    }

    private List<CampusOption> campuses(SchoolPrincipal actor) {
        if (isTeacher(actor)) return List.of();
        return jdbc.query(
                "SELECT id,code,name FROM school_campus WHERE tenant_id=? AND active=TRUE"
                        + " ORDER BY name",
                (result, row) ->
                        new CampusOption(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("name")),
                actor.tenantId());
    }

    private List<ClassroomOption> classrooms(SchoolPrincipal actor) {
        String teacherClause =
                isTeacher(actor)
                        ? " AND EXISTS (SELECT 1 FROM teacher_portal_profile tp JOIN"
                              + " timetable_teacher_assignment ta ON ta.tenant_id=tp.tenant_id AND"
                              + " ta.teacher_staff_id=tp.staff_profile_id AND ta.active=TRUE WHERE"
                              + " tp.tenant_id=c.tenant_id AND tp.user_account_id=? AND"
                              + " tp.active=TRUE AND ta.classroom_id=c.id)"
                        : "";
        List<Object> parameters = new ArrayList<>();
        parameters.add(actor.tenantId());
        if (isTeacher(actor)) parameters.add(actor.accountId());
        return jdbc.query(
                "SELECT c.id,c.code,c.name,sc.id campus_id,sc.name campus_name FROM classroom c"
                        + " JOIN school_campus sc ON sc.id=c.campus_id AND sc.tenant_id=c.tenant_id"
                        + " WHERE c.tenant_id=?"
                        + teacherClause
                        + " ORDER BY sc.name,c.name",
                (result, row) ->
                        new ClassroomOption(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("name"),
                                result.getObject("campus_id", UUID.class),
                                result.getString("campus_name")),
                parameters.toArray());
    }

    private List<ParentOption> parents(SchoolPrincipal actor) {
        String teacherJoin =
                isTeacher(actor)
                        ? " JOIN student_enrollment se ON se.tenant_id=l.tenant_id AND"
                                + " se.student_id=l.student_id AND se.status='ACTIVE' JOIN"
                                + " teacher_portal_profile tp ON tp.tenant_id=l.tenant_id AND"
                                + " tp.user_account_id=? AND tp.active=TRUE JOIN"
                                + " timetable_teacher_assignment ta ON ta.tenant_id=l.tenant_id AND"
                                + " ta.teacher_staff_id=tp.staff_profile_id AND"
                                + " ta.classroom_id=se.classroom_id AND ta.active=TRUE"
                        : "";
        List<Object> parameters = new ArrayList<>();
        if (isTeacher(actor)) parameters.add(actor.accountId());
        parameters.add(actor.tenantId());
        List<ParentOption> rows =
                jdbc.query(
                        "SELECT pa.id,pa.display_name,pa.email,l.student_id,"
                            + " p.first_name,p.last_name FROM admission_portal_account pa JOIN"
                            + " enrollment_portal_student_link l ON l.tenant_id=pa.tenant_id AND"
                            + " l.portal_account_id=pa.id JOIN student_profile sp ON"
                            + " sp.tenant_id=l.tenant_id AND sp.id=l.student_id JOIN person p ON"
                            + " p.tenant_id=sp.tenant_id AND p.id=sp.person_id"
                                + teacherJoin
                                + " WHERE pa.tenant_id=? AND pa.status='ACTIVE'"
                                + " ORDER BY pa.display_name,p.first_name,p.last_name",
                        (result, row) ->
                                new ParentOption(
                                        result.getObject("id", UUID.class),
                                        result.getString("display_name"),
                                        result.getString("email"),
                                        result.getObject("student_id", UUID.class),
                                        result.getString("first_name")
                                                + " "
                                                + result.getString("last_name")),
                        parameters.toArray());
        return rows.stream()
                .collect(
                        LinkedHashMap<String, ParentOption>::new,
                        (map, value) ->
                                map.putIfAbsent(
                                        value.portalAccountId() + ":" + value.studentId(), value),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .toList();
    }

    private List<TemplateView> templates(UUID tenantId) {
        return jdbc.query(
                "SELECT"
                    + " id,template_key,locale,channel,category,subject_template,body_template,active"
                    + " FROM communication_notification_template WHERE tenant_id=? ORDER BY"
                    + " template_key,locale,channel",
                (result, row) ->
                        new TemplateView(
                                result.getObject("id", UUID.class),
                                result.getString("template_key"),
                                result.getString("locale"),
                                result.getString("channel"),
                                result.getString("category"),
                                result.getString("subject_template"),
                                result.getString("body_template"),
                                result.getBoolean("active")),
                tenantId);
    }

    private List<AnnouncementView> announcements(SchoolPrincipal actor) {
        String ownership = isTeacher(actor) ? " AND a.created_by_user_id=?" : "";
        List<Object> parameters = new ArrayList<>();
        parameters.add(actor.tenantId());
        if (isTeacher(actor)) parameters.add(actor.accountId());
        return jdbc.query(
                announcementSql()
                        + " WHERE a.tenant_id=?"
                        + ownership
                        + " ORDER BY a.published_at DESC",
                (result, row) -> announcement(result),
                parameters.toArray());
    }

    private List<AnnouncementView> familyAnnouncements(PortalIdentity parent) {
        return jdbc.query(
                announcementSql()
                        + " JOIN communication_notification n ON n.tenant_id=a.tenant_id"
                        + " AND n.resource_type='ANNOUNCEMENT' AND n.resource_id=a.id"
                        + " WHERE a.tenant_id=? AND n.recipient_kind='PARENT'"
                        + " AND n.recipient_portal_account_id=? ORDER BY a.published_at DESC",
                (result, row) -> announcement(result),
                parent.tenantId(),
                parent.accountId());
    }

    private AnnouncementView announcement(UUID tenantId, UUID id) {
        return jdbc.queryForObject(
                announcementSql() + " WHERE a.tenant_id=? AND a.id=?",
                (result, row) -> announcement(result),
                tenantId,
                id);
    }

    private String announcementSql() {
        return "SELECT a.id,a.title,a.body,a.audience_scope,a.campus_id,sc.name campus_name,"
                + " a.classroom_id,c.name classroom_name,u.display_name created_by,"
                + " a.published_at,(SELECT count(*) FROM communication_notification n"
                + " WHERE n.tenant_id=a.tenant_id AND n.resource_type='ANNOUNCEMENT'"
                + " AND n.resource_id=a.id) recipient_count FROM communication_announcement a"
                + " LEFT JOIN school_campus sc ON sc.tenant_id=a.tenant_id AND sc.id=a.campus_id"
                + " LEFT JOIN classroom c ON c.tenant_id=a.tenant_id AND c.id=a.classroom_id"
                + " JOIN identity_user_account u ON u.tenant_id=a.tenant_id"
                + " AND u.id=a.created_by_user_id";
    }

    private AnnouncementView announcement(ResultSet result) throws SQLException {
        return new AnnouncementView(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("body"),
                result.getString("audience_scope"),
                result.getObject("campus_id", UUID.class),
                result.getString("campus_name"),
                result.getObject("classroom_id", UUID.class),
                result.getString("classroom_name"),
                result.getString("created_by"),
                result.getTimestamp("published_at").toInstant(),
                result.getInt("recipient_count"));
    }

    private List<ConversationView> conversations(SchoolPrincipal actor) {
        String access =
                isTeacher(actor) || !actor.permissions().contains("communication.manage")
                        ? " AND internal_user_id=?"
                        : "";
        List<Object> parameters = new ArrayList<>();
        parameters.add(actor.tenantId());
        if (!access.isEmpty()) parameters.add(actor.accountId());
        return jdbc
                .query(
                        "SELECT id FROM communication_conversation WHERE tenant_id=?"
                                + access
                                + " ORDER BY last_message_at DESC",
                        (result, row) -> result.getObject("id", UUID.class),
                        parameters.toArray())
                .stream()
                .map(id -> conversation(actor.tenantId(), id))
                .toList();
    }

    private List<ConversationView> familyConversations(PortalIdentity parent) {
        return jdbc
                .query(
                        "SELECT id FROM communication_conversation WHERE tenant_id=?"
                                + " AND portal_account_id=? ORDER BY last_message_at DESC",
                        (result, row) -> result.getObject("id", UUID.class),
                        parent.tenantId(),
                        parent.accountId())
                .stream()
                .map(id -> conversation(parent.tenantId(), id))
                .toList();
    }

    private ConversationView conversation(UUID tenantId, UUID id) {
        ConversationView summary =
                jdbc.queryForObject(
                        "SELECT cv.id,cv.subject,cv.portal_account_id,pa.display_name parent_name,"
                            + " cv.student_id,TRIM(COALESCE(p.first_name,'') || ' ' ||"
                            + " COALESCE(p.last_name,'')) student_name,cv.classroom_id, c.name"
                            + " classroom_name,u.display_name internal_name,cv.status,"
                            + " cv.last_message_at FROM communication_conversation cv JOIN"
                            + " admission_portal_account pa ON pa.tenant_id=cv.tenant_id AND"
                            + " pa.id=cv.portal_account_id JOIN identity_user_account u ON"
                            + " u.tenant_id=cv.tenant_id AND u.id=cv.internal_user_id LEFT JOIN"
                            + " student_profile sp ON sp.tenant_id=cv.tenant_id AND"
                            + " sp.id=cv.student_id LEFT JOIN person p ON p.tenant_id=sp.tenant_id"
                            + " AND p.id=sp.person_id LEFT JOIN classroom c ON"
                            + " c.tenant_id=cv.tenant_id AND c.id=cv.classroom_id WHERE"
                            + " cv.tenant_id=? AND cv.id=?",
                        (result, row) ->
                                new ConversationView(
                                        result.getObject("id", UUID.class),
                                        result.getString("subject"),
                                        result.getObject("portal_account_id", UUID.class),
                                        result.getString("parent_name"),
                                        result.getObject("student_id", UUID.class),
                                        blankToNull(result.getString("student_name")),
                                        result.getObject("classroom_id", UUID.class),
                                        result.getString("classroom_name"),
                                        result.getString("internal_name"),
                                        result.getString("status"),
                                        result.getTimestamp("last_message_at").toInstant(),
                                        List.of()),
                        tenantId,
                        id);
        List<MessageView> messages =
                jdbc.query(
                        "SELECT"
                            + " m.id,m.sender_kind,COALESCE(u.display_name,pa.display_name,'SchoolZ')"
                            + " sender_name,m.body,m.sent_at FROM communication_message m LEFT JOIN"
                            + " identity_user_account u ON u.tenant_id=m.tenant_id AND"
                            + " u.id=m.sender_user_id LEFT JOIN admission_portal_account pa ON"
                            + " pa.tenant_id=m.tenant_id AND pa.id=m.sender_portal_account_id WHERE"
                            + " m.tenant_id=? AND m.conversation_id=? ORDER BY m.sent_at",
                        (result, row) ->
                                new MessageView(
                                        result.getObject("id", UUID.class),
                                        result.getString("sender_kind"),
                                        result.getString("sender_name"),
                                        result.getString("body"),
                                        result.getTimestamp("sent_at").toInstant()),
                        tenantId,
                        id);
        return new ConversationView(
                summary.id(),
                summary.subject(),
                summary.portalAccountId(),
                summary.parentName(),
                summary.studentId(),
                summary.studentName(),
                summary.classroomId(),
                summary.classroomName(),
                summary.internalName(),
                summary.status(),
                summary.lastMessageAt(),
                messages);
    }

    private List<NotificationView> internalNotifications(SchoolPrincipal actor) {
        return notifications(actor.tenantId(), "INTERNAL", "recipient_user_id", actor.accountId());
    }

    private List<NotificationView> familyNotifications(PortalIdentity parent) {
        return notifications(
                parent.tenantId(), "PARENT", "recipient_portal_account_id", parent.accountId());
    }

    private List<NotificationView> notifications(
            UUID tenantId, String kind, String recipientColumn, UUID recipientId) {
        return jdbc.query(
                "SELECT id,category,title,body,resource_type,resource_id,created_at,read_at"
                        + " FROM communication_notification WHERE tenant_id=?"
                        + " AND recipient_kind=? AND "
                        + recipientColumn
                        + "=? ORDER BY created_at DESC",
                (result, row) -> notification(result, tenantId),
                tenantId,
                kind,
                recipientId);
    }

    private NotificationView notification(UUID tenantId, UUID id) {
        return jdbc.queryForObject(
                "SELECT id,category,title,body,resource_type,resource_id,created_at,read_at"
                        + " FROM communication_notification WHERE tenant_id=? AND id=?",
                (result, row) -> notification(result, tenantId),
                tenantId,
                id);
    }

    private NotificationView notification(ResultSet result, UUID tenantId) throws SQLException {
        UUID id = result.getObject("id", UUID.class);
        List<DeliveryView> channels =
                jdbc.query(
                        "SELECT"
                            + " channel,status,adapter_key,recipient_masked,attempted_at,error_message"
                            + " FROM communication_notification_delivery WHERE tenant_id=? AND"
                            + " notification_id=? ORDER BY channel",
                        (delivery, row) ->
                                new DeliveryView(
                                        delivery.getString("channel"),
                                        delivery.getString("status"),
                                        delivery.getString("adapter_key"),
                                        delivery.getString("recipient_masked"),
                                        delivery.getTimestamp("attempted_at").toInstant(),
                                        delivery.getString("error_message")),
                        tenantId,
                        id);
        return new NotificationView(
                id,
                result.getString("category"),
                result.getString("title"),
                result.getString("body"),
                result.getString("resource_type"),
                result.getObject("resource_id", UUID.class),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("read_at") == null
                        ? null
                        : result.getTimestamp("read_at").toInstant(),
                channels);
    }

    private List<PreferenceView> preferences(PortalIdentity parent) {
        Map<String, Boolean> overrides = new LinkedHashMap<>();
        List<PreferenceView> stored =
                jdbc.query(
                        "SELECT category,channel,enabled FROM"
                                + " communication_notification_preference WHERE tenant_id=? AND"
                                + " recipient_kind='PARENT' AND recipient_key=?",
                        (result, row) ->
                                new PreferenceView(
                                        result.getString("category"),
                                        result.getString("channel"),
                                        result.getBoolean("enabled")),
                        parent.tenantId(),
                        parent.accountId().toString());
        stored.forEach(
                value -> overrides.put(value.category() + ":" + value.channel(), value.enabled()));
        List<PreferenceView> result = new ArrayList<>();
        for (String category :
                List.of("ANNOUNCEMENT", "DIRECT_MESSAGE", "AUTOMATED_NOTIFICATION")) {
            for (String channel : CHANNELS) {
                result.add(
                        new PreferenceView(
                                category,
                                channel,
                                overrides.getOrDefault(
                                        category + ":" + channel, "IN_APP".equals(channel))));
            }
        }
        return List.copyOf(result);
    }

    private void validateAnnouncementScope(
            SchoolPrincipal actor, String scope, UUID campusId, UUID classroomId) {
        if ("CAMPUS".equals(scope)) {
            requireExists(
                    "SELECT count(*) FROM school_campus WHERE tenant_id=? AND id=? AND active=TRUE",
                    "Campus introuvable",
                    actor.tenantId(),
                    campusId);
        }
        if ("CLASS".equals(scope)) {
            requireExists(
                    "SELECT count(*) FROM classroom WHERE tenant_id=? AND id=?",
                    "Classe introuvable",
                    actor.tenantId(),
                    classroomId);
            if (isTeacher(actor) && !teacherCanReachClass(actor, classroomId)) {
                throw new CommunicationValidationException(
                        "Cette classe n'est pas attribuée au professeur");
            }
        }
    }

    private List<ParentRecipient> audienceRecipients(
            UUID tenantId, String scope, UUID campusId, UUID classroomId) {
        String filter =
                switch (scope) {
                    case "CAMPUS" -> " AND se.campus_id=?";
                    case "CLASS" -> " AND se.classroom_id=?";
                    default -> "";
                };
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        if (campusId != null) parameters.add(campusId);
        if (classroomId != null) parameters.add(classroomId);
        List<ParentRecipient> rows =
                jdbc.query(
                        "SELECT pa.id portal_account_id,l.student_id FROM admission_portal_account"
                                + " pa JOIN enrollment_portal_student_link l ON"
                                + " l.tenant_id=pa.tenant_id AND l.portal_account_id=pa.id JOIN"
                                + " student_enrollment se ON se.tenant_id=l.tenant_id AND"
                                + " se.student_id=l.student_id AND se.status='ACTIVE' WHERE"
                                + " pa.tenant_id=? AND pa.status='ACTIVE'"
                                + filter
                                + " ORDER BY pa.id,l.student_id",
                        (result, row) ->
                                new ParentRecipient(
                                        result.getObject("portal_account_id", UUID.class),
                                        result.getObject("student_id", UUID.class)),
                        parameters.toArray());
        LinkedHashMap<UUID, ParentRecipient> unique = new LinkedHashMap<>();
        rows.forEach(value -> unique.putIfAbsent(value.portalAccountId(), value));
        return List.copyOf(unique.values());
    }

    private boolean teacherCanReachClass(SchoolPrincipal actor, UUID classroomId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM teacher_portal_profile tp JOIN"
                            + " timetable_teacher_assignment ta ON ta.tenant_id=tp.tenant_id AND"
                            + " ta.teacher_staff_id=tp.staff_profile_id WHERE tp.tenant_id=? AND"
                            + " tp.user_account_id=? AND tp.active=TRUE AND ta.active=TRUE AND"
                            + " ta.classroom_id=?",
                        Integer.class,
                        actor.tenantId(),
                        actor.accountId(),
                        classroomId);
        return count != null && count > 0;
    }

    private UUID validateParentStudent(UUID tenantId, UUID parentId, UUID studentId) {
        List<UUID> classrooms =
                jdbc.query(
                        "SELECT se.classroom_id FROM enrollment_portal_student_link l LEFT JOIN"
                            + " student_enrollment se ON se.tenant_id=l.tenant_id AND"
                            + " se.student_id=l.student_id AND se.status='ACTIVE' WHERE"
                            + " l.tenant_id=? AND l.portal_account_id=? AND l.student_id=? ORDER BY"
                            + " se.updated_at DESC",
                        (result, row) -> result.getObject("classroom_id", UUID.class),
                        tenantId,
                        parentId,
                        studentId);
        if (classrooms.isEmpty()) {
            throw new CommunicationValidationException("Élève non lié à cette famille");
        }
        return classrooms.getFirst();
    }

    private ParentAccount parent(UUID tenantId, UUID parentId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id,display_name,email,locale FROM admission_portal_account"
                            + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                    (result, row) ->
                            new ParentAccount(
                                    result.getObject("id", UUID.class),
                                    result.getString("display_name"),
                                    result.getString("email"),
                                    result.getString("locale")),
                    tenantId,
                    parentId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CommunicationNotFoundException("Famille introuvable");
        }
    }

    private TemplateContent template(UUID tenantId, String templateKey, String locale) {
        String key = required(templateKey, "Modèle requis", 100);
        List<TemplateContent> candidates =
                jdbc.query(
                        "SELECT id,template_key,subject_template,body_template FROM"
                            + " communication_notification_template WHERE tenant_id=? AND"
                            + " template_key=? AND channel='IN_APP' AND active=TRUE ORDER BY CASE"
                            + " WHEN locale=? THEN 0 WHEN locale='fr-MA' THEN 1 ELSE 2 END",
                        (result, row) ->
                                new TemplateContent(
                                        result.getObject("id", UUID.class),
                                        result.getString("template_key"),
                                        result.getString("subject_template"),
                                        result.getString("body_template")),
                        tenantId,
                        key,
                        locale);
        if (candidates.isEmpty()) {
            throw new CommunicationNotFoundException("Modèle de notification introuvable");
        }
        return candidates.getFirst();
    }

    private ConversationContext conversationContext(UUID tenantId, UUID conversationId) {
        try {
            return jdbc.queryForObject(
                    "SELECT subject,internal_user_id,portal_account_id,student_id,classroom_id"
                            + " FROM communication_conversation WHERE tenant_id=? AND id=?"
                            + " AND status='OPEN'",
                    (result, row) ->
                            new ConversationContext(
                                    result.getString("subject"),
                                    result.getObject("internal_user_id", UUID.class),
                                    result.getObject("portal_account_id", UUID.class),
                                    result.getObject("student_id", UUID.class),
                                    result.getObject("classroom_id", UUID.class)),
                    tenantId,
                    conversationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CommunicationNotFoundException("Conversation introuvable");
        }
    }

    private void insertMessage(
            UUID tenantId,
            UUID conversationId,
            String senderKind,
            UUID userId,
            UUID parentId,
            String body,
            Instant sentAt) {
        jdbc.update(
                "INSERT INTO communication_message"
                        + " (id,tenant_id,conversation_id,sender_kind,sender_user_id,"
                        + " sender_portal_account_id,body,sent_at) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenantId,
                conversationId,
                senderKind,
                userId,
                parentId,
                body,
                timestamp(sentAt));
    }

    private void touchConversation(UUID tenantId, UUID id, Instant now) {
        jdbc.update(
                "UPDATE communication_conversation SET last_message_at=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(now),
                timestamp(now),
                tenantId,
                id);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private void requireParent(PortalIdentity parent) {
        parent(parent.tenantId(), parent.accountId());
    }

    private void requirePermission(SchoolPrincipal actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission requise: " + permission);
        }
    }

    private void requireExists(String sql, String message, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        if (count == null || count == 0) throw new CommunicationNotFoundException(message);
    }

    private static boolean isTeacher(SchoolPrincipal actor) {
        return actor.roles().contains("TEACHER");
    }

    private static String render(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered =
                    rendered.replace(
                            "{{" + entry.getKey() + "}}",
                            entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private static String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) throw new CommunicationValidationException(message);
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new CommunicationValidationException(message + " (maximum " + maxLength + ")");
        }
        return trimmed;
    }

    private static <T> T required(T value, String message) {
        if (value == null) throw new CommunicationValidationException(message);
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record PublishAnnouncementCommand(
            String title, String body, String audienceScope, UUID campusId, UUID classroomId) {}

    public record StartConversationCommand(
            UUID portalAccountId, UUID studentId, String subject, String body) {}

    public record ReplyCommand(String body) {}

    public record AutomatedNotificationCommand(
            String templateKey,
            UUID portalAccountId,
            UUID studentId,
            Map<String, String> variables) {}

    public record UpdatePreferencesCommand(List<PreferenceUpdate> preferences) {}

    public record PreferenceUpdate(String category, String channel, boolean enabled) {}

    public record CampusOption(UUID id, String code, String name) {}

    public record ClassroomOption(
            UUID id, String code, String name, UUID campusId, String campusName) {}

    public record ParentOption(
            UUID portalAccountId,
            String displayName,
            String email,
            UUID studentId,
            String studentName) {}

    public record TemplateView(
            UUID id,
            String key,
            String locale,
            String channel,
            String category,
            String subject,
            String body,
            boolean active) {}

    public record AnnouncementView(
            UUID id,
            String title,
            String body,
            String audienceScope,
            UUID campusId,
            String campusName,
            UUID classroomId,
            String classroomName,
            String createdBy,
            Instant publishedAt,
            int recipientCount) {}

    public record MessageView(
            UUID id, String senderKind, String senderName, String body, Instant sentAt) {}

    public record ConversationView(
            UUID id,
            String subject,
            UUID portalAccountId,
            String parentName,
            UUID studentId,
            String studentName,
            UUID classroomId,
            String classroomName,
            String internalName,
            String status,
            Instant lastMessageAt,
            List<MessageView> messages) {}

    public record DeliveryView(
            String channel,
            String status,
            String adapterKey,
            String recipientMasked,
            Instant attemptedAt,
            String errorMessage) {}

    public record NotificationView(
            UUID id,
            String category,
            String title,
            String body,
            String resourceType,
            UUID resourceId,
            Instant createdAt,
            Instant readAt,
            List<DeliveryView> deliveries) {}

    public record PreferenceView(String category, String channel, boolean enabled) {}

    public record CommunicationDashboard(
            Instant generatedAt,
            boolean teacherView,
            List<CampusOption> campuses,
            List<ClassroomOption> classrooms,
            List<ParentOption> parents,
            List<TemplateView> templates,
            List<AnnouncementView> announcements,
            List<ConversationView> conversations,
            List<NotificationView> notifications) {}

    public record FamilyCommunicationDashboard(
            Instant generatedAt,
            String parentName,
            List<AnnouncementView> announcements,
            List<ConversationView> conversations,
            List<NotificationView> notifications,
            List<PreferenceView> preferences) {}

    private record ParentRecipient(UUID portalAccountId, UUID studentId) {}

    private record ParentAccount(UUID id, String displayName, String email, String locale) {}

    private record TemplateContent(UUID id, String key, String subject, String body) {}

    private record ConversationContext(
            String subject,
            UUID internalUserId,
            UUID portalAccountId,
            UUID studentId,
            UUID classroomId) {}
}
