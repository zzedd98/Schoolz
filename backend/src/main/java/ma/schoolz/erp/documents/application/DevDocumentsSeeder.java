package ma.schoolz.erp.documents.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.documents.application.DocumentManagementService.GeneratePdfCommand;
import ma.schoolz.erp.documents.application.DocumentManagementService.ShareCommand;
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
@Order(150)
public class DevDocumentsSeeder implements ApplicationRunner {
    private static final Map<String, String> CATEGORY_LABELS =
            Map.ofEntries(
                    Map.entry("PARENT_ID", "Pièces d'identité"),
                    Map.entry("BIRTH_CERTIFICATE", "Actes de naissance"),
                    Map.entry("PHOTO", "Photos"),
                    Map.entry("CERTIFICATE", "Certificats"),
                    Map.entry("CONTRACT", "Contrats"),
                    Map.entry("AUTHORIZATION", "Autorisations"),
                    Map.entry("REPORT_CARD", "Bulletins"),
                    Map.entry("INVOICE", "Factures et reçus"),
                    Map.entry("ATTESTATION", "Attestations"),
                    Map.entry("INTERNAL", "Documents internes"));

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final DocumentManagementService documents;
    private final boolean enabled;
    private final String tenantSlug;

    public DevDocumentsSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            DocumentManagementService documents,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.documents = documents;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?"
                                + " AND normalized_email='admin@schoolz.ma'",
                        tenantId);
        UUID parentId =
                id(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=?"
                                + " AND normalized_email='parent.demo@schoolz.ma'",
                        tenantId);
        seedCategories(tenantId);
        SchoolPrincipal admin =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Administrateur SchoolZ",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        Set.of(
                                "document.read",
                                "document.write",
                                "document.generate",
                                "document.share",
                                "document.sign"));

        seedGenerated(admin, parentId, "SCHOOL_CERTIFICATE", "Attestation de scolarité");
        seedGenerated(admin, parentId, "RECEIPT", "Reçu de paiement");
        seedGenerated(admin, parentId, "INVOICE", "Facture annuelle");
        seedGenerated(admin, parentId, "CERTIFICATE", "Certificat administratif");
        seedGenerated(admin, parentId, "REPORT_CARD", "Bulletin scolaire");
        seedGenerated(admin, parentId, "CONTRACT", "Contrat de scolarisation");
    }

    private void seedCategories(UUID tenantId) {
        CATEGORY_LABELS.forEach(
                (code, label) -> {
                    if (count(
                                    "SELECT count(*) FROM document_category"
                                            + " WHERE tenant_id=? AND code=?",
                                    tenantId,
                                    code)
                            > 0) return;
                    jdbc.update(
                            "INSERT INTO document_category"
                                + " (id,tenant_id,code,label_fr,label_ar,system_category,active,created_at)"
                                + " VALUES (?,?,?,?,NULL,TRUE,TRUE,?)",
                            UUID.randomUUID(),
                            tenantId,
                            code,
                            label,
                            Timestamp.from(clock.instant()));
                });
    }

    private void seedGenerated(
            SchoolPrincipal admin, UUID parentId, String template, String title) {
        if (count(
                        "SELECT count(*) FROM document_version"
                                + " WHERE tenant_id=? AND pdf_template=?",
                        admin.tenantId(),
                        template)
                > 0) return;
        String categoryCode = categoryFor(template);
        UUID categoryId =
                id(
                        "SELECT id FROM document_category WHERE tenant_id=? AND code=?",
                        admin.tenantId(),
                        categoryCode);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Établissement", "École Démo SchoolZ");
        fields.put("Élève", "Yasmine El Mansouri");
        fields.put("Objet", "Exemple contrôlé du modèle " + template);
        fields.put("Note", "Ce PDF de démonstration possède une version et une empreinte SHA-256.");
        DocumentManagementService.DocumentView document =
                documents.generatePdf(
                        admin,
                        new GeneratePdfCommand(
                                categoryId,
                                template,
                                title,
                                "Document généré pour démontrer le DMS SchoolZ.",
                                "Famille El Mansouri",
                                "DEMO-" + template,
                                "PARENT",
                                parentId,
                                "FAMILY",
                                LocalDate.now(clock).plusYears(1),
                                fields),
                        "seed");
        documents.shareWithFamily(
                admin,
                document.id(),
                new ShareCommand(parentId, true, clock.instant().plus(365, ChronoUnit.DAYS)),
                "seed");
    }

    private static String categoryFor(String template) {
        return switch (template) {
            case "SCHOOL_CERTIFICATE" -> "ATTESTATION";
            case "RECEIPT", "INVOICE" -> "INVOICE";
            case "CERTIFICATE" -> "CERTIFICATE";
            case "REPORT_CARD" -> "REPORT_CARD";
            case "CONTRACT" -> "CONTRACT";
            default -> "INTERNAL";
        };
    }

    private UUID id(String sql, Object... args) {
        List<UUID> values = jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
        if (values.isEmpty())
            throw new IllegalStateException("Donnée de démonstration introuvable");
        return values.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
