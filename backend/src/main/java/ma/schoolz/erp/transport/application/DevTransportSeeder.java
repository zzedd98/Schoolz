package ma.schoolz.erp.transport.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.transport.application.TransportService.AttendanceCommand;
import ma.schoolz.erp.transport.application.TransportService.PositionCommand;
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
@Order(170)
public class DevTransportSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransportService transport;
    private final boolean enabled;
    private final String tenantSlug;

    public DevTransportSeeder(
            JdbcTemplate jdbc,
            Clock clock,
            TransportService transport,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.transport = transport;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM transport_vehicle WHERE tenant_id=?", tenantId) > 0) return;
        UUID campusId =
                id("SELECT id FROM school_campus WHERE tenant_id=? ORDER BY created_at", tenantId);
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=?"
                                + " AND normalized_email='admin@schoolz.ma'",
                        tenantId);
        UUID parentId =
                id(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=?"
                                + " AND normalized_email='parent.demo@schoolz.ma'",
                        tenantId);
        UUID studentId =
                id(
                        "SELECT student_id FROM enrollment_portal_student_link"
                                + " WHERE tenant_id=? AND portal_account_id=? ORDER BY created_at",
                        tenantId,
                        parentId);
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID pickupStopId = UUID.randomUUID();
        UUID middleStopId = UUID.randomUUID();
        UUID schoolStopId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO transport_vehicle"
                    + " (id,tenant_id,campus_id,code,registration_number,make_model,capacity,status,created_at,updated_at)"
                    + " VALUES (?,?,?,'BUS-01','12345-A-6','Mercedes Sprinter',32,'ACTIVE',?,?)",
                vehicleId,
                tenantId,
                campusId,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO transport_driver"
                    + " (id,tenant_id,display_name,phone,license_number,license_expires_on,active,created_at)"
                    + " VALUES (?,?,'Rachid Benali','+212600000111','PERMIS-DEMO-01',?,TRUE,?)",
                driverId,
                tenantId,
                LocalDate.now(clock).plusYears(2),
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO transport_assistant"
                        + " (id,tenant_id,display_name,phone,active,created_at)"
                        + " VALUES (?,?,'Khadija Amrani','+212600000222',TRUE,?)",
                assistantId,
                tenantId,
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO transport_route"
                    + " (id,tenant_id,campus_id,code,name,description,active,created_at,updated_at)"
                    + " VALUES (?,?,?,'CASA-CENTRE','Casablanca Centre', 'Circuit de démonstration"
                    + " matin et soir',TRUE,?,?)",
                routeId,
                tenantId,
                campusId,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()));
        stop(
                tenantId,
                routeId,
                pickupStopId,
                1,
                "Maarif",
                new BigDecimal("33.580000"),
                new BigDecimal("-7.620000"),
                0);
        stop(
                tenantId,
                routeId,
                middleStopId,
                2,
                "Gauthier",
                new BigDecimal("33.590000"),
                new BigDecimal("-7.610000"),
                10);
        stop(
                tenantId,
                routeId,
                schoolStopId,
                3,
                "École Démo",
                new BigDecimal("33.573000"),
                new BigDecimal("-7.589000"),
                20);
        jdbc.update(
                "INSERT INTO transport_route_assignment"
                        + " (id,tenant_id,route_id,vehicle_id,driver_id,assistant_id,service_date,"
                        + " direction,planned_departure_at,status,created_at)"
                        + " VALUES (?,?,?,?,?,?,?,'MORNING',?,'READY',?)",
                assignmentId,
                tenantId,
                routeId,
                vehicleId,
                driverId,
                assistantId,
                LocalDate.now(clock),
                Timestamp.from(clock.instant().minusSeconds(600)),
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO transport_student_subscription"
                        + " (id,tenant_id,student_id,route_id,pickup_stop_id,dropoff_stop_id,"
                        + " morning_enabled,evening_enabled,starts_on,status,created_at)"
                        + " VALUES (?,?,?,?,?,?,TRUE,TRUE,?,'ACTIVE',?)",
                subscriptionId,
                tenantId,
                studentId,
                routeId,
                pickupStopId,
                schoolStopId,
                LocalDate.now(clock).minusMonths(1),
                Timestamp.from(clock.instant()));
        jdbc.update(
                "INSERT INTO transport_trip"
                    + " (id,tenant_id,assignment_id,status,gps_provider_key,started_at,created_at)"
                    + " VALUES (?,?,?,'IN_PROGRESS','local-demo',?,?)",
                tripId,
                tenantId,
                assignmentId,
                Timestamp.from(clock.instant().minusSeconds(480)),
                Timestamp.from(clock.instant().minusSeconds(480)));

        SchoolPrincipal admin =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Administrateur SchoolZ",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        Set.of(
                                "transport.read",
                                "transport.manage",
                                "transport.attendance",
                                "transport.position"));
        transport.pickup(admin, tripId, new AttendanceCommand(subscriptionId, "NFC"), "seed");
        transport.position(
                admin,
                tripId,
                new PositionCommand(
                        "local-demo",
                        "DEMO-POS-001",
                        new BigDecimal("33.590000"),
                        new BigDecimal("-7.610000"),
                        new BigDecimal("21.5"),
                        new BigDecimal("8.0"),
                        clock.instant()),
                "seed");
    }

    private void stop(
            UUID tenantId,
            UUID routeId,
            UUID stopId,
            int sequence,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            int offset) {
        jdbc.update(
                "INSERT INTO transport_stop"
                    + " (id,tenant_id,route_id,sequence_number,name,address,latitude,longitude,"
                    + " planned_offset_minutes,active) VALUES (?,?,?,?,?,'Casablanca',?,?,?,TRUE)",
                stopId,
                tenantId,
                routeId,
                sequence,
                name,
                latitude,
                longitude,
                offset);
    }

    private UUID id(String sql, Object... args) {
        List<UUID> values = jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), args);
        if (values.isEmpty())
            throw new IllegalStateException("Donnée de démonstration introuvable");
        return values.getFirst();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
