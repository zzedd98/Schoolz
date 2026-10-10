package ma.schoolz.erp.grading.application;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.grading.application.GradingService.ReportCardView;
import ma.schoolz.erp.grading.application.ReportCardPublicationService.PublicationCommand;
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
@Order(95)
public class DevReportCardSeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of("grade.read", "report-card.calculate", "report-card.publish");

    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final GradingService grading;
    private final ReportCardPublicationService publications;
    private final boolean enabled;
    private final String tenantSlug;

    public DevReportCardSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            GradingService grading,
            ReportCardPublicationService publications,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.grading = grading;
        this.publications = publications;
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
        Integer published =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM grading_report_card WHERE tenant_id=? AND"
                                + " status='PUBLISHED'",
                        Integer.class,
                        tenantId);
        if (published != null && published >= 3) return;

        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenantId);
        UUID classroomId =
                id("SELECT id FROM classroom WHERE tenant_id=? AND code='1AP-A'", tenantId);
        UUID termId = id("SELECT id FROM academic_term WHERE tenant_id=? AND code='T1'", tenantId);
        UUID templateId =
                id(
                        "SELECT id FROM grading_report_card_template WHERE tenant_id=? AND"
                                + " code='PRIMARY-TRIMESTER'",
                        tenantId);
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
            Integer existing =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM grading_report_card WHERE tenant_id=? AND"
                                    + " classroom_id=? AND academic_term_id=?",
                            Integer.class,
                            tenantId,
                            classroomId,
                            termId);
            List<ReportCardView> available =
                    existing == null || existing == 0
                            ? grading.calculateReportCards(
                                    actor, classroomId, termId, templateId, "dev-seeder")
                            : grading.dashboard(actor, classroomId, termId).reportCards();
            List<ReportCardView> reports =
                    available.stream()
                            .sorted(Comparator.comparing(ReportCardView::studentName))
                            .toList();
            List<String> languages = List.of("FR", "AR", "BILINGUAL");
            for (int index = 0; index < reports.size(); index++) {
                if (!reports.get(index).status().equals("CALCULATED")) continue;
                publications.publish(
                        actor,
                        reports.get(index).id(),
                        new PublicationCommand(
                                languages.get(index % languages.size()),
                                "Nadia El Mansouri",
                                "Directrice",
                                "Démonstration SchoolZ - jalon 11"),
                        "dev-seeder");
            }
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private UUID id(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }
}
