package ma.schoolz.erp.documents.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.documents.application.DocumentSignatureService.AcceptCommand;
import ma.schoolz.erp.documents.application.DocumentSignatureService.CreateWorkflowCommand;
import ma.schoolz.erp.documents.application.DocumentSignatureService.SignCommand;
import ma.schoolz.erp.documents.application.DocumentSignatureService.WorkflowView;
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
@Order(160)
public class DevDocumentSignaturesSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DocumentSignatureService signatures;
    private final boolean enabled;
    private final String tenantSlug;

    public DevDocumentSignaturesSeeder(
            JdbcTemplate jdbc,
            DocumentSignatureService signatures,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.signatures = signatures;
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
        SchoolPrincipal admin =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Administrateur SchoolZ",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        Set.of("document.read", "document.write", "document.sign"));
        PortalIdentity parent =
                new PortalIdentity(
                        tenantId,
                        parentId,
                        "parent.demo@schoolz.ma",
                        "Famille El Mansouri",
                        "fr-MA");

        seedSent(admin, parentId, documentByTemplate(tenantId, "SCHOOL_CERTIFICATE"));
        seedSigned(admin, parent, documentByTemplate(tenantId, "CONTRACT"));
    }

    private void seedSent(SchoolPrincipal admin, UUID parentId, UUID documentId) {
        if (workflowCount(admin.tenantId(), documentId) > 0) return;
        WorkflowView workflow =
                signatures.create(admin, new CreateWorkflowCommand(documentId, parentId), "seed");
        signatures.send(admin, workflow.id(), "seed");
    }

    private void seedSigned(SchoolPrincipal admin, PortalIdentity parent, UUID documentId) {
        if (workflowCount(admin.tenantId(), documentId) > 0) return;
        WorkflowView workflow =
                signatures.create(
                        admin, new CreateWorkflowCommand(documentId, parent.accountId()), "seed");
        signatures.send(admin, workflow.id(), "seed");
        signatures.view(parent, workflow.id(), "127.0.0.1");
        signatures.accept(parent, workflow.id(), new AcceptCommand(true), "127.0.0.1");
        signatures.sign(
                parent,
                workflow.id(),
                new SignCommand("Amina El Mansouri", "local-simple", true),
                "127.0.0.1");
    }

    private UUID documentByTemplate(UUID tenantId, String template) {
        return id(
                "SELECT document_id FROM document_version WHERE tenant_id=? AND pdf_template=?"
                        + " ORDER BY created_at FETCH FIRST 1 ROW ONLY",
                tenantId,
                template);
    }

    private int workflowCount(UUID tenantId, UUID documentId) {
        Integer value =
                jdbc.queryForObject(
                        "SELECT count(*) FROM document_signature_workflow"
                                + " WHERE tenant_id=? AND document_id=?",
                        Integer.class,
                        tenantId,
                        documentId);
        return value == null ? 0 : value;
    }

    private UUID id(String sql, Object... args) {
        List<UUID> values = jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
        if (values.isEmpty())
            throw new IllegalStateException("Donnée de démonstration introuvable");
        return values.getFirst();
    }
}
