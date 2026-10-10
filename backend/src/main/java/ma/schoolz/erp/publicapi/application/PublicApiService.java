package ma.schoolz.erp.publicapi.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
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
public class PublicApiService {
    public static final String DEMO_KEY = "schoolz_demo_api_key_m37_2026";
    private static final Set<String> ALLOWED_SCOPES =
            Set.of("students:read", "invoices:read", "classes:read");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public PublicApiService(
            JdbcTemplate jdbc, ObjectMapper json, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('public-api.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        List<ClientView> clients = clients(actor.tenantId());
        List<RequestLogView> logs = requestLogs(actor.tenantId());
        long active = clients.stream().filter(ClientView::active).count();
        long requests = logs.size();
        long limited = logs.stream().filter(log -> "RATE_LIMITED".equals(log.outcome())).count();
        return new Dashboard(
                clock.instant(),
                new Statistics(clients.size(), active, requests, limited),
                ALLOWED_SCOPES.stream().sorted().toList(),
                clients,
                logs,
                List.of(
                        new EndpointView("GET", "/api/public/v1/students", "students:read"),
                        new EndpointView("GET", "/api/public/v1/invoices", "invoices:read"),
                        new EndpointView("GET", "/api/public/v1/classes", "classes:read")));
    }

    @Transactional
    @PreAuthorize("hasAuthority('public-api.manage')")
    public CreatedKey create(SchoolPrincipal actor, ApiKeyCommand command, String ipAddress) {
        ValidatedCommand valid = validate(command);
        String raw = generateKey();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO public_api_client
                    (id,tenant_id,name,description,key_prefix,key_hash,scopes_json,
                     rate_limit_per_minute,active,expires_at,created_by_user_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,TRUE,?,?,?)
                """,
                id,
                actor.tenantId(),
                valid.name(),
                valid.description(),
                prefix(raw),
                hashKey(raw),
                write(valid.scopes()),
                valid.rateLimit(),
                valid.expiresAt() == null ? null : Timestamp.from(valid.expiresAt()),
                actor.accountId(),
                Timestamp.from(now));
        ClientView created = client(actor.tenantId(), id);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PUBLIC_API_KEY_CREATED",
                "public-api-client",
                id.toString(),
                ipAddress,
                Map.of("scopes", valid.scopes(), "keyPrefix", created.keyPrefix()),
                null,
                created);
        return new CreatedKey(created, raw);
    }

    @Transactional
    @PreAuthorize("hasAuthority('public-api.manage')")
    public ClientView revoke(
            SchoolPrincipal actor, UUID clientId, RevokeCommand command, String ipAddress) {
        ClientView before = client(actor.tenantId(), clientId);
        if (!before.active()) return before;
        String reason = required(command == null ? null : command.reason(), "Le motif", 500);
        jdbc.update(
                """
                UPDATE public_api_client
                SET active=FALSE,revoked_by_user_id=?,revoked_at=?
                WHERE tenant_id=? AND id=? AND active=TRUE
                """,
                actor.accountId(),
                Timestamp.from(clock.instant()),
                actor.tenantId(),
                clientId);
        ClientView after = client(actor.tenantId(), clientId);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "PUBLIC_API_KEY_REVOKED",
                "public-api-client",
                clientId.toString(),
                ipAddress,
                Map.of("reason", reason, "keyPrefix", before.keyPrefix()),
                before,
                after);
        return after;
    }

    @Transactional(
            noRollbackFor = {PublicApiForbiddenException.class, PublicApiRateLimitException.class})
    public RequestContext authorize(
            String rawKey, String scope, String method, String path, String ipAddress) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new PublicApiUnauthorizedException("L’en-tête X-SchoolZ-Api-Key est requis");
        }
        List<ClientView> matches =
                jdbc.query(
                        "SELECT * FROM public_api_client WHERE key_hash=?",
                        this::mapClient,
                        hashKey(rawKey.trim()));
        if (matches.isEmpty()) throw new PublicApiUnauthorizedException("Clé API invalide");
        ClientView client = matches.getFirst();
        Instant now = clock.instant();
        if (!client.active() || (client.expiresAt() != null && !client.expiresAt().isAfter(now))) {
            throw new PublicApiUnauthorizedException("Clé API révoquée ou expirée");
        }
        RequestContext context =
                new RequestContext(
                        UUID.randomUUID(),
                        client.tenantId(),
                        client.id(),
                        client.name(),
                        client.rateLimitPerMinute(),
                        0,
                        scope,
                        method,
                        path == null ? "" : path.substring(0, Math.min(path.length(), 500)),
                        ipAddress,
                        now);
        if (!client.scopes().contains(scope)) {
            log(context, 403, "UNAUTHORIZED_SCOPE", 0);
            throw new PublicApiForbiddenException("Le scope " + scope + " est requis");
        }
        Long recent =
                jdbc.queryForObject(
                        "SELECT count(*) FROM public_api_request_log WHERE client_id=? AND"
                                + " requested_at>=?",
                        Long.class,
                        client.id(),
                        Timestamp.from(now.minusSeconds(60)));
        int used = recent == null ? 0 : recent.intValue();
        if (used >= client.rateLimitPerMinute()) {
            RequestContext limited = context.withRemaining(0);
            log(limited, 429, "RATE_LIMITED", 0);
            throw new PublicApiRateLimitException("Quota par minute atteint");
        }
        jdbc.update(
                "UPDATE public_api_client SET last_used_at=? WHERE id=? AND tenant_id=?",
                Timestamp.from(now),
                client.id(),
                client.tenantId());
        return context.withRemaining(Math.max(0, client.rateLimitPerMinute() - used - 1));
    }

    @Transactional
    public void complete(RequestContext context, int statusCode, long startedNanos) {
        long duration = Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        log(context, statusCode, statusCode < 400 ? "SUCCESS" : "FAILED", duration);
    }

    @Transactional(readOnly = true)
    public PageView<StudentResource> students(
            RequestContext context, int requestedPage, int requestedSize) {
        PageRequest page = page(requestedPage, requestedSize);
        List<StudentResource> rows =
                jdbc.query(
                        """
                        SELECT s.id,
                               (SELECT i.identifier_value FROM student_identifier i
                                WHERE i.tenant_id=s.tenant_id AND i.student_id=s.id
                                ORDER BY i.primary_identifier DESC,i.created_at FETCH FIRST 1 ROW ONLY) student_number,
                               p.first_name,p.last_name,s.status,c.name classroom_name,sc.name campus_name
                        FROM student_profile s
                        JOIN person p ON p.id=s.person_id AND p.tenant_id=s.tenant_id
                        LEFT JOIN student_enrollment e ON e.student_id=s.id AND e.tenant_id=s.tenant_id
                            AND e.status='ACTIVE'
                        LEFT JOIN classroom c ON c.id=e.classroom_id AND c.tenant_id=e.tenant_id
                        LEFT JOIN school_campus sc ON sc.id=e.campus_id AND sc.tenant_id=e.tenant_id
                        WHERE s.tenant_id=? AND s.archived_at IS NULL
                        ORDER BY p.last_name,p.first_name,s.id
                        LIMIT ? OFFSET ?
                        """,
                        (rs, row) ->
                                new StudentResource(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("student_number"),
                                        rs.getString("first_name"),
                                        rs.getString("last_name"),
                                        rs.getString("status"),
                                        rs.getString("classroom_name"),
                                        rs.getString("campus_name")),
                        context.tenantId(),
                        page.size() + 1,
                        page.offset());
        return page(rows, page);
    }

    @Transactional(readOnly = true)
    public PageView<InvoiceResource> invoices(
            RequestContext context, int requestedPage, int requestedSize) {
        PageRequest page = page(requestedPage, requestedSize);
        List<InvoiceResource> rows =
                jdbc.query(
                        """
                        SELECT id,invoice_number,issue_date,due_date,currency_code,status,total_amount,
                               paid_amount,balance_amount
                        FROM billing_invoice
                        WHERE tenant_id=? AND status<>'DRAFT'
                        ORDER BY issue_date DESC,invoice_number DESC
                        LIMIT ? OFFSET ?
                        """,
                        (rs, row) ->
                                new InvoiceResource(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("invoice_number"),
                                        rs.getObject("issue_date", LocalDate.class),
                                        rs.getObject("due_date", LocalDate.class),
                                        rs.getString("currency_code"),
                                        rs.getString("status"),
                                        rs.getBigDecimal("total_amount"),
                                        rs.getBigDecimal("paid_amount"),
                                        rs.getBigDecimal("balance_amount")),
                        context.tenantId(),
                        page.size() + 1,
                        page.offset());
        return page(rows, page);
    }

    @Transactional(readOnly = true)
    public PageView<ClassResource> classes(
            RequestContext context, int requestedPage, int requestedSize) {
        PageRequest page = page(requestedPage, requestedSize);
        List<ClassResource> rows =
                jdbc.query(
                        """
                        SELECT c.id,c.code,c.name,c.capacity,c.active,g.name grade_name,
                               sc.name campus_name,y.name academic_year_name,
                               (SELECT count(*) FROM student_enrollment e WHERE e.tenant_id=c.tenant_id
                                AND e.classroom_id=c.id AND e.status='ACTIVE') enrolled_count
                        FROM classroom c
                        JOIN grade_level g ON g.id=c.grade_level_id AND g.tenant_id=c.tenant_id
                        JOIN school_campus sc ON sc.id=c.campus_id AND sc.tenant_id=c.tenant_id
                        JOIN academic_year y ON y.id=c.academic_year_id AND y.tenant_id=c.tenant_id
                        WHERE c.tenant_id=?
                        ORDER BY sc.name,g.sequence_number,c.name
                        LIMIT ? OFFSET ?
                        """,
                        (rs, row) ->
                                new ClassResource(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getString("grade_name"),
                                        rs.getString("campus_name"),
                                        rs.getString("academic_year_name"),
                                        rs.getInt("capacity"),
                                        rs.getInt("enrolled_count"),
                                        rs.getBoolean("active")),
                        context.tenantId(),
                        page.size() + 1,
                        page.offset());
        return page(rows, page);
    }

    private void log(RequestContext context, int status, String outcome, long duration) {
        jdbc.update(
                """
                INSERT INTO public_api_request_log
                    (id,tenant_id,client_id,request_id,method,request_path,required_scope,
                     status_code,outcome,ip_address,duration_ms,requested_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                context.tenantId(),
                context.clientId(),
                context.requestId(),
                context.method(),
                context.path(),
                context.requiredScope(),
                status,
                outcome,
                context.ipAddress(),
                duration,
                Timestamp.from(context.startedAt()));
    }

    private List<ClientView> clients(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM public_api_client WHERE tenant_id=? ORDER BY created_at DESC",
                this::mapClient,
                tenantId);
    }

