package ma.schoolz.erp.tasks.application;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
@Order(370)
public class DevTaskSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevTaskSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
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
        UUID teacherId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?"
                                + " AND normalized_email='prof.math@schoolz.ma'",
                        tenantId);
        UUID studentId =
                id(
                        "SELECT id FROM student_profile WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        if (count(
                        "SELECT count(*) FROM internal_task WHERE tenant_id=? AND source='MANUAL'",
                        tenantId)
                > 0) return;
        insert(
                tenantId,
                adminId,
                adminId,
                "Relancer la famille Alaoui",
                "Vérifier la réception du dernier échéancier et convenir d’une date.",
                LocalDate.now(clock),
                "URGENT",
                "FAMILY",
                studentId,
                "IN_PROGRESS");
        insert(
                tenantId,
                adminId,
                adminId,
                "Valider le dossier d’admission",
                "Les pièces ont été contrôlées, la décision finale reste à confirmer.",
                LocalDate.now(clock).plusDays(1),
                "HIGH",
                null,
                null,
                "TODO");
        insert(
                tenantId,
                teacherId,
                adminId,
                "Vérifier le dossier de Yassine",
                "Ajouter l’observation pédagogique manquante avant le conseil.",
                LocalDate.now(clock).plusDays(2),
                "MEDIUM",
                "STUDENT",
                studentId,
                "TODO");
        insert(
                tenantId,
                adminId,
                adminId,
                "Appeler le fournisseur de manuels",
                "Confirmer la date de livraison du bon de commande en cours.",
                LocalDate.now(clock).plusDays(4),
                "LOW",
                null,
                null,
                "TODO");
    }

    private void insert(
            UUID tenantId,
            UUID assignee,
            UUID creator,
            String title,
            String description,
            LocalDate dueDate,
            String priority,
            String entityType,
            UUID entityId,
            String status) {
        jdbc.update(
                "INSERT INTO internal_task"
                        + " (id,tenant_id,title,description,assignee_user_id,created_by_user_id,"
                        + " due_date,priority,status,linked_entity_type,linked_entity_id,source,"
                        + " created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,'MANUAL',?,?)",
                UUID.randomUUID(),
                tenantId,
                title,
                description,
                assignee,
                creator,
                Date.valueOf(dueDate),
                priority,
                status,
                entityType,
                entityId,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()));
    }

    private UUID id(String sql, Object... arguments) {
        List<UUID> values =
                jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), arguments);
        if (values.isEmpty()) throw new IllegalStateException("Donnée de tâche démo introuvable");
        return values.getFirst();
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }
}
