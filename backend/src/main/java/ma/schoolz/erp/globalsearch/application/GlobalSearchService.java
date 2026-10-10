package ma.schoolz.erp.globalsearch.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalSearchService {
    private static final Set<String> TYPES =
            Set.of("STUDENT", "PARENT", "TEACHER", "INVOICE", "CLASSROOM", "DOCUMENT");
    private static final Map<String, String> TYPE_PERMISSION =
            Map.of(
                    "STUDENT", "student.read",
                    "PARENT", "person.read",
                    "TEACHER", "hr.read",
                    "INVOICE", "invoice.read",
                    "CLASSROOM", "organization.read",
                    "DOCUMENT", "document.read");
    private static final Map<String, String> ARABIC_ALIASES =
            Map.of(
                    "STUDENT", "تلميذ تلميذة طالب طالبة",
                    "PARENT", "ولي أب أم أسرة",
                    "TEACHER", "أستاذ أستاذة مدرس مدرسة",
                    "INVOICE", "فاتورة أداء مالي",
                    "CLASSROOM", "قسم فصل حجرة",
                    "DOCUMENT", "وثيقة ملف شهادة");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern ARABIC_MARKS = Pattern.compile("[\\u064B-\\u065F\\u0670\\u0640]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public GlobalSearchService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('global-search.read')")
    public SearchResponse search(
            SchoolPrincipal actor, String query, String requestedType, int page, int size) {
        long started = System.nanoTime();
        String cleaned = requiredQuery(query);
        String normalized = normalize(cleaned);
        if (page < 0 || size < 1 || size > 50)
            throw new IllegalArgumentException("Pagination invalide");
        List<String> allowedTypes =
                TYPES.stream()
                        .filter(
                                type ->
                                        actor.permissions().contains(TYPE_PERMISSION.get(type))
                                                || actor.roles().contains("SUPER_ADMIN")
                                                || actor.roles().contains("SCHOOL_DIRECTOR"))
                        .sorted()
                        .toList();
        String type = optionalType(requestedType);
        if (type != null && !allowedTypes.contains(type))
            throw new IllegalArgumentException("Type de résultat non autorisé");
        List<String> effectiveTypes = type == null ? allowedTypes : List.of(type);
        if (effectiveTypes.isEmpty()) {
            return response(
                    cleaned, normalized, page, size, false, started, allowedTypes, List.of());
        }

        List<String> terms = List.of(normalized.split(" "));
        String placeholders = String.join(",", effectiveTypes.stream().map(value -> "?").toList());
        StringBuilder sql =
                new StringBuilder(
                        "SELECT * FROM global_search_document WHERE tenant_id=? AND source_type IN"
                                + " ("
                                + placeholders
                                + ")");
        List<Object> params = new ArrayList<>();
        params.add(actor.tenantId());
        params.addAll(effectiveTypes);
        for (String term : terms) {
            sql.append(" AND normalized_text LIKE ?");
            params.add("%" + term + "%");
        }
        sql.append(
                " ORDER BY CASE WHEN normalized_text=? THEN 0 WHEN normalized_text LIKE ? THEN 1"
                        + " ELSE 2 END, display_name,source_type LIMIT ? OFFSET ?");
        params.add(normalized);
        params.add(normalized + "%");
        params.add(size + 1);
        params.add(page * size);
        List<SearchItem> rows = jdbc.query(sql.toString(), this::mapItem, params.toArray());
        boolean hasNext = rows.size() > size;
        List<SearchItem> items = hasNext ? rows.subList(0, size) : rows;
        return response(cleaned, normalized, page, size, hasNext, started, allowedTypes, items);
    }

    @Transactional
    @PreAuthorize("hasAuthority('global-search.manage')")
    public RefreshResult refresh(SchoolPrincipal actor) {
        return refreshTenant(actor.tenantId());
    }

    @Transactional
    public RefreshResult refreshTenant(UUID tenantId) {
        Instant now = clock.instant();
        jdbc.update("DELETE FROM global_search_document WHERE tenant_id=?", tenantId);
        List<Source> sources = new ArrayList<>();
        sources.addAll(students(tenantId));
        sources.addAll(parents(tenantId));
        sources.addAll(teachers(tenantId));
        sources.addAll(invoices(tenantId));
        sources.addAll(classrooms(tenantId));
        sources.addAll(documents(tenantId));
        for (Source source : sources) {
            String searchable =
                    String.join(
                            " ",
                            source.displayName(),
                            value(source.secondaryText()),
                            value(source.extraSearch()),
                            ARABIC_ALIASES.get(source.type()));
            jdbc.update(
                    """
                    INSERT INTO global_search_document
                        (id,tenant_id,source_type,source_id,display_name,secondary_text,route_path,
                         search_text,normalized_text,metadata_json,indexed_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    documentId(tenantId, source.type(), source.sourceId()),
                    tenantId,
                    source.type(),
                    source.sourceId(),
                    source.displayName(),
                    source.secondaryText(),
                    source.routePath(),
                    searchable,
                    normalize(searchable),
                    write(source.metadata()),
                    Timestamp.from(now));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        TYPES.stream()
                .sorted()
                .forEach(
                        type ->
                                counts.put(
                                        type,
                                        sources.stream()
                                                .filter(source -> type.equals(source.type()))
                                                .count()));
        return new RefreshResult(now, sources.size(), counts);
    }

    private List<Source> students(UUID tenantId) {
        return jdbc.query(
                """
                SELECT s.id,p.first_name,p.last_name,s.status,
                       (SELECT i.identifier_value FROM student_identifier i
                        WHERE i.tenant_id=s.tenant_id AND i.student_id=s.id
                        ORDER BY i.primary_identifier DESC,i.created_at FETCH FIRST 1 ROW ONLY) number
                FROM student_profile s JOIN person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id
                WHERE s.tenant_id=? AND s.archived_at IS NULL
                """,
                (rs, row) ->
                        new Source(
                                "STUDENT",
                                rs.getObject("id", UUID.class),
                                rs.getString("first_name") + " " + rs.getString("last_name"),
                                "Élève · " + value(rs.getString("number")),
                                "/app/students/" + rs.getObject("id", UUID.class),
                                value(rs.getString("number")) + " " + rs.getString("status"),
                                Map.of("status", rs.getString("status"))),
                tenantId);
    }

    private List<Source> parents(UUID tenantId) {
        return jdbc.query(
                """
                SELECT g.id,p.first_name,p.last_name,g.status,
                       (SELECT c.contact_value FROM person_contact_method c
                        WHERE c.tenant_id=p.tenant_id AND c.person_id=p.id
                        ORDER BY c.primary_contact DESC,c.created_at FETCH FIRST 1 ROW ONLY) contact
                FROM guardian_profile g JOIN person p ON p.id=g.person_id AND p.tenant_id=g.tenant_id
                WHERE g.tenant_id=?
                """,
                (rs, row) ->
                        new Source(
                                "PARENT",
                                rs.getObject("id", UUID.class),
                                rs.getString("first_name") + " " + rs.getString("last_name"),
                                "Parent · " + value(rs.getString("contact")),
                                "/app/students",
                                value(rs.getString("contact")),
                                Map.of("status", rs.getString("status"))),
                tenantId);
    }

    private List<Source> teachers(UUID tenantId) {
        return jdbc.query(
                """
                SELECT e.id,p.first_name,p.last_name,e.work_email,pos.title
                FROM hr_employee e
                JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                JOIN hr_position pos ON pos.id=e.position_id AND pos.tenant_id=e.tenant_id
                WHERE e.tenant_id=? AND pos.teacher_position=TRUE AND e.status<>'ENDED'
                """,
                (rs, row) ->
                        new Source(
                                "TEACHER",
                                rs.getObject("id", UUID.class),
                                rs.getString("first_name") + " " + rs.getString("last_name"),
                                rs.getString("title"),
                                "/app/hr",
                                value(rs.getString("work_email")),
                                Map.of("position", rs.getString("title"))),
                tenantId);
    }

    private List<Source> invoices(UUID tenantId) {
        return jdbc.query(
                """
                SELECT id,invoice_number,status,total_amount,currency_code,due_date
                FROM billing_invoice WHERE tenant_id=? AND status<>'DRAFT'
                """,
                (rs, row) ->
                        new Source(
                                "INVOICE",
                                rs.getObject("id", UUID.class),
                                rs.getString("invoice_number"),
                                "Facture · "
                                        + rs.getBigDecimal("total_amount")
                                        + " "
                                        + rs.getString("currency_code"),
                                "/app/finance",
                                rs.getString("status") + " " + rs.getObject("due_date"),
                                Map.of("status", rs.getString("status"))),
                tenantId);
    }

    private List<Source> classrooms(UUID tenantId) {
        return jdbc.query(
                """
                SELECT c.id,c.code,c.name,c.capacity,sc.name campus_name,g.name grade_name
                FROM classroom c
                JOIN school_campus sc ON sc.id=c.campus_id AND sc.tenant_id=c.tenant_id
                JOIN grade_level g ON g.id=c.grade_level_id AND g.tenant_id=c.tenant_id
                WHERE c.tenant_id=? AND c.active=TRUE
                """,
                (rs, row) ->
                        new Source(
                                "CLASSROOM",
                                rs.getObject("id", UUID.class),
                                rs.getString("name"),
                                rs.getString("grade_name") + " · " + rs.getString("campus_name"),
                                "/app/academics",
                                rs.getString("code") + " " + rs.getInt("capacity"),
                                Map.of("capacity", rs.getInt("capacity"))),
                tenantId);
    }

    private List<Source> documents(UUID tenantId) {
        return jdbc.query(
                """
                SELECT d.id,d.title,d.description,d.document_type,d.owner_kind,
                       c.label_fr,c.label_ar
                FROM document_file d
                JOIN document_category c ON c.id=d.category_id AND c.tenant_id=d.tenant_id
                WHERE d.tenant_id=? AND d.status='ACTIVE'
                """,
                (rs, row) ->
                        new Source(
                                "DOCUMENT",
                                rs.getObject("id", UUID.class),
                                rs.getString("title"),
                                "Document · " + rs.getString("label_fr"),
                                "/app/documents",
                                String.join(
                                        " ",
                                        value(rs.getString("description")),
                                        value(rs.getString("label_ar")),
                                        rs.getString("document_type"),
                                        rs.getString("owner_kind")),
                                Map.of("documentType", rs.getString("document_type"))),
                tenantId);
    }

    private SearchItem mapItem(ResultSet rs, int row) throws SQLException {
        return new SearchItem(
                rs.getString("source_type"),
                rs.getObject("source_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("secondary_text"),
                rs.getString("route_path"),
                readMap(rs.getString("metadata_json")),
                rs.getTimestamp("indexed_at").toInstant());
    }

    private SearchResponse response(
            String query,
            String normalized,
            int page,
            int size,
            boolean hasNext,
            long started,
            List<String> availableTypes,
            List<SearchItem> items) {
        Map<String, Long> facets = new LinkedHashMap<>();
        availableTypes.forEach(
                type ->
                        facets.put(
                                type,
                                items.stream().filter(item -> type.equals(item.type())).count()));
        return new SearchResponse(
                query,
                normalized,
                page,
                size,
                hasNext,
                Math.max(0, Duration.ofNanos(System.nanoTime() - started).toMillis()),
                availableTypes,
                facets,
                items);
    }

    public static String normalize(String value) {
        String latin =
                MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        String arabic = ARABIC_MARKS.matcher(latin).replaceAll("");
        arabic = arabic.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي');
        return SPACES.matcher(arabic.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    private String requiredQuery(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Saisissez au moins deux caractères");
        String result = value.trim();
        if (result.length() < 2 || result.length() > 120)
            throw new IllegalArgumentException("La recherche doit contenir de 2 à 120 caractères");
        return result;
    }

    private String optionalType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized))
            throw new IllegalArgumentException("Type de recherche inconnu");
        return normalized;
    }

    private UUID documentId(UUID tenantId, String type, UUID sourceId) {
        return UUID.nameUUIDFromBytes(
                (tenantId + ":" + type + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record SearchResponse(
            String query,
            String normalizedQuery,
            int page,
            int size,
            boolean hasNext,
            long tookMs,
            List<String> availableTypes,
            Map<String, Long> facets,
            List<SearchItem> items) {}

    public record SearchItem(
            String type,
            UUID sourceId,
            String displayName,
            String secondaryText,
            String routePath,
            Map<String, Object> metadata,
            Instant indexedAt) {}

    public record RefreshResult(Instant indexedAt, int documentCount, Map<String, Long> counts) {}

    private record Source(
            String type,
            UUID sourceId,
            String displayName,
            String secondaryText,
            String routePath,
            String extraSearch,
            Map<String, Object> metadata) {}
}
