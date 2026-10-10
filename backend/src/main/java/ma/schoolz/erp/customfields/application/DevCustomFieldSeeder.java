package ma.schoolz.erp.customfields.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
@Order(310)
public class DevCustomFieldSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevCustomFieldSeeder(
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
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? ORDER BY created_at"
                                + " FETCH FIRST 1 ROW ONLY",
                        tenantId);
        UUID studentId =
                id(
                        "SELECT s.id FROM student_profile s JOIN student_identifier i ON"
                                + " i.student_id=s.id AND i.tenant_id=s.tenant_id WHERE"
                                + " s.tenant_id=? AND i.identifier_value='SZ-2026-0001'",
                        tenantId);
        if (actorId == null || studentId == null) return;

        UUID blood =
                definition(
                        tenantId,
                        actorId,
                        "blood_group",
                        "Groupe sanguin déclaré",
                        "SELECT",
                        List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"),
                        "health.sensitive.read",
                        "health.write",
                        10);
        UUID registration =
                definition(
                        tenantId,
                        actorId,
                        "internal_registration_number",
                        "Matricule interne",
                        "TEXT",
                        List.of(),
                        null,
                        null,
                        20);
        UUID bus =
                definition(
                        tenantId,
                        actorId,
                        "bus_code",
                        "Code transport",
                        "TEXT",
                        List.of(),
                        "transport.read",
                        "transport.manage",
                        30);
        UUID reference =
                definition(
                        tenantId,
                        actorId,
                        "custom_reference",
                        "Référence école",
                        "TEXT",
                        List.of(),
                        null,
                        null,
                        40);

        value(tenantId, actorId, blood, studentId, "O+");
        value(tenantId, actorId, registration, studentId, "INS-2026-0001");
        value(tenantId, actorId, bus, studentId, "BUS-CASA-A");
        value(tenantId, actorId, reference, studentId, "REF-YA-26");
    }

    private UUID definition(
            UUID tenantId,
            UUID actorId,
            String key,
            String label,
            String type,
            List<String> options,
            String readPermission,
            String writePermission,
            int order) {
        UUID existing =
                id(
                        "SELECT id FROM custom_field_definition WHERE tenant_id=? AND"
                                + " entity_type='STUDENT' AND field_key=?",
                        tenantId,
                        key);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO custom_field_definition
                    (id,tenant_id,entity_type,field_key,label,description,field_type,options_json,
                     required,read_permission,write_permission,display_order,active,
                     created_by_user_id,created_at,updated_at)
                VALUES (?,?,'STUDENT',?,?,?,?,?,FALSE,?,?,?,?,?,?,?)
                """,
                id,
                tenantId,
                key,
                label,
                "Champ de démonstration configurable par l’établissement.",
                type,
                write(options),
                readPermission,
                writePermission,
                order,
                true,
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    private void value(
            UUID tenantId, UUID actorId, UUID definitionId, UUID entityId, String textValue) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM custom_field_value WHERE tenant_id=? AND"
                                + " definition_id=? AND entity_id=?",
                        Integer.class,
                        tenantId,
                        definitionId,
                        entityId);
        if (count != null && count > 0) return;
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO custom_field_value
                    (id,tenant_id,definition_id,entity_id,text_value,updated_by_user_id,
                     created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                definitionId,
                entityId,
                textValue,
                actorId,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private UUID id(String sql, Object... arguments) {
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
}
