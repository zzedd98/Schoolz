package ma.schoolz.erp.transport.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;
    private final Map<String, GpsProvider> gpsProviders;

    public TransportService(
            JdbcTemplate jdbc,
            Clock clock,
            IdentityAuditTrail audit,
            List<GpsProvider> gpsProviders) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
        this.gpsProviders =
                gpsProviders.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        provider -> provider.key().toLowerCase(Locale.ROOT),
                                        Function.identity()));
    }

    @Transactional(readOnly = true)
    public TransportDashboard dashboard(SchoolPrincipal actor) {
        require(actor, "transport.read");
        return new TransportDashboard(
                clock.instant(),
                vehicles(actor.tenantId()),
                drivers(actor.tenantId()),
                assistants(actor.tenantId()),
                routes(actor.tenantId()),
                assignments(actor.tenantId()),
                subscriptions(actor.tenantId()),
                trips(actor.tenantId()),
                students(actor.tenantId()),
                gpsProviders.keySet().stream().sorted().toList());
    }

    @Transactional(readOnly = true)
    public FamilyTransportDashboard familyDashboard(PortalIdentity parent) {
        List<FamilyTripView> children =
                jdbc.query(
                        "SELECT sub.id AS subscription_id,sub.student_id,sub.morning_enabled,"
                            + " sub.evening_enabled,r.id AS route_id,r.name AS route_name,"
                            + " pickup.name AS pickup_name,dropoff.name AS dropoff_name,"
                            + " pickup.latitude AS pickup_lat,pickup.longitude AS pickup_lon,"
                            + " dropoff.latitude AS dropoff_lat,dropoff.longitude AS dropoff_lon,"
                            + " p.first_name,p.last_name FROM enrollment_portal_student_link link"
                            + " JOIN transport_student_subscription sub ON"
                            + " sub.tenant_id=link.tenant_id AND sub.student_id=link.student_id AND"
                            + " sub.status='ACTIVE' JOIN transport_route r ON r.id=sub.route_id"
                            + " JOIN transport_stop pickup ON pickup.id=sub.pickup_stop_id JOIN"
                            + " transport_stop dropoff ON dropoff.id=sub.dropoff_stop_id JOIN"
                            + " student_profile sp ON sp.id=sub.student_id JOIN person p ON"
                            + " p.id=sp.person_id WHERE link.tenant_id=? AND"
                            + " link.portal_account_id=? ORDER BY p.first_name,p.last_name",
                        (result, row) -> familyTrip(parent.tenantId(), result),
                        parent.tenantId(),
                        parent.accountId());
        return new FamilyTransportDashboard(
                clock.instant(),
                parent.displayName(),
                children,
                gpsProviders.keySet().stream().sorted().toList());
    }

    @Transactional
    public VehicleView createVehicle(
            SchoolPrincipal actor, VehicleCommand command, String ipAddress) {
        require(actor, "transport.manage");
        if (command.capacity() == null || command.capacity() < 1 || command.capacity() > 100) {
            throw new TransportValidationException("Capacité du véhicule invalide");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO transport_vehicle"
                    + " (id,tenant_id,campus_id,code,registration_number,make_model,capacity,status,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)",
                id,
                actor.tenantId(),
                command.campusId(),
                code(command.code()),
                required(command.registrationNumber(), "Immatriculation obligatoire", 40),
                optional(command.makeModel(), 160),
                command.capacity(),
                timestamp(now),
                timestamp(now));
        audit(actor, "transport.vehicle.create", "TransportVehicle", id, ipAddress);
        return vehicles(actor.tenantId()).stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public RouteView createRoute(SchoolPrincipal actor, RouteCommand command, String ipAddress) {
        require(actor, "transport.manage");
        if (command.stops() == null || command.stops().size() < 2) {
            throw new TransportValidationException("Un circuit doit avoir au moins deux arrêts");
        }
        UUID routeId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO transport_route"
                    + " (id,tenant_id,campus_id,code,name,description,active,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,TRUE,?,?)",
                routeId,
                actor.tenantId(),
                command.campusId(),
                code(command.code()),
                required(command.name(), "Nom du circuit obligatoire", 180),
                optional(command.description(), 1000),
                timestamp(now),
                timestamp(now));
        for (int index = 0; index < command.stops().size(); index++) {
            StopCommand stop = command.stops().get(index);
            jdbc.update(
                    "INSERT INTO transport_stop"
                        + " (id,tenant_id,route_id,sequence_number,name,address,latitude,longitude,"
                        + " planned_offset_minutes,active) VALUES (?,?,?,?,?,?,?,?,?,TRUE)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    routeId,
                    index + 1,
                    required(stop.name(), "Nom d'arrêt obligatoire", 180),
                    optional(stop.address(), 300),
                    stop.latitude(),
                    stop.longitude(),
                    stop.plannedOffsetMinutes() == null ? index * 10 : stop.plannedOffsetMinutes());
        }
        audit(actor, "transport.route.create", "TransportRoute", routeId, ipAddress);
        return routes(actor.tenantId()).stream()
                .filter(value -> value.id().equals(routeId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public SubscriptionView subscribe(
            SchoolPrincipal actor, SubscriptionCommand command, String ipAddress) {
        require(actor, "transport.manage");
        UUID studentId = required(command.studentId(), "Élève requis");
        UUID routeId = required(command.routeId(), "Circuit requis");
        requireExists("student_profile", actor.tenantId(), studentId, "Élève introuvable");
        requireExists("transport_route", actor.tenantId(), routeId, "Circuit introuvable");
        validateStop(actor.tenantId(), routeId, command.pickupStopId());
        validateStop(actor.tenantId(), routeId, command.dropoffStopId());
        if (!command.morningEnabled() && !command.eveningEnabled()) {
            throw new TransportValidationException("Choisir au moins un trajet matin ou soir");
        }
        LocalDate startsOn = command.startsOn() == null ? LocalDate.now(clock) : command.startsOn();
        if (command.endsOn() != null && command.endsOn().isBefore(startsOn)) {
            throw new TransportValidationException("Période d'abonnement invalide");
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO transport_student_subscription"
                        + " (id,tenant_id,student_id,route_id,pickup_stop_id,dropoff_stop_id,"
                        + " morning_enabled,evening_enabled,starts_on,ends_on,status,created_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVE',?)",
                id,
                actor.tenantId(),
                studentId,
                routeId,
                command.pickupStopId(),
                command.dropoffStopId(),
                command.morningEnabled(),
                command.eveningEnabled(),
                startsOn,
                command.endsOn(),
                timestamp(clock.instant()));
        audit(actor, "transport.subscription.create", "TransportSubscription", id, ipAddress);
        return subscriptions(actor.tenantId()).stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public AssignmentView assign(
            SchoolPrincipal actor, AssignmentCommand command, String ipAddress) {
        require(actor, "transport.manage");
        UUID routeId = required(command.routeId(), "Circuit requis");
        UUID vehicleId = required(command.vehicleId(), "Véhicule requis");
        UUID driverId = required(command.driverId(), "Chauffeur requis");
        requireEntity("transport_route", actor.tenantId(), routeId, "Circuit introuvable");
        requireEntity("transport_vehicle", actor.tenantId(), vehicleId, "Véhicule introuvable");
        requireEntity("transport_driver", actor.tenantId(), driverId, "Chauffeur introuvable");
        if (command.assistantId() != null) {
            requireEntity(
                    "transport_assistant",
                    actor.tenantId(),
                    command.assistantId(),
                    "Accompagnateur introuvable");
        }
        String direction =
                command.direction() == null ? "" : command.direction().toUpperCase(Locale.ROOT);
        if (!List.of("MORNING", "EVENING").contains(direction)) {
            throw new TransportValidationException("Sens matin/soir invalide");
        }
        LocalDate serviceDate =
                command.serviceDate() == null ? LocalDate.now(clock) : command.serviceDate();
        Instant departure =
                command.plannedDepartureAt() == null
                        ? clock.instant().plusSeconds(3600)
                        : command.plannedDepartureAt();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO transport_route_assignment"
                        + " (id,tenant_id,route_id,vehicle_id,driver_id,assistant_id,service_date,"
                        + " direction,planned_departure_at,status,created_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,'PLANNED',?)",
                id,
                actor.tenantId(),
                routeId,
                vehicleId,
                driverId,
                command.assistantId(),
                serviceDate,
                direction,
                timestamp(departure),
                timestamp(clock.instant()));
        audit(actor, "transport.assignment.create", "TransportAssignment", id, ipAddress);
        return assignments(actor.tenantId()).stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public TripView startTrip(SchoolPrincipal actor, StartTripCommand command, String ipAddress) {
        require(actor, "transport.manage");
        UUID assignmentId = required(command.assignmentId(), "Affectation requise");
        AssignmentView assignment =
                assignments(actor.tenantId()).stream()
                        .filter(value -> value.id().equals(assignmentId))
                        .findFirst()
                        .orElseThrow(
                                () -> new TransportNotFoundException("Affectation introuvable"));
        String providerKey =
                command.gpsProviderKey() == null || command.gpsProviderKey().isBlank()
                        ? "local-demo"
                        : command.gpsProviderKey().toLowerCase(Locale.ROOT);
        if (!gpsProviders.containsKey(providerKey)) {
            throw new TransportValidationException("Fournisseur GPS inconnu");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO transport_trip"
                    + " (id,tenant_id,assignment_id,status,gps_provider_key,started_at,created_at)"
                    + " VALUES (?,?,?,'IN_PROGRESS',?,?,?)",
                id,
                actor.tenantId(),
                assignmentId,
                providerKey,
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "UPDATE transport_route_assignment SET status='READY' WHERE tenant_id=? AND id=?",
                actor.tenantId(),
                assignment.id());
        audit(actor, "transport.trip.start", "TransportTrip", id, ipAddress);
        return trip(actor.tenantId(), id);
    }

    @Transactional
    public TripView pickup(
            SchoolPrincipal actor, UUID tripId, AttendanceCommand command, String ipAddress) {
        requireAny(actor, "transport.attendance", "transport.manage");
        TripContext context = tripContext(actor.tenantId(), tripId);
        SubscriptionContext subscription =
                subscriptionContext(actor.tenantId(), command.subscriptionId());
        validateTripSubscription(context, subscription);
        int onboard = onboard(actor.tenantId(), tripId);
        if (onboard >= context.capacity()) {
            throw new TransportValidationException("Capacité du bus atteinte");
        }
        jdbc.update(
                "INSERT INTO transport_pickup_event"
                        + " (id,tenant_id,trip_id,subscription_id,student_id,stop_id,occurred_at,"
                        + " recorded_by_user_id,source) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.tenantId(),
                tripId,
                subscription.id(),
                subscription.studentId(),
                subscription.pickupStopId(),
                timestamp(clock.instant()),
                actor.accountId(),
                source(command.source()));
        audit(actor, "transport.student.pickup", "TransportTrip", tripId, ipAddress);
        return trip(actor.tenantId(), tripId);
    }

    @Transactional
    public TripView dropoff(
            SchoolPrincipal actor, UUID tripId, AttendanceCommand command, String ipAddress) {
        requireAny(actor, "transport.attendance", "transport.manage");
        TripContext context = tripContext(actor.tenantId(), tripId);
        SubscriptionContext subscription =
                subscriptionContext(actor.tenantId(), command.subscriptionId());
        validateTripSubscription(context, subscription);
        Integer pickupCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM transport_pickup_event"
                                + " WHERE tenant_id=? AND trip_id=? AND student_id=?",
                        Integer.class,
                        actor.tenantId(),
                        tripId,
                        subscription.studentId());
        if (pickupCount == null || pickupCount == 0) {
            throw new TransportValidationException("L'élève doit monter avant de descendre");
        }
        jdbc.update(
                "INSERT INTO transport_dropoff_event"
                        + " (id,tenant_id,trip_id,subscription_id,student_id,stop_id,occurred_at,"
                        + " recorded_by_user_id,source) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.tenantId(),
                tripId,
                subscription.id(),
                subscription.studentId(),
                subscription.dropoffStopId(),
                timestamp(clock.instant()),
                actor.accountId(),
                source(command.source()));
        audit(actor, "transport.student.dropoff", "TransportTrip", tripId, ipAddress);
        return trip(actor.tenantId(), tripId);
    }

    @Transactional
    public TripView position(
            SchoolPrincipal actor, UUID tripId, PositionCommand command, String ipAddress) {
        requireAny(actor, "transport.position", "transport.manage");
        TripContext context = tripContext(actor.tenantId(), tripId);
        String providerKey =
                command.providerKey() == null || command.providerKey().isBlank()
                        ? context.gpsProviderKey()
                        : command.providerKey().toLowerCase(Locale.ROOT);
        if (!gpsProviders.containsKey(providerKey)) {
            throw new TransportValidationException("Fournisseur GPS inconnu");
        }
        validateCoordinates(command.latitude(), command.longitude());
        Instant recordedAt = command.recordedAt() == null ? clock.instant() : command.recordedAt();
        jdbc.update(
                "INSERT INTO transport_vehicle_position"
                        + " (id,tenant_id,trip_id,vehicle_id,provider_key,external_position_id,"
                        + " latitude,longitude,speed_kph,accuracy_meters,recorded_at,received_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.tenantId(),
                tripId,
                context.vehicleId(),
                providerKey,
                optional(command.externalPositionId(), 180),
                command.latitude(),
                command.longitude(),
                command.speedKph(),
                command.accuracyMeters(),
                timestamp(recordedAt),
                timestamp(clock.instant()));
        audit(actor, "transport.vehicle.position", "TransportTrip", tripId, ipAddress);
        return trip(actor.tenantId(), tripId);
    }

    private FamilyTripView familyTrip(UUID tenantId, ResultSet result) throws SQLException {
        UUID routeId = result.getObject("route_id", UUID.class);
        TripSummary trip = latestTrip(tenantId, routeId);
        UUID studentId = result.getObject("student_id", UUID.class);
        EventSummary pickup = trip == null ? null : event(tenantId, trip.id(), studentId, true);
        EventSummary dropoff = trip == null ? null : event(tenantId, trip.id(), studentId, false);
        boolean onboard = pickup != null && dropoff == null;
        PositionView position = trip == null ? null : latestPosition(tenantId, trip.id());
        GpsProvider.Eta eta =
                estimate(
                        trip,
                        position,
                        onboard
                                ? result.getBigDecimal("dropoff_lat")
                                : result.getBigDecimal("pickup_lat"),
                        onboard
                                ? result.getBigDecimal("dropoff_lon")
                                : result.getBigDecimal("pickup_lon"));
        String studentName = result.getString("first_name") + " " + result.getString("last_name");
        String message =
                dropoff != null
                        ? studentName + " est descendu du bus."
                        : pickup != null
                                ? studentName + " est monté dans le bus."
                                : "Aucun pointage bus pour " + studentName + ".";
        return new FamilyTripView(
                result.getObject("subscription_id", UUID.class),
                studentId,
                studentName,
                routeId,
                result.getString("route_name"),
                result.getString("pickup_name"),
                result.getString("dropoff_name"),
                result.getBoolean("morning_enabled"),
                result.getBoolean("evening_enabled"),
                trip,
                pickup,
                dropoff,
                onboard,
                message,
                position,
                eta);
    }

    private TripSummary latestTrip(UUID tenantId, UUID routeId) {
        List<TripSummary> values =
                jdbc.query(
                        "SELECT t.id,t.status,t.gps_provider_key,a.direction,v.code AS"
                            + " vehicle_code, v.registration_number,t.started_at,t.completed_at"
                            + " FROM transport_trip t JOIN transport_route_assignment a ON"
                            + " a.id=t.assignment_id JOIN transport_vehicle v ON v.id=a.vehicle_id"
                            + " WHERE t.tenant_id=? AND a.route_id=? ORDER BY t.created_at DESC"
                            + " FETCH FIRST 1 ROW ONLY",
                        (row, index) ->
                                new TripSummary(
                                        row.getObject("id", UUID.class),
                                        row.getString("status"),
                                        row.getString("direction"),
                                        row.getString("vehicle_code"),
                                        row.getString("registration_number"),
                                        row.getString("gps_provider_key"),
                                        instant(row, "started_at"),
                                        instant(row, "completed_at")),
                        tenantId,
                        routeId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private EventSummary event(UUID tenantId, UUID tripId, UUID studentId, boolean pickup) {
        String table = pickup ? "transport_pickup_event" : "transport_dropoff_event";
        List<EventSummary> values =
                jdbc.query(
                        "SELECT e.id,e.occurred_at,e.source,s.name AS stop_name FROM "
                                + table
                                + " e JOIN transport_stop s ON s.id=e.stop_id"
                                + " WHERE e.tenant_id=? AND e.trip_id=? AND e.student_id=?",
                        (result, row) ->
                                new EventSummary(
                                        result.getObject("id", UUID.class),
                                        result.getString("stop_name"),
                                        result.getString("source"),
                                        instant(result, "occurred_at")),
                        tenantId,
                        tripId,
                        studentId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private GpsProvider.Eta estimate(
            TripSummary trip, PositionView position, BigDecimal stopLat, BigDecimal stopLon) {
        if (trip == null || position == null) {
            return new GpsProvider.Eta(
                    false, null, trip == null ? null : trip.gpsProviderKey(), clock.instant());
        }
        GpsProvider provider = gpsProviders.get(trip.gpsProviderKey());
        if (provider == null) {
            return new GpsProvider.Eta(false, null, trip.gpsProviderKey(), clock.instant());
        }
        return provider.estimate(
                new GpsProvider.EtaRequest(
                        trip.id(),
                        position.latitude(),
                        position.longitude(),
                        stopLat,
                        stopLon,
                        position.recordedAt()));
    }

    private List<VehicleView> vehicles(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,registration_number,make_model,capacity,status,campus_id"
                        + " FROM transport_vehicle WHERE tenant_id=? ORDER BY code",
                (result, row) ->
                        new VehicleView(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("registration_number"),
                                result.getString("make_model"),
                                result.getInt("capacity"),
                                result.getString("status"),
                                result.getObject("campus_id", UUID.class)),
                tenantId);
    }

    private List<PersonnelView> drivers(UUID tenantId) {
        return personnel(tenantId, "transport_driver", true);
    }

    private List<PersonnelView> assistants(UUID tenantId) {
        return personnel(tenantId, "transport_assistant", false);
    }

    private List<PersonnelView> personnel(UUID tenantId, String table, boolean license) {
        return jdbc.query(
                "SELECT id,display_name,phone,"
                        + (license ? "license_number" : "CAST(NULL AS VARCHAR) AS license_number")
                        + ",active FROM "
                        + table
                        + " WHERE tenant_id=? ORDER BY display_name",
                (result, row) ->
                        new PersonnelView(
                                result.getObject("id", UUID.class),
                                result.getString("display_name"),
                                result.getString("phone"),
                                result.getString("license_number"),
                                result.getBoolean("active")),
                tenantId);
    }

    private List<RouteView> routes(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,name,description,campus_id,active FROM transport_route"
                        + " WHERE tenant_id=? ORDER BY code",
                (result, row) ->
                        new RouteView(
                                result.getObject("id", UUID.class),
                                result.getString("code"),
                                result.getString("name"),
                                result.getString("description"),
                                result.getObject("campus_id", UUID.class),
                                result.getBoolean("active"),
                                stops(tenantId, result.getObject("id", UUID.class))),
                tenantId);
    }

    private List<StopView> stops(UUID tenantId, UUID routeId) {
        return jdbc.query(
                "SELECT id,sequence_number,name,address,latitude,longitude,planned_offset_minutes"
                        + " FROM transport_stop WHERE tenant_id=? AND route_id=? AND active=TRUE"
                        + " ORDER BY sequence_number",
                (result, row) ->
                        new StopView(
                                result.getObject("id", UUID.class),
                                result.getInt("sequence_number"),
                                result.getString("name"),
                                result.getString("address"),
                                result.getBigDecimal("latitude"),
                                result.getBigDecimal("longitude"),
                                result.getInt("planned_offset_minutes")),
                tenantId,
                routeId);
    }

    private List<AssignmentView> assignments(UUID tenantId) {
        return jdbc.query(
                "SELECT a.id,a.route_id,r.name AS route_name,a.vehicle_id,v.code AS vehicle_code,"
                        + " a.driver_id,d.display_name AS driver_name,a.assistant_id,"
                        + " assistant.display_name AS assistant_name,a.service_date,a.direction,"
                        + " a.planned_departure_at,a.status FROM transport_route_assignment a"
                        + " JOIN transport_route r ON r.id=a.route_id"
                        + " JOIN transport_vehicle v ON v.id=a.vehicle_id"
                        + " JOIN transport_driver d ON d.id=a.driver_id"
                        + " LEFT JOIN transport_assistant assistant ON assistant.id=a.assistant_id"
                        + " WHERE a.tenant_id=? ORDER BY a.service_date DESC,a.direction",
                (result, row) -> assignmentView(result),
                tenantId);
    }

    private AssignmentView assignmentView(ResultSet result) throws SQLException {
        return new AssignmentView(
                result.getObject("id", UUID.class),
                result.getObject("route_id", UUID.class),
                result.getString("route_name"),
                result.getObject("vehicle_id", UUID.class),
                result.getString("vehicle_code"),
                result.getObject("driver_id", UUID.class),
                result.getString("driver_name"),
                result.getObject("assistant_id", UUID.class),
                result.getString("assistant_name"),
                result.getObject("service_date", LocalDate.class),
                result.getString("direction"),
                instant(result, "planned_departure_at"),
                result.getString("status"));
    }

    private List<SubscriptionView> subscriptions(UUID tenantId) {
        return jdbc.query(
                "SELECT sub.id,sub.student_id,p.first_name,p.last_name,sub.route_id,r.name AS"
                    + " route_name, pickup.id AS pickup_id,pickup.name AS pickup_name,dropoff.id AS"
                    + " dropoff_id, dropoff.name AS"
                    + " dropoff_name,sub.morning_enabled,sub.evening_enabled,"
                    + " sub.starts_on,sub.ends_on,sub.status FROM transport_student_subscription"
                    + " sub JOIN student_profile sp ON sp.id=sub.student_id JOIN person p ON"
                    + " p.id=sp.person_id JOIN transport_route r ON r.id=sub.route_id JOIN"
                    + " transport_stop pickup ON pickup.id=sub.pickup_stop_id JOIN transport_stop"
                    + " dropoff ON dropoff.id=sub.dropoff_stop_id WHERE sub.tenant_id=? ORDER BY"
                    + " p.first_name,p.last_name",
                (result, row) ->
                        new SubscriptionView(
                                result.getObject("id", UUID.class),
                                result.getObject("student_id", UUID.class),
                                result.getString("first_name")
                                        + " "
                                        + result.getString("last_name"),
                                result.getObject("route_id", UUID.class),
                                result.getString("route_name"),
                                result.getObject("pickup_id", UUID.class),
                                result.getString("pickup_name"),
                                result.getObject("dropoff_id", UUID.class),
                                result.getString("dropoff_name"),
                                result.getBoolean("morning_enabled"),
                                result.getBoolean("evening_enabled"),
                                result.getObject("starts_on", LocalDate.class),
                                result.getObject("ends_on", LocalDate.class),
                                result.getString("status")),
                tenantId);
    }

    private List<TripView> trips(UUID tenantId) {
        return jdbc.query(
                "SELECT"
                    + " t.id,t.assignment_id,t.status,t.gps_provider_key,t.started_at,t.completed_at,"
                    + " a.direction,r.name AS route_name,v.id AS vehicle_id,v.code AS vehicle_code,"
                    + " v.capacity,d.display_name AS driver_name FROM transport_trip t JOIN"
                    + " transport_route_assignment a ON a.id=t.assignment_id JOIN transport_route r"
                    + " ON r.id=a.route_id JOIN transport_vehicle v ON v.id=a.vehicle_id JOIN"
                    + " transport_driver d ON d.id=a.driver_id WHERE t.tenant_id=? ORDER BY"
                    + " t.created_at DESC",
                (result, row) -> tripView(result, tenantId),
                tenantId);
    }

    private TripView trip(UUID tenantId, UUID tripId) {
        return trips(tenantId).stream()
                .filter(value -> value.id().equals(tripId))
                .findFirst()
                .orElseThrow(() -> new TransportNotFoundException("Trajet introuvable"));
    }

    private TripView tripView(ResultSet result, UUID tenantId) throws SQLException {
        UUID id = result.getObject("id", UUID.class);
        return new TripView(
                id,
                result.getObject("assignment_id", UUID.class),
                result.getString("route_name"),
                result.getString("direction"),
                result.getObject("vehicle_id", UUID.class),
                result.getString("vehicle_code"),
                result.getInt("capacity"),
                result.getString("driver_name"),
                result.getString("status"),
                result.getString("gps_provider_key"),
                instant(result, "started_at"),
                instant(result, "completed_at"),
                onboard(tenantId, id),
                pickupEvents(tenantId, id),
                dropoffEvents(tenantId, id),
                latestPosition(tenantId, id));
    }

    private List<StudentEventView> pickupEvents(UUID tenantId, UUID tripId) {
        return studentEvents(tenantId, tripId, true);
    }

    private List<StudentEventView> dropoffEvents(UUID tenantId, UUID tripId) {
        return studentEvents(tenantId, tripId, false);
    }

    private List<StudentEventView> studentEvents(UUID tenantId, UUID tripId, boolean pickup) {
        String table = pickup ? "transport_pickup_event" : "transport_dropoff_event";
        return jdbc.query(
                "SELECT e.id,e.student_id,p.first_name,p.last_name,s.name AS stop_name,"
                        + " e.source,e.occurred_at FROM "
                        + table
                        + " e JOIN student_profile sp ON sp.id=e.student_id JOIN person p ON"
                        + " p.id=sp.person_id JOIN transport_stop s ON s.id=e.stop_id WHERE"
                        + " e.tenant_id=? AND e.trip_id=? ORDER BY e.occurred_at",
                (result, row) ->
                        new StudentEventView(
                                result.getObject("id", UUID.class),
                                result.getObject("student_id", UUID.class),
                                result.getString("first_name")
                                        + " "
                                        + result.getString("last_name"),
                                result.getString("stop_name"),
                                result.getString("source"),
                                instant(result, "occurred_at")),
                tenantId,
                tripId);
    }

    private PositionView latestPosition(UUID tenantId, UUID tripId) {
        List<PositionView> values =
                jdbc.query(
                        "SELECT"
                            + " id,provider_key,latitude,longitude,speed_kph,accuracy_meters,recorded_at"
                            + " FROM transport_vehicle_position WHERE tenant_id=? AND trip_id=?"
                            + " ORDER BY recorded_at DESC FETCH FIRST 1 ROW ONLY",
                        (result, row) ->
                                new PositionView(
                                        result.getObject("id", UUID.class),
                                        result.getString("provider_key"),
                                        result.getBigDecimal("latitude"),
                                        result.getBigDecimal("longitude"),
                                        result.getBigDecimal("speed_kph"),
                                        result.getBigDecimal("accuracy_meters"),
                                        instant(result, "recorded_at")),
                        tenantId,
                        tripId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private List<StudentOption> students(UUID tenantId) {
        return jdbc.query(
                "SELECT sp.id,p.first_name,p.last_name FROM student_profile sp JOIN person p ON"
                        + " p.id=sp.person_id WHERE sp.tenant_id=? AND sp.status='ACTIVE' ORDER BY"
                        + " p.first_name,p.last_name",
                (result, row) ->
                        new StudentOption(
                                result.getObject("id", UUID.class),
                                result.getString("first_name")
                                        + " "
                                        + result.getString("last_name")),
                tenantId);
    }

    private TripContext tripContext(UUID tenantId, UUID tripId) {
        List<TripContext> values =
                jdbc.query(
                        "SELECT t.id,a.route_id,a.direction,v.id AS vehicle_id,v.capacity,t.status,"
                            + " t.gps_provider_key FROM transport_trip t JOIN"
                            + " transport_route_assignment a ON a.id=t.assignment_id JOIN"
                            + " transport_vehicle v ON v.id=a.vehicle_id WHERE t.tenant_id=? AND"
                            + " t.id=? AND t.status IN ('BOARDING','IN_PROGRESS')",
                        (result, row) ->
                                new TripContext(
                                        result.getObject("id", UUID.class),
                                        result.getObject("route_id", UUID.class),
                                        result.getString("direction"),
                                        result.getObject("vehicle_id", UUID.class),
                                        result.getInt("capacity"),
                                        result.getString("status"),
                                        result.getString("gps_provider_key")),
                        tenantId,
                        tripId);
        if (values.isEmpty()) throw new TransportNotFoundException("Trajet actif introuvable");
        return values.getFirst();
    }

    private SubscriptionContext subscriptionContext(UUID tenantId, UUID subscriptionId) {
        List<SubscriptionContext> values =
                jdbc.query(
                        "SELECT id,student_id,route_id,pickup_stop_id,dropoff_stop_id,"
                            + " morning_enabled,evening_enabled FROM transport_student_subscription"
                            + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                        (result, row) ->
                                new SubscriptionContext(
                                        result.getObject("id", UUID.class),
                                        result.getObject("student_id", UUID.class),
                                        result.getObject("route_id", UUID.class),
                                        result.getObject("pickup_stop_id", UUID.class),
                                        result.getObject("dropoff_stop_id", UUID.class),
                                        result.getBoolean("morning_enabled"),
                                        result.getBoolean("evening_enabled")),
                        tenantId,
                        subscriptionId);
        if (values.isEmpty()) throw new TransportNotFoundException("Abonnement introuvable");
        return values.getFirst();
    }

    private static void validateTripSubscription(
            TripContext trip, SubscriptionContext subscription) {
        if (!trip.routeId().equals(subscription.routeId())) {
            throw new TransportValidationException("L'abonnement appartient à un autre circuit");
        }
        if (("MORNING".equals(trip.direction()) && !subscription.morningEnabled())
                || ("EVENING".equals(trip.direction()) && !subscription.eveningEnabled())) {
            throw new TransportValidationException("Abonnement inactif pour ce sens");
        }
    }

    private int onboard(UUID tenantId, UUID tripId) {
        Integer value =
                jdbc.queryForObject(
                        "SELECT (SELECT count(*) FROM transport_pickup_event"
                                + " WHERE tenant_id=? AND trip_id=?) -"
                                + " (SELECT count(*) FROM transport_dropoff_event"
                                + " WHERE tenant_id=? AND trip_id=?)",
                        Integer.class,
                        tenantId,
                        tripId,
                        tenantId,
                        tripId);
        return value == null ? 0 : value;
    }

    private void validateStop(UUID tenantId, UUID routeId, UUID stopId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM transport_stop"
                                + " WHERE tenant_id=? AND route_id=? AND id=? AND active=TRUE",
                        Integer.class,
                        tenantId,
                        routeId,
                        stopId);
        if (count == null || count == 0) {
            throw new TransportValidationException("Arrêt incompatible avec le circuit");
        }
    }

    private void requireExists(String table, UUID tenantId, UUID id, String message) {
        if (!List.of("student_profile", "transport_route").contains(table)) {
            throw new IllegalArgumentException("Table de référence invalide");
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        id);
        if (count == null || count == 0) throw new TransportNotFoundException(message);
    }

    private void requireEntity(String table, UUID tenantId, UUID id, String message) {
        if (!List.of(
                        "transport_route",
                        "transport_vehicle",
                        "transport_driver",
                        "transport_assistant")
                .contains(table)) {
            throw new IllegalArgumentException("Table transport invalide");
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        id);
        if (count == null || count == 0) throw new TransportNotFoundException(message);
    }

    private void audit(
            SchoolPrincipal actor, String action, String type, UUID id, String ipAddress) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                type,
                id.toString(),
                ipAddress,
                Map.of(),
                null,
                Map.of("id", id));
    }

    private static void require(SchoolPrincipal actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission requise : " + permission);
        }
    }

    private static void requireAny(SchoolPrincipal actor, String first, String second) {
        if (actor == null
                || (!actor.permissions().contains(first)
                        && !actor.permissions().contains(second))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Permission transport requise");
        }
    }

    private static UUID required(UUID value, String message) {
        if (value == null) throw new TransportValidationException(message);
        return value;
    }

    private static String required(String value, String message, int maximum) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw new TransportValidationException(message);
        }
        return normalized;
    }

    private static String optional(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maximum)
            throw new TransportValidationException("Valeur trop longue");
        return normalized;
    }

    private static String code(String value) {
        String normalized =
                value == null
                        ? ""
                        : value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "-");
        if (normalized.isBlank() || normalized.length() > 60) {
            throw new TransportValidationException("Code invalide");
        }
        return normalized;
    }

    private static String source(String value) {
        String normalized = value == null ? "MANUAL" : value.toUpperCase(Locale.ROOT);
        if (!List.of("MANUAL", "QR", "NFC").contains(normalized)) {
            throw new TransportValidationException("Source de pointage invalide");
        }
        return normalized;
    }

    private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null
                || longitude == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new TransportValidationException("Coordonnées GPS invalides");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record TripContext(
            UUID id,
            UUID routeId,
            String direction,
            UUID vehicleId,
            int capacity,
            String status,
            String gpsProviderKey) {}

    private record SubscriptionContext(
            UUID id,
            UUID studentId,
            UUID routeId,
            UUID pickupStopId,
            UUID dropoffStopId,
            boolean morningEnabled,
            boolean eveningEnabled) {}

    public record VehicleCommand(
            UUID campusId,
            String code,
            String registrationNumber,
            String makeModel,
            Integer capacity) {}

    public record StopCommand(
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer plannedOffsetMinutes) {}

    public record RouteCommand(
            UUID campusId, String code, String name, String description, List<StopCommand> stops) {}

    public record SubscriptionCommand(
            UUID studentId,
            UUID routeId,
            UUID pickupStopId,
            UUID dropoffStopId,
            boolean morningEnabled,
            boolean eveningEnabled,
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record StartTripCommand(UUID assignmentId, String gpsProviderKey) {}

    public record AssignmentCommand(
            UUID routeId,
            UUID vehicleId,
            UUID driverId,
            UUID assistantId,
            LocalDate serviceDate,
            String direction,
            Instant plannedDepartureAt) {}

    public record AttendanceCommand(UUID subscriptionId, String source) {}

    public record PositionCommand(
            String providerKey,
            String externalPositionId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKph,
            BigDecimal accuracyMeters,
            Instant recordedAt) {}

    public record VehicleView(
            UUID id,
            String code,
            String registrationNumber,
            String makeModel,
            int capacity,
            String status,
            UUID campusId) {}

    public record PersonnelView(
            UUID id, String displayName, String phone, String licenseNumber, boolean active) {}

    public record StopView(
            UUID id,
            int sequence,
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            int plannedOffsetMinutes) {}

    public record RouteView(
            UUID id,
            String code,
            String name,
            String description,
            UUID campusId,
            boolean active,
            List<StopView> stops) {}

    public record AssignmentView(
            UUID id,
            UUID routeId,
            String routeName,
            UUID vehicleId,
            String vehicleCode,
            UUID driverId,
            String driverName,
            UUID assistantId,
            String assistantName,
            LocalDate serviceDate,
            String direction,
            Instant plannedDepartureAt,
            String status) {}

    public record SubscriptionView(
            UUID id,
            UUID studentId,
            String studentName,
            UUID routeId,
            String routeName,
            UUID pickupStopId,
            String pickupStopName,
            UUID dropoffStopId,
            String dropoffStopName,
            boolean morningEnabled,
            boolean eveningEnabled,
            LocalDate startsOn,
            LocalDate endsOn,
            String status) {}

    public record StudentOption(UUID id, String displayName) {}

    public record StudentEventView(
            UUID id,
            UUID studentId,
            String studentName,
            String stopName,
            String source,
            Instant occurredAt) {}

    public record PositionView(
            UUID id,
            String providerKey,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKph,
            BigDecimal accuracyMeters,
            Instant recordedAt) {}

    public record TripView(
            UUID id,
            UUID assignmentId,
            String routeName,
            String direction,
            UUID vehicleId,
            String vehicleCode,
            int capacity,
            String driverName,
            String status,
            String gpsProviderKey,
            Instant startedAt,
            Instant completedAt,
            int onboardCount,
            List<StudentEventView> pickupEvents,
            List<StudentEventView> dropoffEvents,
            PositionView latestPosition) {}

    public record TripSummary(
            UUID id,
            String status,
            String direction,
            String vehicleCode,
            String registrationNumber,
            String gpsProviderKey,
            Instant startedAt,
            Instant completedAt) {}

    public record EventSummary(UUID id, String stopName, String source, Instant occurredAt) {}

    public record FamilyTripView(
            UUID subscriptionId,
            UUID studentId,
            String studentName,
            UUID routeId,
            String routeName,
            String pickupStopName,
            String dropoffStopName,
            boolean morningEnabled,
            boolean eveningEnabled,
            TripSummary latestTrip,
            EventSummary pickup,
            EventSummary dropoff,
            boolean onboard,
            String message,
            PositionView position,
            GpsProvider.Eta eta) {}

    public record TransportDashboard(
            Instant generatedAt,
            List<VehicleView> vehicles,
            List<PersonnelView> drivers,
            List<PersonnelView> assistants,
            List<RouteView> routes,
            List<AssignmentView> assignments,
            List<SubscriptionView> subscriptions,
            List<TripView> trips,
            List<StudentOption> students,
            List<String> gpsProviders) {}

    public record FamilyTransportDashboard(
            Instant generatedAt,
            String parentName,
            List<FamilyTripView> children,
            List<String> gpsProviders) {}
}
