package ma.schoolz.erp.businessconfiguration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
public class BusinessConfigurationService {
    private static final Set<String> CATEGORIES =
            Set.of(
                    "ACADEMIC",
                    "CALENDAR",
                    "SCHEDULE",
                    "FINANCE",
                    "ATTENDANCE",
                    "DISCIPLINE",
                    "DOCUMENTS",
                    "NOTIFICATIONS",
                    "GRADING",
                    "SECURITY");
    private static final Set<String> TYPES =
            Set.of(
                    "TEXT",
                    "NUMBER",
                    "BOOLEAN",
                    "TIME",
                    "SELECT",
                    "MULTI_SELECT",
                    "JSON",
                    "MONEY",
                    "PERCENT");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public BusinessConfigurationService(
            JdbcTemplate jdbc, ObjectMapper json, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('business-configuration.read')")
    public Dashboard dashboard(SchoolPrincipal actor, String requestedCategory) {
        String category = normalizeOptionalCategory(requestedCategory);
        List<ItemView> items = items(actor.tenantId(), category);
        Map<String, List<ItemView>> grouped = new LinkedHashMap<>();
        for (ItemView item : items) {
            grouped.computeIfAbsent(item.category(), ignored -> new ArrayList<>()).add(item);
        }
        List<CategoryView> categories =
                grouped.entrySet().stream()
                        .map(entry -> new CategoryView(entry.getKey(), entry.getValue()))
                        .toList();
        long customized =
                items.stream().filter(item -> !item.value().equals(item.defaultValue())).count();
        long guarded = items.stream().filter(item -> !item.editable()).count();
        return new Dashboard(
                clock.instant(),
                category,
                new Statistics(items.size(), categories.size(), customized, guarded),
                categories,
                recentRevisions(actor.tenantId(), category));
    }

    @Transactional
    @PreAuthorize("hasAuthority('business-configuration.manage')")
    public ItemView update(
            SchoolPrincipal actor, UUID itemId, SettingCommand command, String ipAddress) {
        if (command == null || command.value() == null) {
            throw new IllegalArgumentException("Une valeur de configuration est requise");
        }
        ItemView before = item(actor.tenantId(), itemId);
        if (!before.editable()) {
            throw new IllegalArgumentException("Ce réglage est piloté par son module d’origine");
        }
        int expected =
                command.expectedVersion() == null ? before.version() : command.expectedVersion();
        if (expected != before.version()) {
            throw new BusinessConfigurationConflictException(
                    "Cette configuration a été modifiée. Rechargez la page avant de réessayer.");
        }
        String reason = required(command.reason(), "Le motif de modification", 500);
        JsonNode normalized = normalize(before, command.value());
        String serialized = write(normalized);
        if (serialized.equals(write(before.value()))) return before;
        Instant now = clock.instant();
        int nextVersion = before.version() + 1;
        int changed =
                jdbc.update(
                        """
                        UPDATE business_configuration_item
                        SET current_value_json=?,version=?,updated_by_user_id=?,updated_at=?
                        WHERE tenant_id=? AND id=? AND version=?
                        """,
                        serialized,
                        nextVersion,
                        actor.accountId(),
                        Timestamp.from(now),
                        actor.tenantId(),
                        itemId,
                        expected);
        if (changed != 1) {
            throw new BusinessConfigurationConflictException(
                    "Cette configuration a été modifiée pendant votre saisie");
        }
        jdbc.update(
                """
                INSERT INTO business_configuration_revision
                    (id,tenant_id,item_id,version,previous_value_json,next_value_json,reason,
                     changed_by_user_id,changed_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                actor.tenantId(),
                itemId,
                nextVersion,
                write(before.value()),
                serialized,
                reason,
                actor.accountId(),
                Timestamp.from(now));
        ItemView after = item(actor.tenantId(), itemId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "BUSINESS_CONFIGURATION_UPDATED",
                "business-configuration",
                itemId.toString(),
                ipAddress,
                Map.of("configKey", before.key(), "version", nextVersion, "reason", reason),
                before,
                after);
        return after;
    }

    @Transactional
    @PreAuthorize("hasAuthority('business-configuration.manage')")
    public ItemView reset(
            SchoolPrincipal actor, UUID itemId, ResetCommand command, String ipAddress) {
        ItemView current = item(actor.tenantId(), itemId);
        String reason = command == null ? null : command.reason();
        Integer expected = command == null ? null : command.expectedVersion();
        return update(
                actor,
                itemId,
                new SettingCommand(
                        current.defaultValue(),
                        expected,
                        reason == null || reason.isBlank()
                                ? "Retour à la valeur de référence"
                                : reason),
                ipAddress);
    }

    private JsonNode normalize(ItemView item, Object rawValue) {
        JsonNode value = json.valueToTree(rawValue);
        return switch (item.valueType()) {
            case "TEXT" -> text(value, 2000);
            case "NUMBER" -> decimal(value, item.constraints(), false, false);
            case "MONEY" -> decimal(value, item.constraints(), true, false);
            case "PERCENT" -> decimal(value, item.constraints(), false, true);
            case "BOOLEAN" -> bool(value);
            case "TIME" -> time(value);
            case "SELECT" -> selection(value, item.options());
            case "MULTI_SELECT" -> multiple(value, item.options(), item.constraints());
            case "JSON" -> structured(value);
            default -> throw new IllegalArgumentException("Type de configuration inconnu");
        };
    }

    private JsonNode text(JsonNode value, int maximum) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Une valeur texte non vide est requise");
        }
        String result = value.textValue().trim();
        if (result.length() > maximum)
            throw new IllegalArgumentException("Valeur texte trop longue");
        return json.getNodeFactory().textNode(result);
    }

    private JsonNode decimal(
            JsonNode value, Map<String, Object> constraints, boolean money, boolean percent) {
        BigDecimal number;
        try {
            number = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Une valeur numérique valide est requise");
        }
        BigDecimal min = configuredDecimal(constraints.get("min"));
        BigDecimal max = configuredDecimal(constraints.get("max"));
        if (percent) {
            min = min == null ? BigDecimal.ZERO : min;
            max = max == null ? BigDecimal.valueOf(100) : max;
        }
        if (money && number.scale() > 2) {
            throw new IllegalArgumentException("Un montant accepte au maximum deux décimales");
        }
        if (min != null && number.compareTo(min) < 0) {
            throw new IllegalArgumentException("La valeur est inférieure au minimum autorisé");
        }
        if (max != null && number.compareTo(max) > 0) {
            throw new IllegalArgumentException("La valeur dépasse le maximum autorisé");
        }
        return DecimalNode.valueOf(number.stripTrailingZeros());
    }

    private JsonNode bool(JsonNode value) {
        if (!value.isBoolean())
            throw new IllegalArgumentException("Une valeur oui/non est requise");
        return value;
    }

    private JsonNode time(JsonNode value) {
        if (!value.isTextual()) throw new IllegalArgumentException("Une heure HH:mm est requise");
        try {
            return json.getNodeFactory().textNode(LocalTime.parse(value.textValue()).toString());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("L’heure doit respecter le format HH:mm");
        }
    }

    private JsonNode selection(JsonNode value, List<String> options) {
        JsonNode normalized = text(value, 200);
        if (!options.contains(normalized.textValue())) {
            throw new IllegalArgumentException(
                    "Cette option ne fait pas partie des choix autorisés");
        }
        return normalized;
    }

    private JsonNode multiple(
            JsonNode value, List<String> options, Map<String, Object> constraints) {
        if (!value.isArray()) throw new IllegalArgumentException("Une liste de choix est requise");
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        value.forEach(
                node -> {
                    if (!node.isTextual() || !options.contains(node.textValue())) {
                        throw new IllegalArgumentException(
                                "La liste contient une option non autorisée");
                    }
                    selected.add(node.textValue());
                });
        int minimum = configuredInteger(constraints.get("minItems"), 0);
        int maximum = configuredInteger(constraints.get("maxItems"), options.size());
        if (selected.size() < minimum || selected.size() > maximum) {
            throw new IllegalArgumentException(
                    "Le nombre de choix ne respecte pas la configuration");
        }
        return json.valueToTree(selected);
    }

    private JsonNode structured(JsonNode value) {
        if (!value.isObject() && !value.isArray()) {
            throw new IllegalArgumentException("Une structure JSON objet ou liste est requise");
        }
        if (write(value).length() > 12000) {
            throw new IllegalArgumentException("La configuration structurée est trop volumineuse");
        }
        return value;
    }

    private List<ItemView> items(UUID tenantId, String category) {
        String sql =
                """
                SELECT * FROM business_configuration_item
                WHERE tenant_id=?
                """
                        + (category == null ? "" : " AND category=?")
                        + " ORDER BY category,display_order,config_key";
        return category == null
                ? jdbc.query(sql, this::mapItem, tenantId)
                : jdbc.query(sql, this::mapItem, tenantId, category);
    }

    private ItemView item(UUID tenantId, UUID id) {
        List<ItemView> rows =
                jdbc.query(
                        "SELECT * FROM business_configuration_item WHERE tenant_id=? AND id=?",
                        this::mapItem,
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Configuration introuvable");
        return rows.getFirst();
    }

    private ItemView mapItem(ResultSet rs, int row) throws SQLException {
        return new ItemView(
                rs.getObject("id", UUID.class),
                rs.getString("category"),
                rs.getString("config_key"),
                rs.getString("label"),
                rs.getString("description"),
                rs.getString("value_type"),
                readValue(rs.getString("current_value_json")),
                readValue(rs.getString("default_value_json")),
                readStrings(rs.getString("options_json")),
                readMap(rs.getString("constraints_json")),
                rs.getString("source_module"),
                rs.getBoolean("editable"),
                rs.getInt("version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private List<RevisionView> recentRevisions(UUID tenantId, String category) {
        String sql =
                """
                SELECT r.id,r.item_id,i.config_key,i.label,r.version,r.previous_value_json,
                       r.next_value_json,r.reason,r.changed_at
                FROM business_configuration_revision r
                JOIN business_configuration_item i ON i.id=r.item_id AND i.tenant_id=r.tenant_id
                WHERE r.tenant_id=?
                """
                        + (category == null ? "" : " AND i.category=?")
                        + " ORDER BY r.changed_at DESC FETCH FIRST 30 ROWS ONLY";
        Object[] args =
                category == null ? new Object[] {tenantId} : new Object[] {tenantId, category};
        return jdbc.query(
                sql,
                (rs, row) ->
                        new RevisionView(
                                rs.getObject("id", UUID.class),
                                rs.getObject("item_id", UUID.class),
                                rs.getString("config_key"),
                                rs.getString("label"),
                                rs.getInt("version"),
                                readValue(rs.getString("previous_value_json")),
                                readValue(rs.getString("next_value_json")),
                                rs.getString("reason"),
                                rs.getTimestamp("changed_at").toInstant()),
                args);
    }

    private String normalizeOptionalCategory(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized))
            throw new IllegalArgumentException("Catégorie inconnue");
        return normalized;
    }

    private Object readValue(String value) {
        try {
            return json.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Configuration persistée invalide", exception);
        }
    }

    private List<String> readStrings(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Options persistées invalides", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Contraintes persistées invalides", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Configuration JSON invalide", exception);
        }
    }

    private String required(String value, String label, int maximum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " est requis");
        String result = value.trim();
        if (result.length() > maximum) throw new IllegalArgumentException(label + " est trop long");
        return result;
    }

    private BigDecimal configuredDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private int configuredInteger(Object value, int fallback) {
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    public record SettingCommand(Object value, Integer expectedVersion, String reason) {}

    public record ResetCommand(Integer expectedVersion, String reason) {}

    public record Dashboard(
            Instant generatedAt,
            String selectedCategory,
            Statistics statistics,
            List<CategoryView> categories,
            List<RevisionView> recentRevisions) {}

    public record Statistics(
            int itemCount, int categoryCount, long customizedCount, long guardedCount) {}

    public record CategoryView(String key, List<ItemView> items) {}

    public record ItemView(
            UUID id,
            String category,
            String key,
            String label,
            String description,
            String valueType,
            Object value,
            Object defaultValue,
            List<String> options,
            Map<String, Object> constraints,
            String sourceModule,
            boolean editable,
            int version,
            Instant updatedAt) {}

    public record RevisionView(
            UUID id,
            UUID itemId,
            String key,
            String label,
            int version,
            Object previousValue,
            Object nextValue,
            String reason,
            Instant changedAt) {}
}
