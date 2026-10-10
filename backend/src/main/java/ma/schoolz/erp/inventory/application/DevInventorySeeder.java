package ma.schoolz.erp.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.inventory.application.InventoryService.AssetCommand;
import ma.schoolz.erp.inventory.application.InventoryService.AssetStatusCommand;
import ma.schoolz.erp.inventory.application.InventoryService.AssetView;
import ma.schoolz.erp.inventory.application.InventoryService.AssignmentCommand;
import ma.schoolz.erp.inventory.application.InventoryService.AssignmentView;
import ma.schoolz.erp.inventory.application.InventoryService.ItemCommand;
import ma.schoolz.erp.inventory.application.InventoryService.ItemView;
import ma.schoolz.erp.inventory.application.InventoryService.LocationCommand;
import ma.schoolz.erp.inventory.application.InventoryService.LocationView;
import ma.schoolz.erp.inventory.application.InventoryService.MovementCommand;
import ma.schoolz.erp.inventory.application.InventoryService.ReturnCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(250)
public class DevInventorySeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of(
                    "inventory.read",
                    "inventory.write",
                    "inventory.assign",
                    "inventory.adjust",
                    "inventory.configure");

    private final JdbcTemplate jdbc;
    private final InventoryService inventory;
    private final boolean enabled;
    private final String tenantSlug;

    public DevInventorySeeder(
            JdbcTemplate jdbc,
            InventoryService inventory,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM inventory_location WHERE tenant_id=?", tenantId) > 0) {
            return;
        }
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        tenantId,
                        "admin@schoolz.ma");
        UUID campusId =
                id(
                        "SELECT id FROM school_campus WHERE tenant_id=? ORDER BY created_at LIMIT"
                                + " 1",
                        tenantId);
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Démonstration inventaire",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        PERMISSIONS);
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext seeded = SecurityContextHolder.createEmptyContext();
        seeded.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        actor,
                        null,
                        PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(seeded);
        try {
            String ip = "127.0.0.1";
            LocationView warehouse =
                    inventory.createLocation(
                            actor,
                            new LocationCommand(
                                    campusId,
                                    null,
                                    "MAG-CENTRAL",
                                    "Réserve centrale",
                                    "WAREHOUSE",
                                    "Point d’entrée et de contrôle des fournitures."),
                            ip);
            LocationView library =
                    inventory.createLocation(
                            actor,
                            new LocationCommand(
                                    campusId,
                                    null,
                                    "BIB-01",
                                    "Bibliothèque",
                                    "LIBRARY",
                                    "Fonds documentaire et manuels scolaires."),
                            ip);
            LocationView lab =
                    inventory.createLocation(
                            actor,
                            new LocationCommand(
                                    campusId,
                                    null,
                                    "LAB-NUM",
                                    "Laboratoire numérique",
                                    "ROOM",
                                    "Équipements informatiques partagés."),
                            ip);
            LocationView primary =
                    inventory.createLocation(
                            actor,
                            new LocationCommand(
                                    campusId,
                                    null,
                                    "PRIM-1AP-A",
                                    "Classe 1AP — Groupe A",
                                    "ROOM",
                                    "Salle de Yasmine et des ateliers pédagogiques."),
                            ip);

            inventory.createItem(
                    actor,
                    new ItemCommand(
                            "LIV-FR-1AP",
                            "Livre de français 1AP",
                            "BOOK",
                            "exemplaire",
                            new BigDecimal("40"),
                            "Manuel réutilisable suivi par emplacement.",
                            library.id(),
                            new BigDecimal("120"),
                            LocalDate.of(2026, 8, 20)),
                    ip);
            inventory.createItem(
                    actor,
                    new ItemCommand(
                            "UNI-PRIM-BLEU",
                            "Uniforme primaire bleu",
                            "UNIFORM",
                            "pièce",
                            new BigDecimal("100"),
                            "Tailles assorties pour la rentrée.",
                            warehouse.id(),
                            new BigDecimal("80"),
                            LocalDate.of(2026, 8, 20)),
                    ip);
            ItemView kits =
                    inventory.createItem(
                            actor,
                            new ItemCommand(
                                    "MAT-CREA-001",
                                    "Kit de matériel pédagogique créatif",
                                    "TEACHING_MATERIAL",
                                    "kit",
                                    new BigDecimal("20"),
                                    "Géométrie, dessin et manipulation en classe.",
                                    warehouse.id(),
                                    new BigDecimal("45"),
                                    LocalDate.of(2026, 8, 20)),
                            ip);
            inventory.recordMovement(
                    actor,
                    kits.id(),
                    new MovementCommand(
                            "TRANSFER",
                            warehouse.id(),
                            primary.id(),
                            new BigDecimal("12"),
                            LocalDate.of(2026, 8, 28),
                            "TRF-RENTREE-2026",
                            "Mise à disposition des ateliers de rentrée."),
                    ip);

            AssetView computer =
                    inventory.createAsset(
                            actor,
                            new AssetCommand(
                                    lab.id(),
                                    "SZ-PC-2026-001",
                                    "Ordinateur portable enseignant",
                                    "COMPUTER",
                                    "Dell",
                                    "Latitude 5450",
                                    "DEMO-PC-5450-001",
                                    LocalDate.of(2026, 8, 18),
                                    new BigDecimal("9200.00"),
                                    "MAD",
                                    "NEW",
                                    "Équipement pédagogique administré."),
                            ip);
            inventory.assignAsset(
                    actor,
                    computer.id(),
                    new AssignmentCommand(
                            lab.id(),
                            "PERSON",
                            "prof.math@schoolz.ma",
                            "Professeur de mathématiques",
                            LocalDate.of(2026, 9, 1),
                            LocalDate.of(2027, 6, 30),
                            "NEW",
                            "Prêt annuel avec restitution en fin d’année."),
                    ip);

            AssetView tablet =
                    inventory.createAsset(
                            actor,
                            new AssetCommand(
                                    lab.id(),
                                    "SZ-TAB-2026-014",
                                    "Tablette pédagogique 14",
                                    "TABLET",
                                    "Samsung",
                                    "Galaxy Tab A9",
                                    "DEMO-TAB-A9-014",
                                    LocalDate.of(2026, 8, 18),
                                    new BigDecimal("2450.00"),
                                    "MAD",
                                    "GOOD",
                                    "Tablette du chariot numérique."),
                            ip);
            AssignmentView tabletLoan =
                    inventory.assignAsset(
                            actor,
                            tablet.id(),
                            new AssignmentCommand(
                                    primary.id(),
                                    "CLASSROOM",
                                    "1AP-A",
                                    "Classe 1AP — Groupe A",
                                    LocalDate.of(2026, 9, 1),
                                    LocalDate.of(2026, 9, 4),
                                    "GOOD",
                                    "Atelier numérique de rentrée."),
                            ip);
            inventory.returnAsset(
                    actor,
                    tabletLoan.id(),
                    new ReturnCommand(
                            lab.id(),
                            LocalDate.of(2026, 9, 4),
                            "GOOD",
                            "Restituée complète et fonctionnelle."),
                    ip);

            AssetView projector =
                    inventory.createAsset(
                            actor,
                            new AssetCommand(
                                    primary.id(),
                                    "SZ-PROJ-2026-003",
                                    "Projecteur salle primaire",
                                    "PROJECTOR",
                                    "Epson",
                                    "EB-W06",
                                    "DEMO-PROJ-W06-003",
                                    LocalDate.of(2024, 9, 2),
                                    new BigDecimal("6800.00"),
                                    "MAD",
                                    "FAIR",
                                    "Lampe à contrôler avant la rentrée."),
                            ip);
            inventory.changeAssetStatus(
                    actor,
                    projector.id(),
                    new AssetStatusCommand(
                            "MAINTENANCE",
                            primary.id(),
                            LocalDate.of(2026, 8, 29),
                            "FAIR",
                            "Maintenance préventive et contrôle de la lampe."),
                    ip);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private UUID id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }
}
