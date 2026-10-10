package ma.schoolz.erp.homework.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.homework.application.HomeworkService.HomeworkCommand;
import ma.schoolz.erp.homework.application.HomeworkService.LessonContentCommand;
import ma.schoolz.erp.homework.application.HomeworkService.StudentStatusCommand;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(105)
public class DevHomeworkSeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of("homework.read", "homework.write", "homework.publish");

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final HomeworkService homework;
    private final boolean enabled;
    private final String tenantSlug;

    public DevHomeworkSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            HomeworkService homework,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.homework = homework;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"))
                        .id();
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM homework_assignment WHERE tenant_id=?",
                        Integer.class,
                        tenantId);
        if (existing != null && existing >= 3) return;

        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenantId);
        UUID termId = id("SELECT id FROM academic_term WHERE tenant_id=? AND code='T1'", tenantId);
        UUID mathAssignment = assignment(tenantId, "MATH");
        UUID frenchAssignment = assignment(tenantId, "FR");
        UUID scienceAssignment = assignment(tenantId, "SCI");
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        actorId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Administrateur SchoolZ",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        PERMISSIONS);
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext seeded = SecurityContextHolder.createEmptyContext();
        seeded.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        actor,
                        null,
                        PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(seeded);
        try {
            var lesson =
                    homework.createLessonContent(
                            actor,
                            new LessonContentCommand(
                                    mathAssignment,
                                    termId,
                                    LocalDate.of(2026, 9, 7),
                                    "Comparer et ordonner des fractions",
                                    "Fractions de même dénominateur et repérage sur une"
                                            + " demi-droite.",
                                    "Nous avons représenté, comparé puis ordonné des fractions de"
                                        + " même dénominateur à partir de situations concrètes."),
                            "dev-seeder");
            homework.uploadAttachment(
                    actor,
                    "LESSON_CONTENT",
                    lesson.id(),
                    "fiche-fractions.txt",
                    "text/plain",
                    "Exercices : comparer 3/8, 5/8 et 7/8.".getBytes(StandardCharsets.UTF_8),
                    "dev-seeder");
            homework.publishLessonContent(actor, lesson.id(), "dev-seeder");

            var math =
                    homework.createHomework(
                            actor,
                            new HomeworkCommand(
                                    mathAssignment,
                                    termId,
                                    lesson.id(),
                                    "Exercices sur les fractions",
                                    "Faire les exercices 2, 3 et 5 de la fiche. Montrer les"
                                            + " étapes.",
                                    LocalDate.of(2026, 9, 7),
                                    OffsetDateTime.parse("2026-09-10T18:00:00+01:00"),
                                    30),
                            "dev-seeder");
            homework.uploadAttachment(
                    actor,
                    "HOMEWORK",
                    math.id(),
                    "consignes-fractions.txt",
                    "text/plain",
                    "Matériel : cahier, règle et crayons de couleur."
                            .getBytes(StandardCharsets.UTF_8),
                    "dev-seeder");
            homework.publishHomework(actor, math.id(), "dev-seeder");

            var french =
                    homework.createHomework(
                            actor,
                            new HomeworkCommand(
                                    frenchAssignment,
                                    termId,
                                    null,
                                    "Lecture expressive",
                                    "Lire le texte pages 12–13 et préparer deux phrases à voix"
                                            + " haute.",
                                    LocalDate.of(2026, 9, 8),
                                    OffsetDateTime.parse("2026-09-12T18:00:00+01:00"),
                                    20),
                            "dev-seeder");
            homework.publishHomework(actor, french.id(), "dev-seeder");

            var science =
                    homework.createHomework(
                            actor,
                            new HomeworkCommand(
                                    scienceAssignment,
                                    termId,
                                    null,
                                    "Observer la germination",
                                    "Observer une graine pendant trois jours et noter un changement"
                                            + " par jour.",
                                    LocalDate.of(2026, 9, 9),
                                    OffsetDateTime.parse("2026-09-16T18:00:00+01:00"),
                                    15),
                            "dev-seeder");
            homework.publishHomework(actor, science.id(), "dev-seeder");

            markMathDoneForDemoParent(tenantId, math.id());
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void markMathDoneForDemoParent(UUID tenantId, UUID homeworkId) {
        var parent =
                jdbc.queryForObject(
                        """
                        SELECT id,email,display_name,locale FROM admission_portal_account
                        WHERE tenant_id=? AND normalized_email='parent.demo@schoolz.ma'
                        """,
                        (result, row) ->
                                new PortalIdentity(
                                        tenantId,
                                        result.getObject("id", UUID.class),
                                        result.getString("email"),
                                        result.getString("display_name"),
                                        result.getString("locale")),
                        tenantId);
        UUID studentId =
                id(
                        """
                        SELECT sp.id FROM student_profile sp
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE sp.tenant_id=? AND p.first_name='Yasmine' AND p.last_name='Alaoui'
                        """,
                        tenantId);
        homework.markStatus(
                parent, homeworkId, new StudentStatusCommand(studentId, "DONE"), "dev-seeder");
    }

    private UUID assignment(UUID tenantId, String subjectCode) {
        return id(
                """
                SELECT a.id FROM timetable_teacher_assignment a
                JOIN classroom c ON c.id=a.classroom_id AND c.tenant_id=a.tenant_id
                JOIN timetable_subject s ON s.id=a.subject_id AND s.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND c.code='1AP-A' AND s.code=? AND a.active=TRUE
                ORDER BY a.created_at FETCH FIRST 1 ROW ONLY
                """,
                tenantId,
                subjectCode);
    }

    private UUID id(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }
}
