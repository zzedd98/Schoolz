package ma.schoolz.erp.activities.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import ma.schoolz.erp.activities.application.ActivityService.EnrollCommand;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(190)
public class DevActivitySeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ActivityService service;
    private final boolean enabled;
    private final String slug;

    public DevActivitySeeder(
            JdbcTemplate j,
            Clock c,
            ActivityService s,
            @Value("${schoolz.demo.seed-enabled:false}") boolean e,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String slug) {
        jdbc = j;
        clock = c;
        service = s;
        enabled = e;
        this.slug = slug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments x) {
        if (!enabled) return;
        UUID t = id("SELECT id FROM identity_tenant WHERE slug=?", slug);
        if (count("SELECT count(*) FROM activity WHERE tenant_id=?", t) > 0) return;
        UUID parent =
                id(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=? AND"
                                + " normalized_email='parent.demo@schoolz.ma'",
                        t);
        UUID student =
                id(
                        "SELECT student_id FROM enrollment_portal_student_link WHERE tenant_id=?"
                                + " AND portal_account_id=?",
                        t,
                        parent);
        UUID coach = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " activity_coach(id,tenant_id,display_name,phone,email,specialty,active,created_at)"
                    + " VALUES(?,?,'Sara Alaoui','+212600000333','coach@schoolz.ma','Sports &"
                    + " robotique',TRUE,?)",
                coach,
                t,
                Timestamp.from(clock.instant()));
        UUID football =
                activity(
                        t, coach, "FOOTBALL", "Football", "SPORT", true, new BigDecimal("450"), 12);
        UUID swimming =
                activity(
                        t,
                        coach,
                        "NATATION",
                        "Natation",
                        "SWIMMING",
                        true,
                        new BigDecimal("600"),
                        10);
        UUID robotics =
                activity(
                        t,
                        coach,
                        "ROBOTIQUE",
                        "Robotique",
                        "ROBOTICS",
                        true,
                        new BigDecimal("700"),
                        8);
        activity(t, coach, "MUSIQUE", "Musique", "MUSIC", true, new BigDecimal("500"), 14);
        activity(
                t,
                coach,
                "GARDERIE",
                "Garderie du soir",
                "DAYCARE",
                true,
                new BigDecimal("350"),
                20);
        session(t, football, "Terrain principal", 2);
        session(t, swimming, "Piscine partenaire", 3);
        session(t, robotics, "FabLab", 4);
        PortalIdentity p =
                new PortalIdentity(
                        t, parent, "parent.demo@schoolz.ma", "Famille El Mansouri", "fr-MA");
        service.enrollFamily(p, new EnrollCommand(robotics, student));
    }

    private UUID activity(
            UUID t,
            UUID coach,
            String code,
            String name,
            String cat,
            boolean parent,
            BigDecimal price,
            int capacity) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " activity(id,tenant_id,code,name,category,description,coach_id,allow_parent_enrollment,price,currency_code,status,created_at,updated_at)"
                    + " VALUES(?,?,?,?,?,'Activité de démonstration"
                    + " SchoolZ',?,?,?,'MAD','OPEN',?,?)",
                id,
                t,
                code,
                name,
                cat,
                coach,
                parent,
                price,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO"
                    + " activity_capacity(id,tenant_id,activity_id,maximum_enrollments,waiting_list_enabled,updated_at)"
                    + " VALUES(?,?,?,?,TRUE,?)",
                UUID.randomUUID(),
                t,
                id,
                capacity,
                Timestamp.from(clock.instant()));
        return id;
    }

    private void session(UUID t, UUID a, String location, int days) {
        jdbc.update(
                "INSERT INTO"
                    + " activity_session(id,tenant_id,activity_id,starts_at,ends_at,location,status)"
                    + " VALUES(?,?,?,?,?,?,'SCHEDULED')",
                UUID.randomUUID(),
                t,
                a,
                Timestamp.from(clock.instant().plus(Duration.ofDays(days))),
                Timestamp.from(
                        clock.instant().plus(Duration.ofDays(days)).plus(Duration.ofHours(2))),
                location);
    }

    private UUID id(String q, Object... a) {
        List<UUID> v = jdbc.query(q, (r, n) -> r.getObject(1, UUID.class), a);
        if (v.isEmpty()) throw new IllegalStateException("Donnée démo absente");
        return v.getFirst();
    }

    private int count(String q, Object... a) {
        Integer v = jdbc.queryForObject(q, Integer.class, a);
        return v == null ? 0 : v;
    }
}