    private ClientView client(UUID tenantId, UUID id) {
        List<ClientView> rows =
                jdbc.query(
                        "SELECT * FROM public_api_client WHERE tenant_id=? AND id=?",
                        this::mapClient,
                        tenantId,
                        id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Client API introuvable");
        return rows.getFirst();
    }

    private ClientView mapClient(ResultSet rs, int row) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp lastUsed = rs.getTimestamp("last_used_at");
        Timestamp revoked = rs.getTimestamp("revoked_at");
        return new ClientView(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("key_prefix"),
                readScopes(rs.getString("scopes_json")),
                rs.getInt("rate_limit_per_minute"),
                rs.getBoolean("active"),
                expires == null ? null : expires.toInstant(),
                lastUsed == null ? null : lastUsed.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                revoked == null ? null : revoked.toInstant());
    }

    private List<RequestLogView> requestLogs(UUID tenantId) {
        return jdbc.query(
                """
                SELECT l.request_id,c.name client_name,c.key_prefix,l.method,l.request_path,
                       l.required_scope,l.status_code,l.outcome,l.duration_ms,l.requested_at
                FROM public_api_request_log l
                JOIN public_api_client c ON c.id=l.client_id AND c.tenant_id=l.tenant_id
                WHERE l.tenant_id=? ORDER BY l.requested_at DESC FETCH FIRST 100 ROWS ONLY
                """,
                (rs, row) ->
                        new RequestLogView(
                                rs.getObject("request_id", UUID.class),
                                rs.getString("client_name"),
                                rs.getString("key_prefix"),
                                rs.getString("method"),
                                rs.getString("request_path"),
                                rs.getString("required_scope"),
                                rs.getInt("status_code"),
                                rs.getString("outcome"),
                                rs.getLong("duration_ms"),
                                rs.getTimestamp("requested_at").toInstant()),
                tenantId);
    }

