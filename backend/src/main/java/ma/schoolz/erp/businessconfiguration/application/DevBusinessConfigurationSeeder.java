package ma.schoolz.erp.businessconfiguration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@Order(320)
public class DevBusinessConfigurationSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevBusinessConfigurationSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            ObjectMapper json,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.json = json;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled || tenantSlug.isBlank()) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .map(TenantDirectory.TenantSummary::id)
                        .orElse(null);
        if (tenantId == null) return;
        UUID actorId =
                firstId(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        if (actorId == null) return;

        List<Seed> seeds =
                List.of(
                        new Seed(
                                "ACADEMIC",
                                "academic.periods",
                                "Périodes pédagogiques",
                                "Découpage de l’année utilisé pour les résultats et bulletins.",
                                "JSON",
                                List.of(
                                        Map.of("code", "T1", "label", "Trimestre 1"),
                                        Map.of("code", "T2", "label", "Trimestre 2"),
                                        Map.of("code", "T3", "label", "Trimestre 3")),
                                List.of(),
                                Map.of(),
                                "organization",
                                10),
                        new Seed(
                                "ACADEMIC",
                                "academic.subject_coefficients",
                                "Coefficients par matière",
                                "Coefficients de référence, ajustables ensuite par niveau.",
                                "JSON",
                                Map.of("MATHEMATICS", 4, "FRENCH", 3, "ARABIC", 3, "SCIENCE", 2),
                                List.of(),
                                Map.of(),
                                "grading",
                                20),
                        new Seed(
                                "CALENDAR",
                                "calendar.school_days",
                                "Jours scolaires",
                                "Jours habituels d’ouverture de l’établissement.",
                                "MULTI_SELECT",
                                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                                List.of(
                                        "MONDAY",
                                        "TUESDAY",
                                        "WEDNESDAY",
                                        "THURSDAY",
                                        "FRIDAY",
                                        "SATURDAY",
                                        "SUNDAY"),
                                Map.of("minItems", 1, "maxItems", 7),
                                "timetable",
                                10),
                        new Seed(
                                "SCHEDULE",
                                "schedule.school_hours",
                                "Amplitude quotidienne",
                                "Heures de début, fin et durée de pause utilisées comme repères.",
                                "JSON",
                                Map.of("opensAt", "08:00", "closesAt", "17:00", "breakMinutes", 60),
                                List.of(),
                                Map.of(),
                                "timetable",
                                10),
                        new Seed(
                                "SCHEDULE",
                                "schedule.lesson_minutes",
                                "Durée d’une séance",
                                "Durée de référence avant les exceptions de planning.",
                                "NUMBER",
                                60,
                                List.of(),
                                Map.of("min", 20, "max", 180),
                                "timetable",
                                20),
                        new Seed(
                                "FINANCE",
                                "finance.fee_catalog",
                                "Frais proposés",
                                "Catalogue de référence ; toute facturation reste une opération"
                                        + " explicite.",
                                "JSON",
                                List.of(
                                        Map.of(
                                                "code",
                                                "TUITION",
                                                "label",
                                                "Scolarité",
                                                "amount",
                                                35000),
                                        Map.of(
                                                "code",
                                                "REGISTRATION",
                                                "label",
                                                "Inscription",
                                                "amount",
                                                4500)),
                                List.of(),
                                Map.of(),
                                "billing",
                                10),
                        new Seed(
                                "FINANCE",
                                "finance.payment_plans",
                                "Plans de paiement autorisés",
                                "Rythmes proposés lors de la création d’un échéancier.",
                                "MULTI_SELECT",
                                List.of("MONTHLY", "QUARTERLY", "ANNUAL", "CUSTOM"),
                                List.of("MONTHLY", "QUARTERLY", "ANNUAL", "CUSTOM"),
                                Map.of("minItems", 1),
                                "billing",
                                20),
                        new Seed(
                                "ATTENDANCE",
                                "attendance.absence_reasons",
                                "Motifs d’absence",
                                "Liste proposée lors de la justification d’une absence.",
                                "MULTI_SELECT",
                                List.of(
                                        "MEDICAL",
                                        "FAMILY",
                                        "TRANSPORT",
                                        "ADMINISTRATIVE",
                                        "OTHER"),
                                List.of(
                                        "MEDICAL",
                                        "FAMILY",
                                        "TRANSPORT",
                                        "ADMINISTRATIVE",
                                        "OTHER"),
                                Map.of("minItems", 1),
                                "attendance",
                                10),
                        new Seed(
                                "DISCIPLINE",
                                "discipline.sanctions",
                                "Échelle des réponses éducatives",
                                "Mesures disponibles ; aucune sanction n’est appliquée"
                                        + " automatiquement.",
                                "JSON",
                                List.of(
                                        Map.of("code", "REMINDER", "label", "Rappel au règlement"),
                                        Map.of("code", "MEETING", "label", "Entretien éducatif"),
                                        Map.of("code", "WARNING", "label", "Avertissement écrit")),
                                List.of(),
                                Map.of(),
                                "attendance",
                                10),
                        new Seed(
                                "DOCUMENTS",
                                "documents.enabled_templates",
                                "Templates documentaires",
                                "Modèles disponibles pour la génération contrôlée.",
                                "MULTI_SELECT",
                                List.of("ENROLLMENT_CERTIFICATE", "PAYMENT_RECEIPT", "REPORT_CARD"),
                                List.of(
                                        "ENROLLMENT_CERTIFICATE",
                                        "PAYMENT_RECEIPT",
                                        "REPORT_CARD",
                                        "ATTENDANCE_CERTIFICATE"),
                                Map.of("minItems", 1),
                                "documents",
                                10),
                        new Seed(
                                "NOTIFICATIONS",
                                "notifications.channels",
                                "Canaux autorisés",
                                "Canaux disponibles ; leur activation n’installe aucun fournisseur"
                                        + " externe.",
                                "MULTI_SELECT",
                                List.of("IN_APP", "EMAIL"),
                                List.of("IN_APP", "EMAIL", "SMS", "PUSH", "WHATSAPP"),
                                Map.of("minItems", 1),
                                "communications",
                                10),
                        new Seed(
                                "GRADING",
                                "grading.scales",
                                "Échelles de notes",
                                "Échelles numériques proposées selon le cycle.",
                                "JSON",
                                List.of(
                                        Map.of("cycle", "PRIMARY", "maximum", 20),
                                        Map.of("cycle", "MIDDLE_SCHOOL", "maximum", 20)),
                                List.of(),
                                Map.of(),
                                "grading",
                                10),
                        new Seed(
                                "GRADING",
                                "grading.rules",
                                "Règles de calcul",
                                "Repères globaux ; les politiques de niveau restent prioritaires.",
                                "JSON",
                                Map.of(
                                        "roundingScale",
                                        2,
                                        "rankingEnabled",
                                        false,
                                        "missingGradePolicy",
                                        "EXCLUDED"),
                                List.of(),
                                Map.of(),
                                "grading",
                                20),
                        new Seed(
                                "SECURITY",
                                "security.delegable_permissions",
                                "Permissions délégables",
                                "Permissions que la direction peut déléguer sans rôle"
                                        + " propriétaire.",
                                "MULTI_SELECT",
                                List.of(
                                        "student.read",
                                        "attendance.read",
                                        "grade.read",
                                        "document.read"),
                                List.of(
                                        "student.read",
                                        "attendance.read",
                                        "grade.read",
                                        "document.read",
                                        "invoice.read",
                                        "communication.read"),
                                Map.of("minItems", 1),
                                "identity",
                                10));
        int order = 0;
        for (Seed seed : seeds) upsert(tenantId, actorId, seed, order++);
    }

    private void upsert(UUID tenantId, UUID actorId, Seed seed, int fallbackOrder) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM business_configuration_item WHERE tenant_id=? AND"
                                + " config_key=?",
                        Integer.class,
                        tenantId,
                        seed.key());
        if (count != null && count > 0) return;
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String value = write(seed.value());
        jdbc.update(
                """
                INSERT INTO business_configuration_item
                    (id,tenant_id,category,config_key,label,description,value_type,
                     current_value_json,default_value_json,options_json,constraints_json,
                     source_module,display_order,editable,version,updated_by_user_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,1,?,?,?)
                """,
                id,
                tenantId,
                seed.category(),
                seed.key(),
                seed.label(),
                seed.description(),
                seed.type(),
                value,
                value,
                write(seed.options()),
                write(seed.constraints()),
                seed.sourceModule(),
                seed.displayOrder() == null ? fallbackOrder * 10 : seed.displayOrder(),
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO business_configuration_revision
                    (id,tenant_id,item_id,version,previous_value_json,next_value_json,reason,
                     changed_by_user_id,changed_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                id,
                1,
                value,
                value,
                "Configuration de référence SchoolZ",
                actorId,
                Timestamp.from(now));
    }

    private UUID firstId(String sql, Object... arguments) {
        List<UUID> values = jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), arguments);
        return values.isEmpty() ? null : values.getFirst();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Seed(
            String category,
            String key,
            String label,
            String description,
            String type,
            Object value,
            List<String> options,
            Map<String, Object> constraints,
            String sourceModule,
            Integer displayOrder) {}
}
