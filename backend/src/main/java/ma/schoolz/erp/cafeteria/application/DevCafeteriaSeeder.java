package ma.schoolz.erp.cafeteria.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.cafeteria.application.CafeteriaService.*;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(180)
public class DevCafeteriaSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CafeteriaService service;
    private final boolean enabled;
    private final String slug;

    public DevCafeteriaSeeder(
            JdbcTemplate j,
            Clock c,
            CafeteriaService s,
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
    public void run(ApplicationArguments a) {
        if (!enabled) return;
        UUID t = id("SELECT id FROM identity_tenant WHERE slug=?", slug);
        if (count("SELECT count(*) FROM cafeteria_meal_plan WHERE tenant_id=?", t) > 0) return;
        UUID admin =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        t);
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
        UUID monthly = UUID.randomUUID(),
                single = UUID.randomUUID(),
                menu = UUID.randomUUID(),
                allergen = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_meal_plan(id,tenant_id,code,name,plan_type,monthly_price,single_meal_price,active,created_at)"
                    + " VALUES(?,?,'MENSUEL','Formule mensuelle','MONTHLY',650,35,TRUE,?)",
                monthly,
                t,
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_meal_plan(id,tenant_id,code,name,plan_type,monthly_price,single_meal_price,active,created_at)"
                    + " VALUES(?,?,'PONCTUEL','Repas ponctuel','SINGLE',NULL,40,TRUE,?)",
                single,
                t,
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_menu(id,tenant_id,service_date,meal_type,title,description,published,created_at)"
                    + " VALUES(?,?,?,'LUNCH','Tajine de poulet & légumes','Salade, tajine, fruit et"
                    + " eau',TRUE,?)",
                menu,
                t,
                LocalDate.now(clock),
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO cafeteria_allergen(id,tenant_id,code,label_fr,label_ar,active)"
                        + " VALUES(?,?,'PEANUT','Arachide','الفول السوداني',TRUE)",
                allergen,
                t);
        jdbc.update(
                "INSERT INTO"
                    + " cafeteria_food_restriction(id,tenant_id,student_id,restriction_type,allergen_id,details,severity,emergency_action,verified_by,verified_at,active,created_at)"
                    + " VALUES(?,?,?,'ALLERGY',?,'Éviter toute trace','HIGH','Contacter le parent"
                    + " et suivre le PAI','Infirmière démo',?,TRUE,?)",
                UUID.randomUUID(),
                t,
                student,
                allergen,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()));
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        admin,
                        t,
                        "admin@schoolz.ma",
                        "Administrateur SchoolZ",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        Set.of(
                                "cafeteria.read",
                                "cafeteria.manage",
                                "cafeteria.attendance",
                                "cafeteria.health"));
        Subscription sub =
                service.subscribe(
                        actor,
                        new SubscribeCommand(
                                student, monthly, LocalDate.now(clock).withDayOfMonth(1), null),
                        "seed");
        service.attend(actor, new AttendanceCommand(student, menu, sub.id()), "seed");
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