    private ValidatedCommand validate(ApiKeyCommand command) {
        if (command == null) throw new IllegalArgumentException("La clé API est incomplète");
        String name = required(command.name(), "Le nom", 160);
        String description = optional(command.description(), 500);
        LinkedHashSet<String> scopes =
                command.scopes() == null
                        ? new LinkedHashSet<>()
                        : new LinkedHashSet<>(command.scopes());
        if (scopes.isEmpty() || !ALLOWED_SCOPES.containsAll(scopes)) {
            throw new IllegalArgumentException("Les scopes demandés sont absents ou inconnus");
        }
        int limit = command.rateLimitPerMinute();
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException(
                    "Le quota doit être compris entre 1 et 1000 par minute");
        }
        if (command.expiresAt() != null && !command.expiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("La date d’expiration doit être future");
        }
        return new ValidatedCommand(
                name, description, scopes.stream().sorted().toList(), limit, command.expiresAt());
    }

    private PageRequest page(int requestedPage, int requestedSize) {
        if (requestedPage < 0 || requestedPage > 10000) {
            throw new IllegalArgumentException("Page invalide");
        }
        int size = requestedSize <= 0 ? 25 : Math.min(requestedSize, 100);
        return new PageRequest(requestedPage, size, requestedPage * size);
    }

    private <T> PageView<T> page(List<T> rows, PageRequest page) {
        boolean hasNext = rows.size() > page.size();
        List<T> items = hasNext ? rows.subList(0, page.size()) : rows;
        return new PageView<>(items, page.page(), page.size(), hasNext);
    }

    private String generateKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "sz_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashKey(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String prefix(String value) {
        return value.substring(0, Math.min(16, value.length()));
    }

    private List<String> readScopes(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Scopes persistés invalides", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Scopes invalides", exception);
        }
    }

    private String required(String value, String label, int maximum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " est requis");
        String result = value.trim();
        if (result.length() > maximum) throw new IllegalArgumentException(label + " est trop long");
        return result;
    }

    private String optional(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maximum)
            throw new IllegalArgumentException("Description trop longue");
        return result;
    }

    private record ValidatedCommand(
            String name,
            String description,
            List<String> scopes,
            int rateLimit,
            Instant expiresAt) {}

    private record PageRequest(int page, int size, int offset) {}

    public record ApiKeyCommand(
            String name,
            String description,
            List<String> scopes,
            int rateLimitPerMinute,
            Instant expiresAt) {}

    public record RevokeCommand(String reason) {}

    public record CreatedKey(ClientView client, String apiKey) {}

    public record Dashboard(
            Instant generatedAt,
            Statistics statistics,
            List<String> allowedScopes,
            List<ClientView> clients,
            List<RequestLogView> requestLogs,
            List<EndpointView> endpoints) {}

    public record Statistics(
            long clientCount, long activeCount, long requestCount, long limitedCount) {}

    public record EndpointView(String method, String path, String scope) {}

    public record ClientView(
            UUID id,
            UUID tenantId,
            String name,
            String description,
            String keyPrefix,
            List<String> scopes,
            int rateLimitPerMinute,
            boolean active,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant createdAt,
            Instant revokedAt) {}

    public record RequestLogView(
            UUID requestId,
            String clientName,
            String keyPrefix,
            String method,
            String path,
            String requiredScope,
            int statusCode,
            String outcome,
            long durationMs,
            Instant requestedAt) {}

    public record RequestContext(
            UUID requestId,
            UUID tenantId,
            UUID clientId,
            String clientName,
            int rateLimit,
            int remaining,
            String requiredScope,
            String method,
            String path,
            String ipAddress,
            Instant startedAt) {
        RequestContext withRemaining(int value) {
            return new RequestContext(
                    requestId,
                    tenantId,
                    clientId,
                    clientName,
                    rateLimit,
                    value,
                    requiredScope,
                    method,
                    path,
                    ipAddress,
                    startedAt);
        }
    }

    public record PageView<T>(List<T> items, int page, int size, boolean hasNext) {}

    public record StudentResource(
            UUID id,
            String studentNumber,
            String firstName,
            String lastName,
            String status,
            String classroom,
            String campus) {}

    public record InvoiceResource(
            UUID id,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            String currency,
            String status,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance) {}

    public record ClassResource(
            UUID id,
            String code,
            String name,
            String grade,
            String campus,
            String academicYear,
            int capacity,
            int enrolled,
            boolean active) {}
}
