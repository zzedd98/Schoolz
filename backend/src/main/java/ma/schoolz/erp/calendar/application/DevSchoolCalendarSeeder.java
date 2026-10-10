package ma.schoolz.erp.calendar.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
@Order(380)
public class DevSchoolCalendarSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevSchoolCalendarSeeder(
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
        UUID tenant = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM school_calendar_event WHERE tenant_id=?", tenant) > 0)
            return;
        UUID admin =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenant);
        UUID campus =
                id(
                        "SELECT id FROM school_campus WHERE tenant_id=? ORDER BY created_at FETCH"
                                + " FIRST 1 ROW ONLY",
                        tenant);
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = ZoneId.of("Africa/Casablanca");
        insert(
                tenant,
                admin,
                "Réunion pédagogique de rentrée",
                "MEETING",
                "CAMPUS",
                campus,
                today.plusDays(2).atTime(16, 30).atZone(zone).toInstant(),
                today.plusDays(2).atTime(18, 0).atZone(zone).toInstant(),
                false,
                "Salle des professeurs");
        insert(
                tenant,
                admin,
                "Évaluations diagnostiques",
                "EXAM",
                "SCHOOL",
                null,
                today.plusDays(6).atTime(8, 0).atZone(zone).toInstant(),
                today.plusDays(8).atTime(16, 0).atZone(zone).toInstant(),
                false,
                "Classes");
        insert(
                tenant,
                admin,
                "Rencontre parents–professeurs",
                "PARENT_TEACHER",
                "CAMPUS",
                campus,
                today.plusDays(12).atTime(17, 0).atZone(zone).toInstant(),
                today.plusDays(12).atTime(20, 0).atZone(zone).toInstant(),
                false,
                "Hall principal");
        insert(
                tenant,
                admin,
                "Sortie au Musée Mohammed VI",
                "TRIP",
                "SCHOOL",
                null,
                today.plusDays(18).atTime(8, 0).atZone(zone).toInstant(),
                today.plusDays(18).atTime(17, 0).atZone(zone).toInstant(),
                true,
                "Rabat");
        insert(
                tenant,
                admin,
                "Semaine de vacances",
                "HOLIDAY",
                "SCHOOL",
                null,
                today.plusDays(28).atStartOfDay(zone).toInstant(),
                today.plusDays(35).atStartOfDay(zone).toInstant(),
                true,
                null);
        insert(
                tenant,
                admin,
                "Tournoi interclasses",
                "ACTIVITY",
                "CAMPUS",
                campus,
                today.plusDays(21).atTime(14, 0).atZone(zone).toInstant(),
                today.plusDays(21).atTime(17, 30).atZone(zone).toInstant(),
                false,
                "Terrain de sport");
    }

    private void insert(
            UUID tenant,
            UUID admin,
            String title,
            String type,
            String scope,
            UUID campus,
            java.time.Instant start,
            java.time.Instant end,
            boolean allDay,
            String location) {
        jdbc.update(
                "INSERT INTO school_calendar_event"
                    + " (id,tenant_id,title,event_type,audience_scope,campus_id,starts_at,ends_at,all_day,location,status,sync_status,created_by_user_id,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,'SCHEDULED','LOCAL_ONLY',?,?,?)",
                UUID.randomUUID(),
                tenant,
                title,
                type,
                scope,
                campus,
                Timestamp.from(start),
                Timestamp.from(end),
                allDay,
                location,
                admin,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()));
    }

    private UUID id(String sql, Object... args) {
        List<UUID> values = jdbc.query(sql, (r, n) -> r.getObject(1, UUID.class), args);
        if (values.isEmpty()) throw new IllegalStateException("Calendrier démo incomplet");
        return values.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
