package ma.schoolz.erp.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
public class InventoryService {
    private static final Set<String> LOCATION_TYPES =
            Set.of("CAMPUS", "BUILDING", "ROOM", "WAREHOUSE", "LIBRARY", "OFFICE", "OTHER");
    private static final Set<String> CATEGORIES =
            Set.of(
                    "COMPUTER",
                    "TABLET",
                    "PROJECTOR",
                    "BOOK",
                    "UNIFORM",
                    "TEACHING_MATERIAL",
                    "OTHER");
    private static final Set<String> MOVEMENT_TYPES =
            Set.of("INBOUND", "OUTBOUND", "TRANSFER", "ADJUSTMENT_IN", "ADJUSTMENT_OUT");
    private static final Set<String> CONDITIONS = Set.of("NEW", "GOOD", "FAIR", "DAMAGED");
    private static final Set<String> ASSET_STATUSES =
            Set.of("AVAILABLE", "MAINTENANCE", "LOST", "RETIRED");
    private static final Set<String> ASSIGNEE_TYPES =
            Set.of("PERSON", "CLASSROOM", "DEPARTMENT", "SERVICE", "OTHER");

    private final JdbcTemplate jdbc;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public InventoryService(JdbcTemplate jdbc, IdentityAuditTrail audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('inventory.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        UUID tenantId = actor.tenantId();
        List<LocationView> locations = locations(tenantId);
        List<BalanceView> balances = balances(tenantId);
        List<ItemView> items = items(tenantId, balances);
        List<MovementView> movements = movements(tenantId);
        List<AssetView> assets = assets(tenantId);
        List<AssignmentView> assignments = assignments(tenantId);
        List<AssetEventView> events = events(tenantId);
        long assigned = assets.stream().filter(asset -> "ASSIGNED".equals(asset.status())).count();
        long maintenance =
                assets.stream().filter(asset -> "MAINTENANCE".equals(asset.status())).count();
        long lowStock = items.stream().filter(ItemView::lowStock).count();
        return new Dashboard(
                clock.instant(),
                new Statistics(
                        assets.size(),
                        assigned,
                        maintenance,
                        items.size(),
                        lowStock,
                        movements.size()),
                locations,
                items,
                balances,
                movements,
                assets,
                assignments,
                events);
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.configure')")
    public LocationView createLocation(
            SchoolPrincipal actor, LocationCommand command, String ipAddress) {
        if (command == null) {
            throw new InventoryValidationException("L’emplacement est requis");
        }
        UUID id = UUID.randomUUID();
        UUID tenantId = actor.tenantId();
        if (command.campusId() != null) {
            ensureExists(
                    "SELECT count(*) FROM school_campus WHERE tenant_id=? AND id=?",
                    tenantId,
                    command.campusId(),
                    "Campus introuvable");
        }
        if (command.parentLocationId() != null) {
            ensureLocation(tenantId, command.parentLocationId());
        }
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO inventory_location"
                    + " (id,tenant_id,campus_id,parent_location_id,code,name,location_type,description,active,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,TRUE,?,?)",
                id,
                tenantId,
                command.campusId(),
                command.parentLocationId(),
                required(command.code(), 50, "Le code").toUpperCase(Locale.ROOT),
                required(command.name(), 180, "Le nom"),
                enumValue(command.locationType(), LOCATION_TYPES, "Le type d’emplacement"),
                optional(command.description(), 500),
                timestamp(now),
                timestamp(now));
        LocationView result = location(tenantId, id);
        audit(actor, "inventory.location.created", "InventoryLocation", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.write')")
    public ItemView createItem(SchoolPrincipal actor, ItemCommand command, String ipAddress) {
        if (command == null) {
            throw new InventoryValidationException("L’article est requis");
        }
        BigDecimal minimum = nonNegativeQuantity(command.minimumStock(), "Le stock minimum");
        BigDecimal opening = nonNegativeQuantity(command.openingQuantity(), "Le stock initial");
        if (opening.signum() > 0 && command.openingLocationId() == null) {
            throw new InventoryValidationException(
                    "Un emplacement est requis pour le stock initial");
        }
        if (command.openingLocationId() != null) {
            ensureLocation(actor.tenantId(), command.openingLocationId());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO inventory_item"
                    + " (id,tenant_id,sku,name,category,unit,minimum_stock,description,active,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,TRUE,?,?)",
                id,
                actor.tenantId(),
                required(command.sku(), 70, "Le SKU").toUpperCase(Locale.ROOT),
                required(command.name(), 180, "Le nom"),
                enumValue(command.category(), CATEGORIES, "La catégorie"),
                required(command.unit(), 30, "L’unité"),
                minimum,
                optional(command.description(), 700),
                timestamp(now),
                timestamp(now));
        if (opening.signum() > 0) {
            addBalance(actor.tenantId(), id, command.openingLocationId(), opening, now);
            insertMovement(
                    actor,
                    id,
                    null,
                    command.openingLocationId(),
                    "INBOUND",
                    opening,
                    command.openingDate() == null ? LocalDate.now(clock) : command.openingDate(),
                    "STOCK-INITIAL",
                    "Stock initial de l’article");
        }
        ItemView result = item(actor.tenantId(), id);
        audit(actor, "inventory.item.created", "InventoryItem", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.adjust')")
    public MovementView recordMovement(
            SchoolPrincipal actor, UUID itemId, MovementCommand command, String ipAddress) {
        if (command == null || command.occurredOn() == null) {
            throw new InventoryValidationException("Le mouvement et sa date sont requis");
        }
        ensureItem(actor.tenantId(), itemId);
        String type = enumValue(command.movementType(), MOVEMENT_TYPES, "Le type de mouvement");
        BigDecimal quantity = positiveQuantity(command.quantity());
        validateMovementLocations(
                actor.tenantId(), type, command.fromLocationId(), command.toLocationId());
        Instant now = clock.instant();
        if (command.fromLocationId() != null) {
            subtractBalance(actor.tenantId(), itemId, command.fromLocationId(), quantity, now);
        }
        if (command.toLocationId() != null) {
            addBalance(actor.tenantId(), itemId, command.toLocationId(), quantity, now);
        }
        UUID id =
                insertMovement(
                        actor,
                        itemId,
                        command.fromLocationId(),
                        command.toLocationId(),
                        type,
                        quantity,
                        command.occurredOn(),
                        optional(command.reference(), 120),
                        required(command.reason(), 700, "Le motif"));
        MovementView result = movement(actor.tenantId(), id);
        audit(actor, "inventory.stock.moved", "StockMovement", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.write')")
    public AssetView createAsset(SchoolPrincipal actor, AssetCommand command, String ipAddress) {
        if (command == null || command.locationId() == null) {
            throw new InventoryValidationException("L’actif et son emplacement sont requis");
        }
        ensureLocation(actor.tenantId(), command.locationId());
        BigDecimal value =
                command.acquisitionValue() == null
                        ? null
                        : nonNegativeMoney(command.acquisitionValue(), "La valeur d’acquisition");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String condition = enumValue(command.condition(), CONDITIONS, "L’état physique");
        jdbc.update(
                "INSERT INTO inventory_asset"
                    + " (id,tenant_id,current_location_id,asset_tag,name,category,brand,model,serial_number,acquired_on,acquisition_value,currency,condition_status,status,notes,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'AVAILABLE',?,?,?)",
                id,
                actor.tenantId(),
                command.locationId(),
                required(command.assetTag(), 70, "Le numéro d’inventaire").toUpperCase(Locale.ROOT),
                required(command.name(), 180, "Le nom"),
                enumValue(command.category(), CATEGORIES, "La catégorie"),
                optional(command.brand(), 100),
                optional(command.model(), 120),
                optional(command.serialNumber(), 140),
                command.acquiredOn(),
                value,
                currency(command.currency()),
                condition,
                optional(command.notes(), 700),
                timestamp(now),
                timestamp(now));
        appendAssetEvent(
                actor,
                id,
                null,
                "REGISTERED",
                command.acquiredOn() == null ? LocalDate.now(clock) : command.acquiredOn(),
                null,
                "AVAILABLE",
                command.locationId(),
                "Actif enregistré dans l’inventaire");
        AssetView result = asset(actor.tenantId(), id);
        audit(actor, "inventory.asset.created", "Asset", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.assign')")
    public AssignmentView assignAsset(
            SchoolPrincipal actor, UUID assetId, AssignmentCommand command, String ipAddress) {
        if (command == null || command.assignedOn() == null) {
            throw new InventoryValidationException("L’affectation et sa date sont requises");
        }
        AssetView before = asset(actor.tenantId(), assetId);
        if (!"AVAILABLE".equals(before.status())) {
            throw new InventoryConflictException("Seul un actif disponible peut être affecté");
        }
        if (command.dueOn() != null && command.dueOn().isBefore(command.assignedOn())) {
            throw new InventoryValidationException(
                    "La date de retour prévue précède l’affectation");
        }
        if (command.destinationLocationId() != null) {
            ensureLocation(actor.tenantId(), command.destinationLocationId());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String condition = enumValue(command.conditionOut(), CONDITIONS, "L’état au départ");
        jdbc.update(
                "INSERT INTO inventory_asset_assignment"
                    + " (id,tenant_id,asset_id,destination_location_id,assignee_type,assignee_reference,assignee_label,assigned_on,due_on,returned_on,condition_out,condition_in,note,assigned_by_user_id,returned_by_user_id,status,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,NULL,?,NULL,?,?,NULL,'ACTIVE',?,?)",
                id,
                actor.tenantId(),
                assetId,
                command.destinationLocationId(),
                enumValue(command.assigneeType(), ASSIGNEE_TYPES, "Le type de bénéficiaire"),
                optional(command.assigneeReference(), 100),
                required(command.assigneeLabel(), 180, "Le bénéficiaire"),
                command.assignedOn(),
                command.dueOn(),
                condition,
                optional(command.note(), 700),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "UPDATE inventory_asset SET"
                        + " status='ASSIGNED',condition_status=?,current_location_id=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=? AND status='AVAILABLE'",
                condition,
                command.destinationLocationId() == null
                        ? before.locationId()
                        : command.destinationLocationId(),
                timestamp(now),
                actor.tenantId(),
                assetId);
        appendAssetEvent(
                actor,
                assetId,
                id,
                "ASSIGNED",
                command.assignedOn(),
                "AVAILABLE",
                "ASSIGNED",
                command.destinationLocationId() == null
                        ? before.locationId()
                        : command.destinationLocationId(),
                "Affecté à " + command.assigneeLabel());
        AssignmentView result = assignment(actor.tenantId(), id);
        audit(actor, "inventory.asset.assigned", "AssetAssignment", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.assign')")
    public AssignmentView returnAsset(
            SchoolPrincipal actor, UUID assignmentId, ReturnCommand command, String ipAddress) {
        if (command == null || command.returnedOn() == null || command.returnLocationId() == null) {
            throw new InventoryValidationException(
                    "La date, l’emplacement et l’état de retour sont requis");
        }
        AssignmentView before = assignment(actor.tenantId(), assignmentId);
        if (!"ACTIVE".equals(before.status())) {
            throw new InventoryConflictException("Cette affectation est déjà terminée");
        }
        if (command.returnedOn().isBefore(before.assignedOn())) {
            throw new InventoryValidationException("Le retour précède l’affectation");
        }
        ensureLocation(actor.tenantId(), command.returnLocationId());
        String condition = enumValue(command.conditionIn(), CONDITIONS, "L’état au retour");
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE inventory_asset_assignment SET"
                    + " returned_on=?,condition_in=?,note=?,returned_by_user_id=?,status='RETURNED',updated_at=?"
                    + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                command.returnedOn(),
                condition,
                optional(command.note(), 700),
                actor.accountId(),
                timestamp(now),
                actor.tenantId(),
                assignmentId);
        jdbc.update(
                "UPDATE inventory_asset SET"
                    + " status='AVAILABLE',condition_status=?,current_location_id=?,updated_at=?"
                    + " WHERE tenant_id=? AND id=?",
                condition,
                command.returnLocationId(),
                timestamp(now),
                actor.tenantId(),
                before.assetId());
        appendAssetEvent(
                actor,
                before.assetId(),
                assignmentId,
                "RETURNED",
                command.returnedOn(),
                "ASSIGNED",
                "AVAILABLE",
                command.returnLocationId(),
                "Actif restitué par " + before.assigneeLabel());
        AssignmentView result = assignment(actor.tenantId(), assignmentId);
        audit(
                actor,
                "inventory.asset.returned",
                "AssetAssignment",
                assignmentId,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('inventory.write')")
    public AssetView changeAssetStatus(
            SchoolPrincipal actor, UUID assetId, AssetStatusCommand command, String ipAddress) {
        if (command == null || command.effectiveOn() == null) {
            throw new InventoryValidationException("Le nouvel état et sa date sont requis");
        }
        AssetView before = asset(actor.tenantId(), assetId);
        if ("ASSIGNED".equals(before.status())) {
            throw new InventoryConflictException(
                    "Un actif affecté doit être restitué avant un changement de statut");
        }
        String status = enumValue(command.status(), ASSET_STATUSES, "Le statut");
        String condition =
                command.condition() == null
                        ? before.condition()
                        : enumValue(command.condition(), CONDITIONS, "L’état physique");
        UUID locationId = command.locationId() == null ? before.locationId() : command.locationId();
        if (locationId != null) ensureLocation(actor.tenantId(), locationId);
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE inventory_asset SET"
                        + " status=?,condition_status=?,current_location_id=?,notes=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                status,
                condition,
                locationId,
                optional(command.note(), 700),
                timestamp(now),
                actor.tenantId(),
                assetId);
        appendAssetEvent(
                actor,
                assetId,
                null,
                "STATUS_CHANGED",
                command.effectiveOn(),
                before.status(),
                status,
                locationId,
                required(command.note(), 700, "Le motif"));
        AssetView result = asset(actor.tenantId(), assetId);
        audit(actor, "inventory.asset.status.changed", "Asset", assetId, ipAddress, result);
        return result;
    }

    private void validateMovementLocations(
            UUID tenantId, String type, UUID fromLocationId, UUID toLocationId) {
        if (("INBOUND".equals(type) || "ADJUSTMENT_IN".equals(type))
                && (fromLocationId != null || toLocationId == null)) {
            throw new InventoryValidationException(
                    "Une entrée exige uniquement un emplacement de destination");
        }
        if (("OUTBOUND".equals(type) || "ADJUSTMENT_OUT".equals(type))
                && (fromLocationId == null || toLocationId != null)) {
            throw new InventoryValidationException(
                    "Une sortie exige uniquement un emplacement source");
        }
        if ("TRANSFER".equals(type)
                && (fromLocationId == null
                        || toLocationId == null
                        || fromLocationId.equals(toLocationId))) {
            throw new InventoryValidationException(
                    "Un transfert exige deux emplacements différents");
        }
        if (fromLocationId != null) ensureLocation(tenantId, fromLocationId);
        if (toLocationId != null) ensureLocation(tenantId, toLocationId);
    }

    private void addBalance(
            UUID tenantId, UUID itemId, UUID locationId, BigDecimal quantity, Instant now) {
        int updated =
                jdbc.update(
                        "UPDATE inventory_stock_balance SET quantity=quantity+?,updated_at=? WHERE"
                                + " tenant_id=? AND inventory_item_id=? AND location_id=?",
                        quantity,
                        timestamp(now),
                        tenantId,
                        itemId,
                        locationId);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO inventory_stock_balance"
                        + " (id,tenant_id,inventory_item_id,location_id,quantity,updated_at) VALUES"
                        + " (?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    tenantId,
                    itemId,
                    locationId,
                    quantity,
                    timestamp(now));
        }
    }

    private void subtractBalance(
            UUID tenantId, UUID itemId, UUID locationId, BigDecimal quantity, Instant now) {
        List<BigDecimal> values =
                jdbc.query(
                        "SELECT quantity FROM inventory_stock_balance WHERE tenant_id=? AND"
                                + " inventory_item_id=? AND location_id=? FOR UPDATE",
                        (resultSet, rowNumber) -> resultSet.getBigDecimal(1),
                        tenantId,
                        itemId,
                        locationId);
        BigDecimal available = values.isEmpty() ? BigDecimal.ZERO : values.getFirst();
        if (available.compareTo(quantity) < 0) {
            throw new InventoryConflictException(
                    "Stock insuffisant : "
                            + available.stripTrailingZeros().toPlainString()
                            + " disponible(s)");
        }
        jdbc.update(
                "UPDATE inventory_stock_balance SET quantity=quantity-?,updated_at=? WHERE"
                        + " tenant_id=? AND inventory_item_id=? AND location_id=?",
                quantity,
                timestamp(now),
                tenantId,
                itemId,
                locationId);
    }

    private UUID insertMovement(
            SchoolPrincipal actor,
            UUID itemId,
            UUID fromLocationId,
            UUID toLocationId,
            String type,
            BigDecimal quantity,
            LocalDate occurredOn,
            String reference,
            String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_stock_movement"
                    + " (id,tenant_id,inventory_item_id,from_location_id,to_location_id,movement_type,quantity,occurred_on,reference,reason,recorded_by_user_id,recorded_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                actor.tenantId(),
                itemId,
                fromLocationId,
                toLocationId,
                type,
                quantity,
                occurredOn,
                reference,
                reason,
                actor.accountId(),
                timestamp(clock.instant()));
        return id;
    }

    private void appendAssetEvent(
            SchoolPrincipal actor,
            UUID assetId,
            UUID assignmentId,
            String eventType,
            LocalDate eventDate,
            String fromStatus,
            String toStatus,
            UUID locationId,
            String detail) {
        jdbc.update(
                "INSERT INTO inventory_asset_event"
                    + " (id,tenant_id,asset_id,assignment_id,event_type,event_date,from_status,to_status,location_id,detail,recorded_by_user_id,recorded_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.tenantId(),
                assetId,
                assignmentId,
                eventType,
                eventDate,
                fromStatus,
                toStatus,
                locationId,
                detail,
                actor.accountId(),
                timestamp(clock.instant()));
    }

    private List<LocationView> locations(UUID tenantId) {
        return jdbc.query(
                "SELECT l.*,c.name campus_name,p.name parent_name,(SELECT count(*) FROM"
                    + " inventory_asset a WHERE a.tenant_id=l.tenant_id AND"
                    + " a.current_location_id=l.id) asset_count,(SELECT count(*) FROM"
                    + " inventory_stock_balance b WHERE b.tenant_id=l.tenant_id AND"
                    + " b.location_id=l.id AND b.quantity>0) stocked_item_count FROM"
                    + " inventory_location l LEFT JOIN school_campus c ON c.id=l.campus_id AND"
                    + " c.tenant_id=l.tenant_id LEFT JOIN inventory_location p ON"
                    + " p.id=l.parent_location_id AND p.tenant_id=l.tenant_id WHERE l.tenant_id=?"
                    + " ORDER BY l.name",
                this::mapLocation,
                tenantId);
    }

    private LocationView location(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT l.*,c.name campus_name,p.name parent_name,(SELECT count(*) FROM"
                            + " inventory_asset a WHERE a.tenant_id=l.tenant_id AND"
                            + " a.current_location_id=l.id) asset_count,(SELECT count(*) FROM"
                            + " inventory_stock_balance b WHERE b.tenant_id=l.tenant_id AND"
                            + " b.location_id=l.id AND b.quantity>0) stocked_item_count FROM"
                            + " inventory_location l LEFT JOIN school_campus c ON c.id=l.campus_id"
                            + " AND c.tenant_id=l.tenant_id LEFT JOIN inventory_location p ON"
                            + " p.id=l.parent_location_id AND p.tenant_id=l.tenant_id WHERE"
                            + " l.tenant_id=? AND l.id=?",
                        this::mapLocation,
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InventoryNotFoundException("Emplacement introuvable"));
    }

    private LocationView mapLocation(ResultSet rs, int row) throws SQLException {
        return new LocationView(
                uuid(rs, "id"),
                nullableUuid(rs, "campus_id"),
                rs.getString("campus_name"),
                nullableUuid(rs, "parent_location_id"),
                rs.getString("parent_name"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("location_type"),
                rs.getString("description"),
                rs.getBoolean("active"),
                rs.getInt("asset_count"),
                rs.getInt("stocked_item_count"));
    }

    private List<BalanceView> balances(UUID tenantId) {
        return jdbc.query(
                "SELECT b.*,i.sku,i.name item_name,l.code location_code,l.name location_name FROM"
                    + " inventory_stock_balance b JOIN inventory_item i ON i.id=b.inventory_item_id"
                    + " AND i.tenant_id=b.tenant_id JOIN inventory_location l ON l.id=b.location_id"
                    + " AND l.tenant_id=b.tenant_id WHERE b.tenant_id=? ORDER BY i.name,l.name",
                (rs, row) ->
                        new BalanceView(
                                uuid(rs, "id"),
                                uuid(rs, "inventory_item_id"),
                                rs.getString("sku"),
                                rs.getString("item_name"),
                                uuid(rs, "location_id"),
                                rs.getString("location_code"),
                                rs.getString("location_name"),
                                quantity(rs.getBigDecimal("quantity")),
                                instant(rs, "updated_at")),
                tenantId);
    }

    private List<ItemView> items(UUID tenantId, List<BalanceView> balances) {
        return jdbc.query(
                "SELECT * FROM inventory_item WHERE tenant_id=? ORDER BY name",
                (rs, row) -> mapItem(rs, balances),
                tenantId);
    }

    private ItemView item(UUID tenantId, UUID id) {
        List<BalanceView> allBalances = balances(tenantId);
        return jdbc
                .query(
                        "SELECT * FROM inventory_item WHERE tenant_id=? AND id=?",
                        (rs, row) -> mapItem(rs, allBalances),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InventoryNotFoundException("Article introuvable"));
    }

    private ItemView mapItem(ResultSet rs, List<BalanceView> balances) throws SQLException {
        UUID id = uuid(rs, "id");
        List<BalanceView> itemBalances =
                balances.stream().filter(balance -> id.equals(balance.itemId())).toList();
        BigDecimal total =
                itemBalances.stream()
                        .map(BalanceView::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal minimum = quantity(rs.getBigDecimal("minimum_stock"));
        return new ItemView(
                id,
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("unit"),
                minimum,
                rs.getString("description"),
                rs.getBoolean("active"),
                quantity(total),
                total.compareTo(minimum) < 0,
                itemBalances);
    }

    private List<MovementView> movements(UUID tenantId) {
        return jdbc.query(
                "SELECT m.*,i.sku,i.name item_name,fl.name from_location_name,tl.name"
                    + " to_location_name,u.display_name recorded_by_name FROM"
                    + " inventory_stock_movement m JOIN inventory_item i ON"
                    + " i.id=m.inventory_item_id AND i.tenant_id=m.tenant_id LEFT JOIN"
                    + " inventory_location fl ON fl.id=m.from_location_id AND"
                    + " fl.tenant_id=m.tenant_id LEFT JOIN inventory_location tl ON"
                    + " tl.id=m.to_location_id AND tl.tenant_id=m.tenant_id JOIN"
                    + " identity_user_account u ON u.id=m.recorded_by_user_id WHERE m.tenant_id=?"
                    + " ORDER BY m.occurred_on DESC,m.recorded_at DESC",
                this::mapMovement,
                tenantId);
    }

    private MovementView movement(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT m.*,i.sku,i.name item_name,fl.name from_location_name,tl.name"
                                + " to_location_name,u.display_name recorded_by_name FROM"
                                + " inventory_stock_movement m JOIN inventory_item i ON"
                                + " i.id=m.inventory_item_id AND i.tenant_id=m.tenant_id LEFT JOIN"
                                + " inventory_location fl ON fl.id=m.from_location_id AND"
                                + " fl.tenant_id=m.tenant_id LEFT JOIN inventory_location tl ON"
                                + " tl.id=m.to_location_id AND tl.tenant_id=m.tenant_id JOIN"
                                + " identity_user_account u ON u.id=m.recorded_by_user_id WHERE"
                                + " m.tenant_id=? AND m.id=?",
                        this::mapMovement,
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InventoryNotFoundException("Mouvement introuvable"));
    }

    private MovementView mapMovement(ResultSet rs, int row) throws SQLException {
        return new MovementView(
                uuid(rs, "id"),
                uuid(rs, "inventory_item_id"),
                rs.getString("sku"),
                rs.getString("item_name"),
                nullableUuid(rs, "from_location_id"),
                rs.getString("from_location_name"),
                nullableUuid(rs, "to_location_id"),
                rs.getString("to_location_name"),
                rs.getString("movement_type"),
                quantity(rs.getBigDecimal("quantity")),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getString("reference"),
                rs.getString("reason"),
                uuid(rs, "recorded_by_user_id"),
                rs.getString("recorded_by_name"),
                instant(rs, "recorded_at"));
    }

    private List<AssetView> assets(UUID tenantId) {
        return jdbc.query(
                assetSql() + " WHERE a.tenant_id=? ORDER BY a.name,a.asset_tag",
                this::mapAsset,
                tenantId);
    }

    private AssetView asset(UUID tenantId, UUID id) {
        return jdbc
                .query(assetSql() + " WHERE a.tenant_id=? AND a.id=?", this::mapAsset, tenantId, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InventoryNotFoundException("Actif introuvable"));
    }

    private String assetSql() {
        return "SELECT a.*,l.code location_code,l.name location_name,(SELECT count(*) FROM"
                + " inventory_asset_event e WHERE e.tenant_id=a.tenant_id AND e.asset_id=a.id)"
                + " history_count,(SELECT aa.assignee_label FROM inventory_asset_assignment aa"
                + " WHERE aa.tenant_id=a.tenant_id AND aa.asset_id=a.id AND aa.status='ACTIVE')"
                + " active_assignee FROM inventory_asset a LEFT JOIN inventory_location l ON"
                + " l.id=a.current_location_id AND l.tenant_id=a.tenant_id";
    }

    private AssetView mapAsset(ResultSet rs, int row) throws SQLException {
        return new AssetView(
                uuid(rs, "id"),
                nullableUuid(rs, "current_location_id"),
                rs.getString("location_code"),
                rs.getString("location_name"),
                rs.getString("asset_tag"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("brand"),
                rs.getString("model"),
                rs.getString("serial_number"),
                rs.getObject("acquired_on", LocalDate.class),
                rs.getBigDecimal("acquisition_value"),
                rs.getString("currency"),
                rs.getString("condition_status"),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getString("active_assignee"),
                rs.getInt("history_count"));
    }

    private List<AssignmentView> assignments(UUID tenantId) {
        return jdbc.query(
                assignmentSql()
                        + " WHERE aa.tenant_id=? ORDER BY aa.assigned_on DESC,aa.created_at DESC",
                this::mapAssignment,
                tenantId);
    }

    private AssignmentView assignment(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        assignmentSql() + " WHERE aa.tenant_id=? AND aa.id=?",
                        this::mapAssignment,
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InventoryNotFoundException("Affectation introuvable"));
    }

    private String assignmentSql() {
        return "SELECT aa.*,a.asset_tag,a.name asset_name,l.name"
                + " destination_location_name,au.display_name assigned_by_name,ru.display_name"
                + " returned_by_name FROM inventory_asset_assignment aa JOIN inventory_asset a"
                + " ON a.id=aa.asset_id AND a.tenant_id=aa.tenant_id LEFT JOIN"
                + " inventory_location l ON l.id=aa.destination_location_id AND"
                + " l.tenant_id=aa.tenant_id JOIN identity_user_account au ON"
                + " au.id=aa.assigned_by_user_id LEFT JOIN identity_user_account ru ON"
                + " ru.id=aa.returned_by_user_id";
    }

    private AssignmentView mapAssignment(ResultSet rs, int row) throws SQLException {
        return new AssignmentView(
                uuid(rs, "id"),
                uuid(rs, "asset_id"),
                rs.getString("asset_tag"),
                rs.getString("asset_name"),
                nullableUuid(rs, "destination_location_id"),
                rs.getString("destination_location_name"),
                rs.getString("assignee_type"),
                rs.getString("assignee_reference"),
                rs.getString("assignee_label"),
                rs.getObject("assigned_on", LocalDate.class),
                rs.getObject("due_on", LocalDate.class),
                rs.getObject("returned_on", LocalDate.class),
                rs.getString("condition_out"),
                rs.getString("condition_in"),
                rs.getString("note"),
                rs.getString("assigned_by_name"),
                rs.getString("returned_by_name"),
                rs.getString("status"));
    }

    private List<AssetEventView> events(UUID tenantId) {
        return jdbc.query(
                "SELECT e.*,a.asset_tag,a.name asset_name,l.name location_name,u.display_name"
                    + " recorded_by_name FROM inventory_asset_event e JOIN inventory_asset a ON"
                    + " a.id=e.asset_id AND a.tenant_id=e.tenant_id LEFT JOIN inventory_location l"
                    + " ON l.id=e.location_id AND l.tenant_id=e.tenant_id JOIN"
                    + " identity_user_account u ON u.id=e.recorded_by_user_id WHERE e.tenant_id=?"
                    + " ORDER BY e.event_date DESC,e.recorded_at DESC",
                (rs, row) ->
                        new AssetEventView(
                                uuid(rs, "id"),
                                uuid(rs, "asset_id"),
                                rs.getString("asset_tag"),
                                rs.getString("asset_name"),
                                nullableUuid(rs, "assignment_id"),
                                rs.getString("event_type"),
                                rs.getObject("event_date", LocalDate.class),
                                rs.getString("from_status"),
                                rs.getString("to_status"),
                                nullableUuid(rs, "location_id"),
                                rs.getString("location_name"),
                                rs.getString("detail"),
                                rs.getString("recorded_by_name"),
                                instant(rs, "recorded_at")),
                tenantId);
    }

    private void ensureLocation(UUID tenantId, UUID id) {
        ensureExists(
                "SELECT count(*) FROM inventory_location WHERE tenant_id=? AND id=? AND"
                        + " active=TRUE",
                tenantId,
                id,
                "Emplacement introuvable ou inactif");
    }

    private void ensureItem(UUID tenantId, UUID id) {
        ensureExists(
                "SELECT count(*) FROM inventory_item WHERE tenant_id=? AND id=? AND active=TRUE",
                tenantId,
                id,
                "Article introuvable ou inactif");
    }

    private void ensureExists(String sql, UUID tenantId, UUID id, String message) {
        Integer count = jdbc.queryForObject(sql, Integer.class, tenantId, id);
        if (count == null || count == 0) throw new InventoryNotFoundException(message);
    }

    private String required(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new InventoryValidationException(label + " est requis");
        if (normalized.length() > max)
            throw new InventoryValidationException(label + " dépasse " + max + " caractères");
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max)
            throw new InventoryValidationException("Un texte dépasse " + max + " caractères");
        return normalized;
    }

    private String enumValue(String value, Set<String> values, String label) {
        String normalized = required(value, 40, label).toUpperCase(Locale.ROOT);
        if (!values.contains(normalized))
            throw new InventoryValidationException(label + " n’est pas reconnu");
        return normalized;
    }

    private String currency(String value) {
        String normalized =
                value == null || value.isBlank() ? "MAD" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}"))
            throw new InventoryValidationException(
                    "La devise doit suivre le format ISO à trois lettres");
        return normalized;
    }

    private BigDecimal nonNegativeQuantity(BigDecimal value, String label) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : quantity(value);
        if (normalized.signum() < 0)
            throw new InventoryValidationException(label + " ne peut pas être négatif");
        return normalized;
    }

    private BigDecimal positiveQuantity(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : quantity(value);
        if (normalized.signum() <= 0)
            throw new InventoryValidationException("La quantité doit être strictement positive");
        return normalized;
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String label) {
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0)
            throw new InventoryValidationException(label + " ne peut pas être négative");
        return normalized;
    }

    private BigDecimal quantity(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : rs.getObject(column, UUID.class);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private void audit(
            SchoolPrincipal actor,
            String action,
            String resourceType,
            UUID resourceId,
            String ipAddress,
            Object after) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                resourceType,
                resourceId.toString(),
                ipAddress,
                Map.of("module", "inventory"),
                null,
                after);
    }

    public record Dashboard(
            Instant generatedAt,
            Statistics statistics,
            List<LocationView> locations,
            List<ItemView> items,
            List<BalanceView> balances,
            List<MovementView> movements,
            List<AssetView> assets,
            List<AssignmentView> assignments,
            List<AssetEventView> assetHistory) {}

    public record Statistics(
            long assets,
            long assignedAssets,
            long maintenanceAssets,
            long inventoryItems,
            long lowStockItems,
            long movements) {}

    public record LocationCommand(
            UUID campusId,
            UUID parentLocationId,
            String code,
            String name,
            String locationType,
            String description) {}

    public record LocationView(
            UUID id,
            UUID campusId,
            String campusName,
            UUID parentLocationId,
            String parentLocationName,
            String code,
            String name,
            String locationType,
            String description,
            boolean active,
            int assetCount,
            int stockedItemCount) {}

    public record ItemCommand(
            String sku,
            String name,
            String category,
            String unit,
            BigDecimal minimumStock,
            String description,
            UUID openingLocationId,
            BigDecimal openingQuantity,
            LocalDate openingDate) {}

    public record ItemView(
            UUID id,
            String sku,
            String name,
            String category,
            String unit,
            BigDecimal minimumStock,
            String description,
            boolean active,
            BigDecimal totalQuantity,
            boolean lowStock,
            List<BalanceView> balances) {}

    public record BalanceView(
            UUID id,
            UUID itemId,
            String sku,
            String itemName,
            UUID locationId,
            String locationCode,
            String locationName,
            BigDecimal quantity,
            Instant updatedAt) {}

    public record MovementCommand(
            String movementType,
            UUID fromLocationId,
            UUID toLocationId,
            BigDecimal quantity,
            LocalDate occurredOn,
            String reference,
            String reason) {}

    public record MovementView(
            UUID id,
            UUID itemId,
            String sku,
            String itemName,
            UUID fromLocationId,
            String fromLocationName,
            UUID toLocationId,
            String toLocationName,
            String movementType,
            BigDecimal quantity,
            LocalDate occurredOn,
            String reference,
            String reason,
            UUID recordedById,
            String recordedByName,
            Instant recordedAt) {}

    public record AssetCommand(
            UUID locationId,
            String assetTag,
            String name,
            String category,
            String brand,
            String model,
            String serialNumber,
            LocalDate acquiredOn,
            BigDecimal acquisitionValue,
            String currency,
            String condition,
            String notes) {}

    public record AssetView(
            UUID id,
            UUID locationId,
            String locationCode,
            String locationName,
            String assetTag,
            String name,
            String category,
            String brand,
            String model,
            String serialNumber,
            LocalDate acquiredOn,
            BigDecimal acquisitionValue,
            String currency,
            String condition,
            String status,
            String notes,
            String activeAssignee,
            int historyCount) {}

    public record AssignmentCommand(
            UUID destinationLocationId,
            String assigneeType,
            String assigneeReference,
            String assigneeLabel,
            LocalDate assignedOn,
            LocalDate dueOn,
            String conditionOut,
            String note) {}

    public record ReturnCommand(
            UUID returnLocationId, LocalDate returnedOn, String conditionIn, String note) {}

    public record AssignmentView(
            UUID id,
            UUID assetId,
            String assetTag,
            String assetName,
            UUID destinationLocationId,
            String destinationLocationName,
            String assigneeType,
            String assigneeReference,
            String assigneeLabel,
            LocalDate assignedOn,
            LocalDate dueOn,
            LocalDate returnedOn,
            String conditionOut,
            String conditionIn,
            String note,
            String assignedByName,
            String returnedByName,
            String status) {}

    public record AssetStatusCommand(
            String status, UUID locationId, LocalDate effectiveOn, String condition, String note) {}

    public record AssetEventView(
            UUID id,
            UUID assetId,
            String assetTag,
            String assetName,
            UUID assignmentId,
            String eventType,
            LocalDate eventDate,
            String fromStatus,
            String toStatus,
            UUID locationId,
            String locationName,
            String detail,
            String recordedByName,
            Instant recordedAt) {}
}
