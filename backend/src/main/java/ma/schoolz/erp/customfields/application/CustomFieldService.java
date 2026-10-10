package ma.schoolz.erp.customfields.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomFieldService {
    private static final Set<String> ENTITY_TYPES =
            Set.of("STUDENT", "PERSON", "HOUSEHOLD", "EMPLOYEE", "ADMISSION", "INVOICE");
    private static final Set<String> FIELD_TYPES =
            Set.of("TEXT", "NUMBER", "DATE", "BOOLEAN", "SELECT", "MULTI_SELECT");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public CustomFieldService(
            JdbcTemplate jdbc, ObjectMapper json, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('custom-field.read')")
    public Dashboard dashboard(
            SchoolPrincipal actor, String requestedType, UUID requestedEntityId) {
        String entityType = normalizeEntityType(requestedType);
        List<EntityOption> entities = entities(actor.tenantId(), entityType);
        UUID entityId =
                requestedEntityId == null
                        ? entities.stream().findFirst().map(EntityOption::id).orElse(null)
                        : requestedEntityId;
        if (entityId != null && entities.stream().noneMatch(item -> item.id().equals(entityId))) {
            throw new IllegalArgumentException("L’entité demandée est introuvable dans ce tenant");
        }
        List<DefinitionView> definitions =
                definitions(actor.tenantId(), entityType).stream()
                        .filter(definition -> canRead(actor, definition))
                        .toList();
        List<ValueView> values =
                entityId == null
                        ? List.of()
                        : definitions.stream()
                                .map(definition -> value(actor.tenantId(), entityId, definition))
                                .toList();
        long secured =
                definitions.stream()
                        .filter(
                                definition ->
                                        definition.readPermission() != null
                                                || definition.writePermission() != null)
                        .count();
        long populated = values.stream().filter(item -> item.value() != null).count();
        return new Dashboard(
                clock.instant(),
                entityType,
                entityId,
                new Statistics(definitions.size(), secured, populated, values.size() - populated),
                entities,
                definitions,
                values,
                ENTITY_TYPES.stream().sorted().toList(),
                FIELD_TYPES.stream().sorted().toList(),
                availablePermissions(actor.tenantId()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('custom-field.configure')")
    public DefinitionView create(
            SchoolPrincipal actor, DefinitionCommand command, String ipAddress) {
        ValidatedDefinition valid = validate(actor.tenantId(), command);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO custom_field_definition
                    (id,tenant_id,entity_type,field_key,label,description,field_type,
                     options_json,required,read_permission,write_permission,display_order,active,
                     created_by_user_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                actor.tenantId(),
                valid.entityType(),
                valid.key(),
                valid.label(),
                valid.description(),
                valid.type(),
                writeJson(valid.options()),
                valid.required(),
                valid.readPermission(),
                valid.writePermission(),
                valid.displayOrder(),
                valid.active(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        DefinitionView created = definition(actor.tenantId(), id);
        record(actor, "CUSTOM_FIELD_DEFINITION_CREATED", id, ipAddress, created);
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('custom-field.configure')")
    public DefinitionView update(
            SchoolPrincipal actor, UUID id, DefinitionCommand command, String ipAddress) {
        DefinitionView before = definition(actor.tenantId(), id);
        ValidatedDefinition valid = validate(actor.tenantId(), command);
        if (!before.entityType().equals(valid.entityType())
                || !before.key().equals(valid.key())
                || !before.type().equals(valid.type())) {
            throw new IllegalArgumentException(
                    "Le type d’entité, la clé et le type deviennent immuables après création");
        }
        jdbc.update(
                """
                UPDATE custom_field_definition
                SET label=?,description=?,options_json=?,required=?,read_permission=?,
                    write_permission=?,display_order=?,active=?,updated_at=?
                WHERE tenant_id=? AND id=?
                """,
                valid.label(),
                valid.description(),
                writeJson(valid.options()),
                valid.required(),
                valid.readPermission(),
                valid.writePermission(),
                valid.displayOrder(),
                valid.active(),
                timestamp(clock.instant()),
                actor.tenantId(),
                id);
        DefinitionView updated = definition(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "CUSTOM_FIELD_DEFINITION_UPDATED",
                "custom-field-definition",
                id.toString(),
                ipAddress,
                Map.of("module", "custom-fields"),
                before,
                updated);
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('custom-field.value.write')")
    public ValueView putValue(
            SchoolPrincipal actor,
            UUID definitionId,
            UUID entityId,
            ValueCommand command,
            String ipAddress) {
        DefinitionView definition = definition(actor.tenantId(), definitionId);
        if (!definition.active()) throw new IllegalArgumentException("Ce champ est désactivé");
        if (!canWrite(actor, definition)) {
            throw new IllegalArgumentException("La permission propre à ce champ est requise");
        }
        ensureEntity(actor.tenantId(), definition.entityType(), entityId);
        StoredValue normalized = normalizeValue(definition, command.value());
        List<UUID> existing =
                jdbc.query(
                        "SELECT id FROM custom_field_value WHERE tenant_id=? AND definition_id=?"
                                + " AND entity_id=?",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        actor.tenantId(),
                        definitionId,
                        entityId);
        Instant now = clock.instant();
        UUID valueId;
        if (existing.isEmpty()) {
            valueId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO custom_field_value
                        (id,tenant_id,definition_id,entity_id,text_value,number_value,date_value,
                         boolean_value,json_value,updated_by_user_id,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    valueId,
                    actor.tenantId(),
                    definitionId,
                    entityId,
                    normalized.text(),
                    normalized.number(),
                    normalized.date(),
                    normalized.bool(),
                    normalized.json(),
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } else {
            valueId = existing.getFirst();
            jdbc.update(
                    """
                    UPDATE custom_field_value
                    SET text_value=?,number_value=?,date_value=?,boolean_value=?,json_value=?,
                        updated_by_user_id=?,updated_at=?
                    WHERE tenant_id=? AND id=?
                    """,
                    normalized.text(),
                    normalized.number(),
                    normalized.date(),
                    normalized.bool(),
                    normalized.json(),
                    actor.accountId(),
                    timestamp(now),
                    actor.tenantId(),
                    valueId);
        }
        ValueView updated = value(actor.tenantId(), entityId, definition);
        record(actor, "CUSTOM_FIELD_VALUE_UPDATED", valueId, ipAddress, updated);
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('custom-field.value.write')")
    public void clearValue(
            SchoolPrincipal actor, UUID definitionId, UUID entityId, String ipAddress) {
        DefinitionView definition = definition(actor.tenantId(), definitionId);
        if (definition.required()) {
            throw new IllegalArgumentException("Un champ obligatoire ne peut pas être effacé");
        }
        if (!canWrite(actor, definition)) {
            throw new IllegalArgumentException("La permission propre à ce champ est requise");
        }
        jdbc.update(
                "DELETE FROM custom_field_value WHERE tenant_id=? AND definition_id=? AND"
                        + " entity_id=?",
                actor.tenantId(),
                definitionId,
                entityId);
        record(
                actor,
                "CUSTOM_FIELD_VALUE_CLEARED",
                definitionId,
                ipAddress,
                Map.of("entityId", entityId));
    }

    private List<DefinitionView> definitions(UUID tenantId, String entityType) {
        return jdbc.query(
                "SELECT * FROM custom_field_definition WHERE tenant_id=? AND entity_type=?"
                        + " ORDER BY display_order,label",
                (rs, row) -> mapDefinition(rs),
                tenantId,
                entityType);
    }

    private DefinitionView definition(UUID tenantId, UUID id) {
        List<DefinitionView> values =
                jdbc.query(
                        "SELECT * FROM custom_field_definition WHERE tenant_id=? AND id=?",
                        (rs, row) -> mapDefinition(rs),
                        tenantId,
                        id);
        if (values.isEmpty()) throw new IllegalArgumentException("Champ personnalisé introuvable");
        return values.getFirst();
    }

    private DefinitionView mapDefinition(ResultSet rs) throws SQLException {
        return new DefinitionView(
                rs.getObject("id", UUID.class),
                rs.getString("entity_type"),
                rs.getString("field_key"),
                rs.getString("label"),
                rs.getString("description"),
                rs.getString("field_type"),
                readList(rs.getString("options_json")),
                rs.getBoolean("required"),
                rs.getString("read_permission"),
                rs.getString("write_permission"),
                rs.getInt("display_order"),
                rs.getBoolean("active"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private ValueView value(UUID tenantId, UUID entityId, DefinitionView definition) {
        List<ValueView> values =
                jdbc.query(
                        "SELECT * FROM custom_field_value WHERE tenant_id=? AND definition_id=?"
                                + " AND entity_id=?",
                        (rs, row) -> mapValue(rs, definition),
                        tenantId,
                        definition.id(),
                        entityId);
        return values.isEmpty()
                ? new ValueView(null, definition.id(), entityId, definition.key(), null, null)
                : values.getFirst();
    }

    private ValueView mapValue(ResultSet rs, DefinitionView definition) throws SQLException {
        Object value =
                switch (definition.type()) {
                    case "TEXT", "SELECT" -> rs.getString("text_value");
                    case "NUMBER" -> rs.getBigDecimal("number_value");
                    case "DATE" -> {
                        Date date = rs.getDate("date_value");
                        yield date == null ? null : date.toLocalDate();
                    }
                    case "BOOLEAN" -> rs.getObject("boolean_value", Boolean.class);
                    case "MULTI_SELECT" -> readList(rs.getString("json_value"));
                    default -> null;
                };
        return new ValueView(
                rs.getObject("id", UUID.class),
                definition.id(),
                rs.getObject("entity_id", UUID.class),
                definition.key(),
                value,
                instant(rs, "updated_at"));
    }

    private ValidatedDefinition validate(UUID tenantId, DefinitionCommand command) {
        String entityType = normalizeEntityType(command.entityType());
        String key = command.key() == null ? "" : command.key().strip().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z][a-z0-9_]{1,79}")) {
            throw new IllegalArgumentException(
                    "La clé doit utiliser lettres minuscules, chiffres et _");
        }
        String label = requiredText(command.label(), "Le libellé est obligatoire", 160);
        String description = optionalText(command.description(), 500);
        String type =
                command.type() == null
                        ? ""
                        : command.type().strip().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!FIELD_TYPES.contains(type))
            throw new IllegalArgumentException("Type de champ inconnu");
        List<String> options =
                command.options() == null
                        ? List.of()
                        : command.options().stream()
                                .map(String::strip)
                                .filter(item -> !item.isBlank())
                                .toList();
        if (new LinkedHashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("Les options doivent être uniques");
        }
        if (("SELECT".equals(type) || "MULTI_SELECT".equals(type)) && options.size() < 2) {
            throw new IllegalArgumentException("Un champ de sélection exige au moins deux options");
        }
        if (!("SELECT".equals(type) || "MULTI_SELECT".equals(type)) && !options.isEmpty()) {
            throw new IllegalArgumentException(
                    "Seuls les champs de sélection acceptent des options");
        }
        String readPermission = permission(tenantId, command.readPermission());
        String writePermission = permission(tenantId, command.writePermission());
        int displayOrder = command.displayOrder() == null ? 0 : command.displayOrder();
        if (displayOrder < 0) throw new IllegalArgumentException("L’ordre doit être positif");
        return new ValidatedDefinition(
                entityType,
                key,
                label,
                description,
                type,
                options,
                Boolean.TRUE.equals(command.required()),
                readPermission,
                writePermission,
                displayOrder,
                command.active() == null || command.active());
    }

    private StoredValue normalizeValue(DefinitionView definition, Object raw) {
        if (raw == null) throw new IllegalArgumentException("Une valeur est requise");
        try {
            return switch (definition.type()) {
                case "TEXT" ->
                        new StoredValue(
                                requiredText(String.valueOf(raw), "La valeur est vide", 2000),
                                null,
                                null,
                                null,
                                null);
                case "NUMBER" ->
                        new StoredValue(
                                null, new BigDecimal(String.valueOf(raw)), null, null, null);
                case "DATE" ->
                        new StoredValue(
                                null, null, LocalDate.parse(String.valueOf(raw)), null, null);
                case "BOOLEAN" ->
                        new StoredValue(
                                null,
                                null,
                                null,
                                raw instanceof Boolean value
                                        ? value
                                        : parseBoolean(String.valueOf(raw)),
                                null);
                case "SELECT" -> {
                    String selected = String.valueOf(raw).strip();
                    ensureOptions(definition, List.of(selected));
                    yield new StoredValue(selected, null, null, null, null);
                }
                case "MULTI_SELECT" -> {
                    if (!(raw instanceof Collection<?> collection)) {
                        throw new IllegalArgumentException("Une liste d’options est attendue");
                    }
                    List<String> selected =
                            collection.stream().map(String::valueOf).map(String::strip).toList();
                    if (selected.isEmpty() && definition.required()) {
                        throw new IllegalArgumentException("Au moins une option est requise");
                    }
                    if (new LinkedHashSet<>(selected).size() != selected.size()) {
                        throw new IllegalArgumentException(
                                "Une option ne peut apparaître deux fois");
                    }
                    ensureOptions(definition, selected);
                    yield new StoredValue(null, null, null, null, writeJson(selected));
                }
                default -> throw new IllegalArgumentException("Type de champ inconnu");
            };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Un nombre valide est attendu", exception);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Une date ISO valide est attendue", exception);
        }
    }

    private static void ensureOptions(DefinitionView definition, List<String> selected) {
        List<String> invalid =
                selected.stream().filter(item -> !definition.options().contains(item)).toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "Option non autorisée : " + String.join(", ", invalid));
        }
    }

    private List<EntityOption> entities(UUID tenantId, String type) {
        String sql =
                switch (type) {
                    case "STUDENT" ->
                            "SELECT s.id,p.first_name || ' ' || p.last_name AS label FROM"
                                    + " student_profile s JOIN person p ON p.id=s.person_id AND"
                                    + " p.tenant_id=s.tenant_id WHERE s.tenant_id=? ORDER BY"
                                    + " p.last_name,p.first_name";
                    case "PERSON" ->
                            "SELECT id,first_name || ' ' || last_name AS label FROM person WHERE"
                                    + " tenant_id=? ORDER BY last_name,first_name";
                    case "HOUSEHOLD" ->
                            "SELECT id,name AS label FROM household WHERE tenant_id=? ORDER BY"
                                    + " name";
                    case "EMPLOYEE" ->
                            "SELECT e.id,p.first_name || ' ' || p.last_name AS label FROM"
                                    + " hr_employee e JOIN person p ON p.id=e.person_id AND"
                                    + " p.tenant_id=e.tenant_id WHERE e.tenant_id=? ORDER BY"
                                    + " p.last_name,p.first_name";
                    case "ADMISSION" ->
                            "SELECT id,reference_number AS label FROM admission_application WHERE"
                                    + " tenant_id=? ORDER BY created_at DESC";
                    case "INVOICE" ->
                            "SELECT id,invoice_number AS label FROM billing_invoice WHERE"
                                    + " tenant_id=? ORDER BY created_at DESC";
                    default -> throw new IllegalArgumentException("Type d’entité inconnu");
                };
        return jdbc.query(
                sql,
                (rs, row) ->
                        new EntityOption(rs.getObject("id", UUID.class), rs.getString("label")),
                tenantId);
    }

    private void ensureEntity(UUID tenantId, String type, UUID id) {
        if (entities(tenantId, type).stream().noneMatch(item -> item.id().equals(id))) {
            throw new IllegalArgumentException("L’entité demandée est introuvable dans ce tenant");
        }
    }

    private static boolean canRead(SchoolPrincipal actor, DefinitionView definition) {
        return definition.readPermission() == null
                || actor.permissions().contains(definition.readPermission());
    }

    private static boolean canWrite(SchoolPrincipal actor, DefinitionView definition) {
        return definition.writePermission() == null
                || actor.permissions().contains(definition.writePermission());
    }

    private static String normalizeEntityType(String value) {
        String type = value == null ? "STUDENT" : value.strip().toUpperCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(type))
            throw new IllegalArgumentException("Type d’entité inconnu");
        return type;
    }

    private String permission(UUID tenantId, String value) {
        if (value == null || value.isBlank()) return null;
        String permission = value.strip();
        if (!availablePermissions(tenantId).contains(permission)) {
            throw new IllegalArgumentException("Permission inconnue : " + permission);
        }
        return permission;
    }

    private List<String> availablePermissions(UUID tenantId) {
        return jdbc.queryForList(
                "SELECT DISTINCT rp.permission_code FROM identity_role_permission rp JOIN"
                        + " identity_role r ON r.id=rp.role_id WHERE r.tenant_id=? ORDER BY"
                        + " rp.permission_code",
                String.class,
                tenantId);
    }

    private static String requiredText(String value, String message, int maximum) {
        String clean = value == null ? "" : value.strip();
        if (clean.isBlank()) throw new IllegalArgumentException(message);
        if (clean.length() > maximum) throw new IllegalArgumentException("Valeur trop longue");
        return clean;
    }

    private static String optionalText(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        return requiredText(value, "Valeur vide", maximum);
    }

    private static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Une valeur booléenne est attendue");
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Valeur JSON invalide", exception);
        }
    }

    private List<String> readList(String value) {
        if (value == null) return List.of();
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private void record(
            SchoolPrincipal actor, String action, UUID id, String ipAddress, Object after) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                "custom-field",
                id.toString(),
                ipAddress,
                Map.of("module", "custom-fields"),
                null,
                after);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record Dashboard(
            Instant generatedAt,
            String entityType,
            UUID selectedEntityId,
            Statistics statistics,
            List<EntityOption> entities,
            List<DefinitionView> definitions,
            List<ValueView> values,
            List<String> supportedEntityTypes,
            List<String> supportedFieldTypes,
            List<String> availablePermissions) {}

    public record Statistics(
            long definitionCount, long securedCount, long populatedCount, long emptyCount) {}

    public record EntityOption(UUID id, String label) {}

    public record DefinitionView(
            UUID id,
            String entityType,
            String key,
            String label,
            String description,
            String type,
            List<String> options,
            boolean required,
            String readPermission,
            String writePermission,
            int displayOrder,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}

    public record ValueView(
            UUID id,
            UUID definitionId,
            UUID entityId,
            String key,
            Object value,
            Instant updatedAt) {}

    public record DefinitionCommand(
            String entityType,
            String key,
            String label,
            String description,
            String type,
            List<String> options,
            Boolean required,
            String readPermission,
            String writePermission,
            Integer displayOrder,
            Boolean active) {}

    public record ValueCommand(Object value) {}

    private record ValidatedDefinition(
            String entityType,
            String key,
            String label,
            String description,
            String type,
            List<String> options,
            boolean required,
            String readPermission,
            String writePermission,
            int displayOrder,
            boolean active) {}

    private record StoredValue(
            String text, BigDecimal number, LocalDate date, Boolean bool, String json) {}
}
