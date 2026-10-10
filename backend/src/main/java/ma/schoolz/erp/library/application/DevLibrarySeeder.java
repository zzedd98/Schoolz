package ma.schoolz.erp.library.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.library.application.LibraryService.AuthorCommand;
import ma.schoolz.erp.library.application.LibraryService.AuthorView;
import ma.schoolz.erp.library.application.LibraryService.BookCommand;
import ma.schoolz.erp.library.application.LibraryService.BookView;
import ma.schoolz.erp.library.application.LibraryService.CategoryCommand;
import ma.schoolz.erp.library.application.LibraryService.CategoryView;
import ma.schoolz.erp.library.application.LibraryService.ConfigurationCommand;
import ma.schoolz.erp.library.application.LibraryService.CopyCommand;
import ma.schoolz.erp.library.application.LibraryService.CopyView;
import ma.schoolz.erp.library.application.LibraryService.LoanCommand;
import ma.schoolz.erp.library.application.LibraryService.LoanView;
import ma.schoolz.erp.library.application.LibraryService.ReservationCommand;
import ma.schoolz.erp.library.application.LibraryService.ReturnCommand;
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
@Order(260)
public class DevLibrarySeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of(
                    "library.read",
                    "library.catalog.manage",
                    "library.circulation.manage",
                    "library.fine.manage",
                    "library.configure");

    private final JdbcTemplate jdbc;
    private final LibraryService library;
    private final boolean enabled;
    private final String tenantSlug;

    public DevLibrarySeeder(
            JdbcTemplate jdbc,
            LibraryService library,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.library = library;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM library_configuration WHERE tenant_id=?", tenantId) > 0) {
            return;
        }
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        tenantId,
                        "admin@schoolz.ma");
        UUID yasmineId =
                maybeId(
                        "SELECT spp.student_id FROM student_portal_profile spp JOIN"
                                + " identity_user_account u ON u.id=spp.user_account_id AND"
                                + " u.tenant_id=spp.tenant_id WHERE spp.tenant_id=? AND"
                                + " u.normalized_email=?",
                        tenantId,
                        "yasmine.eleve@schoolz.ma");
        UUID libraryLocationId =
                maybeId(
                        "SELECT id FROM inventory_location WHERE tenant_id=? AND code='BIB-01'",
                        tenantId);
        if (yasmineId == null || libraryLocationId == null) return;
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Démonstration bibliothèque",
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
            library.configure(
                    actor,
                    new ConfigurationCommand(
                            true, new BigDecimal("2.00"), new BigDecimal("100.00"), 14, 3),
                    ip);
            CategoryView stories =
                    library.createCategory(
                            actor,
                            new CategoryCommand("RECITS", "Récits & imagination", "#6C63E8"),
                            ip);
            CategoryView textbooks =
                    library.createCategory(
                            actor,
                            new CategoryCommand("MANUELS", "Manuels scolaires", "#2375D8"),
                            ip);
            CategoryView science =
                    library.createCategory(
                            actor,
                            new CategoryCommand("SCIENCES", "Sciences & découvertes", "#18A999"),
                            ip);

            AuthorView saintExupery =
                    library.createAuthor(
                            actor,
                            new AuthorCommand(
                                    "Antoine de Saint-Exupéry",
                                    "SAINT-EXUPERY ANTOINE",
                                    "Auteur et aviateur français."),
                            ip);
            AuthorView collectif =
                    library.createAuthor(
                            actor,
                            new AuthorCommand(
                                    "Collectif pédagogique SchoolZ",
                                    "SCHOOLZ COLLECTIF",
                                    "Référentiel de démonstration pour les ouvrages pédagogiques."),
                            ip);
            AuthorView aicha =
                    library.createAuthor(
                            actor,
                            new AuthorCommand(
                                    "Aïcha El Mansouri",
                                    "EL MANSOURI AICHA",
                                    "Autrice marocaine fictive utilisée uniquement pour la"
                                            + " démonstration."),
                            ip);

            BookView prince =
                    library.createBook(
                            actor,
                            new BookCommand(
                                    stories.id(),
                                    "9780156012195",
                                    "Le Petit Prince",
                                    null,
                                    "Édition de démonstration",
                                    1943,
                                    "fr",
                                    "Un récit pour explorer l’amitié, la responsabilité et"
                                            + " l’imaginaire.",
                                    null,
                                    List.of(saintExupery.id())),
                            ip);
            BookView maths =
                    library.createBook(
                            actor,
                            new BookCommand(
                                    textbooks.id(),
                                    "9789954001001",
                                    "Mathématiques 1AP — Manipuler et comprendre",
                                    "Cahier de la bibliothèque",
                                    "SchoolZ Démo",
                                    2026,
                                    "fr",
                                    "Exercices progressifs pour le cycle primaire.",
                                    null,
                                    List.of(collectif.id())),
                            ip);
            BookView discoveries =
                    library.createBook(
                            actor,
                            new BookCommand(
                                    science.id(),
                                    "9789954002008",
                                    "Petites découvertes du Maroc",
                                    "Sciences, nature et curiosité",
                                    "Atlas Jeunesse Démo",
                                    2025,
                                    "fr",
                                    "Un documentaire jeunesse de démonstration sur la nature et les"
                                            + " sciences.",
                                    null,
                                    List.of(aicha.id())),
                            ip);

            CopyView princeLoanCopy =
                    copy(
                            actor,
                            prince,
                            libraryLocationId,
                            "BIB-REC-0001",
                            "9780156012195-001",
                            "SZ-LIB-QR-0001",
                            ip);
            copy(
                    actor,
                    prince,
                    libraryLocationId,
                    "BIB-REC-0002",
                    "9780156012195-002",
                    "SZ-LIB-QR-0002",
                    ip);
            CopyView mathReservationCopy =
                    copy(
                            actor,
                            maths,
                            libraryLocationId,
                            "BIB-MAN-0011",
                            "9789954001001-011",
                            "SZ-LIB-QR-0011",
                            ip);
            CopyView scienceHistoryCopy =
                    copy(
                            actor,
                            discoveries,
                            libraryLocationId,
                            "BIB-SCI-0021",
                            "9789954002008-021",
                            "SZ-LIB-QR-0021",
                            ip);

            library.createLoan(
                    actor,
                    new LoanCommand(
                            princeLoanCopy.id(),
                            yasmineId,
                            LocalDate.of(2026, 8, 1),
                            LocalDate.of(2026, 8, 20),
                            "GOOD",
                            "Lecture personnelle pour les vacances."),
                    ip);
            LoanView returned =
                    library.createLoan(
                            actor,
                            new LoanCommand(
                                    scienceHistoryCopy.id(),
                                    yasmineId,
                                    LocalDate.of(2026, 6, 1),
                                    LocalDate.of(2026, 6, 10),
                                    "GOOD",
                                    "Sélection documentaire de fin d’année."),
                            ip);
            library.returnLoan(
                    actor,
                    returned.id(),
                    new ReturnCommand(
                            LocalDate.of(2026, 6, 15),
                            "GOOD",
                            "Retour contrôlé avec cinq jours de retard."),
                    ip);
            library.createReservation(actor, new ReservationCommand(maths.id(), yasmineId), ip);
            if (!"RESERVED".equals(library.scan(actor, mathReservationCopy.barcode()).status())) {
                throw new IllegalStateException("La réservation de démonstration doit être prête");
            }
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private CopyView copy(
            SchoolPrincipal actor,
            BookView book,
            UUID locationId,
            String accession,
            String barcode,
            String qr,
            String ip) {
        return library.createCopy(
                actor,
                book.id(),
                new CopyCommand(
                        locationId,
                        accession,
                        barcode,
                        qr,
                        LocalDate.of(2026, 8, 1),
                        "GOOD",
                        "Exemplaire équipé pour scan code-barres et QR."),
                ip);
    }

    private UUID id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private UUID maybeId(String sql, Object... arguments) {
        return jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), arguments).stream()
                .findFirst()
                .orElse(null);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }
}
