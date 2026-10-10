package ma.schoolz.erp.library.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.enrollment.PortalStudentAccess;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryService {
    private static final Set<String> CONDITIONS = Set.of("NEW", "GOOD", "FAIR", "DAMAGED");
    private static final Set<String> FINE_RESOLUTIONS = Set.of("PAID", "WAIVED");

    private final JdbcTemplate jdbc;
    private final PortalStudentAccess portalStudents;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public LibraryService(
            JdbcTemplate jdbc,
            PortalStudentAccess portalStudents,
            IdentityAuditTrail audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.portalStudents = portalStudents;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('library.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        UUID tenantId = actor.tenantId();
        List<AuthorView> authors = authors(tenantId);
        List<BookAuthorView> bookAuthors = bookAuthors(tenantId);
        List<BookView> books = books(tenantId, bookAuthors);
        List<CopyView> copies = copies(tenantId);
        List<LoanView> loans = loans(tenantId, null);
        List<ReservationView> reservations = reservations(tenantId, null);
        List<FineView> fines = fines(tenantId, null);
        return new Dashboard(
                clock.instant(),
                configuration(tenantId),
                new Statistics(
                        books.size(),
                        copies.size(),
                        copies.stream().filter(copy -> "AVAILABLE".equals(copy.status())).count(),
                        loans.stream().filter(loan -> "ACTIVE".equals(loan.status())).count(),
                        loans.stream().filter(LoanView::overdue).count(),
                        reservations.stream()
                                .filter(
                                        reservation ->
                                                "WAITING".equals(reservation.status())
                                                        || "READY".equals(reservation.status()))
                                .count(),
                        fines.stream().filter(fine -> "OPEN".equals(fine.status())).count()),
                authors,
                categories(tenantId),
                books,
                copies,
                loans,
                reservations,
                fines,
                students(tenantId));
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.configure')")
    public ConfigurationView configure(
            SchoolPrincipal actor, ConfigurationCommand command, String ipAddress) {
        if (command == null
                || command.defaultLoanDays() < 1
                || command.defaultLoanDays() > 120
                || command.reservationHoldDays() < 1
                || command.reservationHoldDays() > 30) {
            throw new LibraryValidationException("La configuration bibliothèque est invalide");
        }
        BigDecimal daily = money(command.finePerOverdueDay());
        BigDecimal maximum = money(command.maximumFine());
        if (daily.signum() < 0 || maximum.signum() < 0) {
            throw new LibraryValidationException(
                    "Les montants d’amende ne peuvent pas être négatifs");
        }
        if (command.fineEnabled() && (daily.signum() == 0 || maximum.signum() == 0)) {
            throw new LibraryValidationException(
                    "Une amende activée exige un montant journalier et un plafond positifs");
        }
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE library_configuration SET"
                            + " fine_enabled=?,fine_per_overdue_day=?,maximum_fine=?,default_loan_days=?,reservation_hold_days=?,updated_at=?"
                            + " WHERE tenant_id=?",
                        command.fineEnabled(),
                        daily,
                        maximum,
                        command.defaultLoanDays(),
                        command.reservationHoldDays(),
                        timestamp(now),
                        actor.tenantId());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO library_configuration"
                        + " (tenant_id,fine_enabled,fine_per_overdue_day,maximum_fine,default_loan_days,reservation_hold_days,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                    actor.tenantId(),
                    command.fineEnabled(),
                    daily,
                    maximum,
                    command.defaultLoanDays(),
                    command.reservationHoldDays(),
                    timestamp(now),
                    timestamp(now));
        }
        ConfigurationView result = configuration(actor.tenantId());
        audit(
                actor,
                "library.configuration.updated",
                "LibraryConfiguration",
                null,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.catalog.manage')")
    public AuthorView createAuthor(SchoolPrincipal actor, AuthorCommand command, String ipAddress) {
        if (command == null) throw new LibraryValidationException("L’auteur est requis");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO library_author"
                        + " (id,tenant_id,display_name,sort_name,biography,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?,?,?)",
                id,
                actor.tenantId(),
                required(command.displayName(), 180, "Le nom affiché"),
                required(command.sortName(), 180, "Le nom de tri").toUpperCase(Locale.ROOT),
                optional(command.biography(), 1000),
                timestamp(now),
                timestamp(now));
        AuthorView result = author(actor.tenantId(), id);
        audit(actor, "library.author.created", "Author", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.catalog.manage')")
    public CategoryView createCategory(
            SchoolPrincipal actor, CategoryCommand command, String ipAddress) {
        if (command == null) throw new LibraryValidationException("La catégorie est requise");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO library_category"
                        + " (id,tenant_id,code,name,color,active,created_at,updated_at) VALUES"
                        + " (?,?,?,?,?,TRUE,?,?)",
                id,
                actor.tenantId(),
                required(command.code(), 50, "Le code").toUpperCase(Locale.ROOT),
                required(command.name(), 140, "Le nom"),
                color(command.color()),
                timestamp(now),
                timestamp(now));
        CategoryView result = category(actor.tenantId(), id);
        audit(actor, "library.category.created", "Category", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.catalog.manage')")
    public BookView createBook(SchoolPrincipal actor, BookCommand command, String ipAddress) {
        if (command == null || command.categoryId() == null) {
            throw new LibraryValidationException("Le livre et sa catégorie sont requis");
        }
        ensureCategory(actor.tenantId(), command.categoryId());
        if (command.publicationYear() != null
                && (command.publicationYear() < 1000 || command.publicationYear() > 2200)) {
            throw new LibraryValidationException("L’année de publication est invalide");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO library_book"
                    + " (id,tenant_id,category_id,isbn,title,subtitle,publisher,publication_year,language_code,description,cover_key,active,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,TRUE,?,?)",
                id,
                actor.tenantId(),
                command.categoryId(),
                optional(command.isbn(), 20),
                required(command.title(), 260, "Le titre"),
                optional(command.subtitle(), 260),
                optional(command.publisher(), 180),
                command.publicationYear(),
                language(command.languageCode()),
                optional(command.description(), 1200),
                optional(command.coverKey(), 300),
                timestamp(now),
                timestamp(now));
        if (command.authorIds() != null) {
            int position = 1;
            for (UUID authorId : command.authorIds()) {
                ensureAuthor(actor.tenantId(), authorId);
                jdbc.update(
                        "INSERT INTO library_book_author"
                                + " (tenant_id,book_id,author_id,author_position) VALUES (?,?,?,?)",
                        actor.tenantId(),
                        id,
                        authorId,
                        position++);
            }
        }
        BookView result = book(actor.tenantId(), id);
        audit(actor, "library.book.created", "Book", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.catalog.manage')")
    public CopyView createCopy(
            SchoolPrincipal actor, UUID bookId, CopyCommand command, String ipAddress) {
        if (command == null) throw new LibraryValidationException("L’exemplaire est requis");
        ensureBook(actor.tenantId(), bookId);
        if (command.locationId() != null) ensureLocation(actor.tenantId(), command.locationId());
        String barcode = optional(command.barcode(), 120);
        String qrCode = optional(command.qrCode(), 180);
        if (barcode == null && qrCode == null) {
            throw new LibraryValidationException(
                    "Un code-barres ou un identifiant QR est obligatoire");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO library_book_copy"
                    + " (id,tenant_id,book_id,location_id,accession_number,barcode,qr_code,acquired_on,condition_status,status,notes,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,'AVAILABLE',?,?,?)",
                id,
                actor.tenantId(),
                bookId,
                command.locationId(),
                required(command.accessionNumber(), 70, "Le numéro d’entrée")
                        .toUpperCase(Locale.ROOT),
                barcode,
                qrCode,
                command.acquiredOn(),
                condition(command.condition()),
                optional(command.notes(), 700),
                timestamp(now),
                timestamp(now));
        CopyView result = copy(actor.tenantId(), id);
        audit(actor, "library.copy.created", "BookCopy", id, ipAddress, result);
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('library.read')")
    public CopyView scan(SchoolPrincipal actor, String code) {
        String normalized = required(code, 180, "Le code scanné");
        return jdbc
                .query(
                        copySql() + " WHERE cp.tenant_id=? AND (cp.barcode=? OR cp.qr_code=?)",
                        this::mapCopy,
                        actor.tenantId(),
                        normalized,
                        normalized)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new LibraryNotFoundException(
                                        "Aucun exemplaire ne correspond au code"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.circulation.manage')")
    public LoanView createLoan(SchoolPrincipal actor, LoanCommand command, String ipAddress) {
        if (command == null
                || command.copyId() == null
                || command.studentId() == null
                || command.loanedOn() == null) {
            throw new LibraryValidationException("L’exemplaire, l’élève et la date sont requis");
        }
        ensureStudent(actor.tenantId(), command.studentId());
        CopyView copy = copy(actor.tenantId(), command.copyId());
        UUID reservationId = null;
        if ("RESERVED".equals(copy.status())) {
            List<UUID> matching =
                    jdbc.query(
                            "SELECT id FROM library_reservation WHERE tenant_id=? AND"
                                + " ready_copy_id=? AND student_id=? AND status='READY' FOR UPDATE",
                            (rs, row) -> rs.getObject(1, UUID.class),
                            actor.tenantId(),
                            command.copyId(),
                            command.studentId());
            if (matching.isEmpty()) {
                throw new LibraryConflictException("Cet exemplaire est réservé à un autre élève");
            }
            reservationId = matching.getFirst();
        } else if (!"AVAILABLE".equals(copy.status())) {
            throw new LibraryConflictException("Cet exemplaire n’est pas disponible");
        }
        ConfigurationView configuration = configuration(actor.tenantId());
        LocalDate dueOn =
                command.dueOn() == null
                        ? command.loanedOn().plusDays(configuration.defaultLoanDays())
                        : command.dueOn();
        if (dueOn.isBefore(command.loanedOn())) {
            throw new LibraryValidationException("L’échéance précède la date de prêt");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO library_loan"
                    + " (id,tenant_id,book_copy_id,student_id,reservation_id,loaned_on,due_on,returned_on,condition_out,condition_in,status,loaned_by_user_id,returned_by_user_id,note,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,NULL,?,NULL,'ACTIVE',?,NULL,?,?,?)",
                id,
                actor.tenantId(),
                command.copyId(),
                command.studentId(),
                reservationId,
                command.loanedOn(),
                dueOn,
                condition(command.conditionOut()),
                actor.accountId(),
                optional(command.note(), 700),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "UPDATE library_book_copy SET status='ON_LOAN',updated_at=? WHERE tenant_id=? AND"
                        + " id=?",
                timestamp(now),
                actor.tenantId(),
                command.copyId());
        if (reservationId != null) {
            jdbc.update(
                    "UPDATE library_reservation SET status='FULFILLED',fulfilled_at=?,updated_at=?"
                            + " WHERE tenant_id=? AND id=?",
                    timestamp(now),
                    timestamp(now),
                    actor.tenantId(),
                    reservationId);
        }
        LoanView result = loan(actor.tenantId(), id);
        audit(actor, "library.loan.created", "Loan", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.circulation.manage')")
    public LoanView returnLoan(
            SchoolPrincipal actor, UUID loanId, ReturnCommand command, String ipAddress) {
        if (command == null || command.returnedOn() == null) {
            throw new LibraryValidationException("La date et l’état de retour sont requis");
        }
        LoanView before = loan(actor.tenantId(), loanId);
        if (!"ACTIVE".equals(before.status())) {
            throw new LibraryConflictException("Ce prêt est déjà terminé");
        }
        if (command.returnedOn().isBefore(before.loanedOn())) {
            throw new LibraryValidationException("Le retour précède le prêt");
        }
        String conditionIn = condition(command.conditionIn());
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE library_loan SET"
                    + " returned_on=?,condition_in=?,status='RETURNED',returned_by_user_id=?,note=?,updated_at=?"
                    + " WHERE tenant_id=? AND id=? AND status='ACTIVE'",
                command.returnedOn(),
                conditionIn,
                actor.accountId(),
                optional(command.note(), 700),
                timestamp(now),
                actor.tenantId(),
                loanId);
        jdbc.update(
                "UPDATE library_book_copy SET status='AVAILABLE',condition_status=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                conditionIn,
                timestamp(now),
                actor.tenantId(),
                before.copyId());
        assessFine(actor.tenantId(), before, command.returnedOn(), now);
        promoteReservation(actor.tenantId(), before.bookId(), before.copyId(), now);
        LoanView result = loan(actor.tenantId(), loanId);
        audit(actor, "library.loan.returned", "Loan", loanId, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.circulation.manage')")
    public ReservationView createReservation(
            SchoolPrincipal actor, ReservationCommand command, String ipAddress) {
        if (command == null || command.bookId() == null || command.studentId() == null) {
            throw new LibraryValidationException("Le livre et l’élève sont requis");
        }
        ensureBook(actor.tenantId(), command.bookId());
        ensureStudent(actor.tenantId(), command.studentId());
        int existing =
                count(
                        "SELECT count(*) FROM library_reservation WHERE tenant_id=? AND book_id=?"
                                + " AND student_id=? AND status IN ('WAITING','READY')",
                        actor.tenantId(),
                        command.bookId(),
                        command.studentId());
        if (existing > 0) {
            throw new LibraryConflictException("Cet élève possède déjà une réservation active");
        }
        int queuePosition =
                count(
                                "SELECT COALESCE(max(queue_position),0) FROM library_reservation"
                                        + " WHERE tenant_id=? AND book_id=?",
                                actor.tenantId(),
                                command.bookId())
                        + 1;
        List<UUID> available =
                jdbc.query(
                        "SELECT id FROM library_book_copy WHERE tenant_id=? AND book_id=? AND"
                            + " status='AVAILABLE' ORDER BY accession_number LIMIT 1 FOR UPDATE",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        actor.tenantId(),
                        command.bookId());
        UUID copyId = available.isEmpty() ? null : available.getFirst();
        String status = copyId == null ? "WAITING" : "READY";
        Instant now = clock.instant();
        Instant expires =
                copyId == null
                        ? null
                        : now.plus(
                                configuration(actor.tenantId()).reservationHoldDays(),
                                ChronoUnit.DAYS);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO library_reservation"
                    + " (id,tenant_id,book_id,student_id,ready_copy_id,queue_position,status,reserved_at,ready_at,expires_at,fulfilled_at,cancelled_at,created_by_user_id,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,NULL,NULL,?,?)",
                id,
                actor.tenantId(),
                command.bookId(),
                command.studentId(),
                copyId,
                queuePosition,
                status,
                timestamp(now),
                copyId == null ? null : timestamp(now),
                expires == null ? null : timestamp(expires),
                actor.accountId(),
                timestamp(now));
        if (copyId != null) reserveCopy(actor.tenantId(), copyId, now);
        ReservationView result = reservation(actor.tenantId(), id);
        audit(actor, "library.reservation.created", "Reservation", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.circulation.manage')")
    public ReservationView cancelReservation(
            SchoolPrincipal actor, UUID reservationId, String ipAddress) {
        ReservationView before = reservation(actor.tenantId(), reservationId);
        if (!("WAITING".equals(before.status()) || "READY".equals(before.status()))) {
            throw new LibraryConflictException("Cette réservation est déjà terminée");
        }
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE library_reservation SET status='CANCELLED',cancelled_at=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                reservationId);
        if (before.readyCopyId() != null) {
            jdbc.update(
                    "UPDATE library_book_copy SET status='AVAILABLE',updated_at=? WHERE tenant_id=?"
                            + " AND id=? AND status='RESERVED'",
                    timestamp(now),
                    actor.tenantId(),
                    before.readyCopyId());
            promoteReservation(actor.tenantId(), before.bookId(), before.readyCopyId(), now);
        }
        ReservationView result = reservation(actor.tenantId(), reservationId);
        audit(
                actor,
                "library.reservation.cancelled",
                "Reservation",
                reservationId,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('library.fine.manage')")
    public FineView resolveFine(
            SchoolPrincipal actor, UUID fineId, FineResolutionCommand command, String ipAddress) {
        if (command == null) throw new LibraryValidationException("La résolution est requise");
        FineView before = fine(actor.tenantId(), fineId);
        if (!"OPEN".equals(before.status())) {
            throw new LibraryConflictException("Cette amende est déjà résolue");
        }
        String status =
                enumValue(command.resolution(), FINE_RESOLUTIONS, "La résolution de l’amende");
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE library_fine SET"
                        + " status=?,resolution_note=?,resolved_at=?,resolved_by_user_id=? WHERE"
                        + " tenant_id=? AND id=? AND status='OPEN'",
                status,
                required(command.note(), 700, "La note de résolution"),
                timestamp(now),
                actor.accountId(),
                actor.tenantId(),
                fineId);
        FineView result = fine(actor.tenantId(), fineId);
        audit(actor, "library.fine.resolved", "Fine", fineId, ipAddress, result);
        return result;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('student-portal.read')")
    public PortalDashboard studentPortal(SchoolPrincipal actor) {
        UUID studentId =
                jdbc
                        .query(
                                "SELECT student_id FROM student_portal_profile WHERE tenant_id=?"
                                        + " AND user_account_id=? AND active=TRUE",
                                (rs, row) -> rs.getObject(1, UUID.class),
                                actor.tenantId(),
                                actor.accountId())
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new LibraryNotFoundException(
                                                "Aucun élève n’est lié à ce compte"));
        return portal(actor.tenantId(), Set.of(studentId));
    }

    @Transactional(readOnly = true)
    public PortalDashboard familyPortal(PortalIdentity parent, UUID requestedStudentId) {
        Set<UUID> linked = portalStudents.linkedStudentIds(parent);
        if (requestedStudentId != null && !linked.contains(requestedStudentId)) {
            throw new LibraryNotFoundException("Enfant lié introuvable");
        }
        return portal(
                parent.tenantId(),
                requestedStudentId == null ? linked : Set.of(requestedStudentId));
    }

    private PortalDashboard portal(UUID tenantId, Set<UUID> studentIds) {
        List<BorrowerView> borrowers =
                students(tenantId).stream()
                        .filter(student -> studentIds.contains(student.id()))
                        .map(
                                student ->
                                        new BorrowerView(
                                                student,
                                                loans(tenantId, student.id()),
                                                reservations(tenantId, student.id()),
                                                fines(tenantId, student.id())))
                        .toList();
        return new PortalDashboard(clock.instant(), configuration(tenantId), borrowers);
    }

    private void assessFine(UUID tenantId, LoanView loan, LocalDate returnedOn, Instant now) {
        ConfigurationView config = configuration(tenantId);
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(loan.dueOn(), returnedOn));
        if (!config.fineEnabled() || overdueDays == 0 || config.finePerOverdueDay().signum() == 0) {
            return;
        }
        BigDecimal amount = config.finePerOverdueDay().multiply(BigDecimal.valueOf(overdueDays));
        if (config.maximumFine().signum() > 0 && amount.compareTo(config.maximumFine()) > 0) {
            amount = config.maximumFine();
        }
        jdbc.update(
                "INSERT INTO library_fine"
                    + " (id,tenant_id,loan_id,student_id,overdue_days,amount,currency,status,resolution_note,assessed_at,resolved_at,resolved_by_user_id)"
                    + " VALUES (?,?,?,?,?,?,?,'OPEN',NULL,?,NULL,NULL)",
                UUID.randomUUID(),
                tenantId,
                loan.id(),
                loan.studentId(),
                overdueDays,
                money(amount),
                "MAD",
                timestamp(now));
    }

    private void promoteReservation(UUID tenantId, UUID bookId, UUID copyId, Instant now) {
        List<UUID> waiting =
                jdbc.query(
                        "SELECT id FROM library_reservation WHERE tenant_id=? AND book_id=? AND"
                            + " status='WAITING' ORDER BY queue_position,reserved_at LIMIT 1 FOR"
                            + " UPDATE",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId,
                        bookId);
        if (waiting.isEmpty()) return;
        int holdDays = configuration(tenantId).reservationHoldDays();
        jdbc.update(
                "UPDATE library_reservation SET"
                    + " ready_copy_id=?,status='READY',ready_at=?,expires_at=?,updated_at=? WHERE"
                    + " tenant_id=? AND id=? AND status='WAITING'",
                copyId,
                timestamp(now),
                timestamp(now.plus(holdDays, ChronoUnit.DAYS)),
                timestamp(now),
                tenantId,
                waiting.getFirst());
        reserveCopy(tenantId, copyId, now);
    }

    private void reserveCopy(UUID tenantId, UUID copyId, Instant now) {
        jdbc.update(
                "UPDATE library_book_copy SET status='RESERVED',updated_at=? WHERE tenant_id=? AND"
                        + " id=? AND status='AVAILABLE'",
                timestamp(now),
                tenantId,
                copyId);
    }

    private ConfigurationView configuration(UUID tenantId) {
        return jdbc
                .query(
                        "SELECT * FROM library_configuration WHERE tenant_id=?",
                        (rs, row) ->
                                new ConfigurationView(
                                        rs.getBoolean("fine_enabled"),
                                        money(rs.getBigDecimal("fine_per_overdue_day")),
                                        money(rs.getBigDecimal("maximum_fine")),
                                        rs.getInt("default_loan_days"),
                                        rs.getInt("reservation_hold_days")),
                        tenantId)
                .stream()
                .findFirst()
                .orElse(new ConfigurationView(false, BigDecimal.ZERO, BigDecimal.ZERO, 14, 3));
    }

    private List<AuthorView> authors(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM library_author WHERE tenant_id=? ORDER BY sort_name",
                (rs, row) -> mapAuthor(rs),
                tenantId);
    }

    private AuthorView author(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM library_author WHERE tenant_id=? AND id=?",
                        (rs, row) -> mapAuthor(rs),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Auteur introuvable"));
    }

    private AuthorView mapAuthor(ResultSet rs) throws SQLException {
        return new AuthorView(
                uuid(rs, "id"),
                rs.getString("display_name"),
                rs.getString("sort_name"),
                rs.getString("biography"));
    }

    private List<CategoryView> categories(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM library_category WHERE tenant_id=? ORDER BY name",
                (rs, row) -> mapCategory(rs),
                tenantId);
    }

    private CategoryView category(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM library_category WHERE tenant_id=? AND id=?",
                        (rs, row) -> mapCategory(rs),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Catégorie introuvable"));
    }

    private CategoryView mapCategory(ResultSet rs) throws SQLException {
        return new CategoryView(
                uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("color"),
                rs.getBoolean("active"));
    }

    private List<BookAuthorView> bookAuthors(UUID tenantId) {
        return jdbc.query(
                "SELECT ba.book_id,a.id author_id,a.display_name,ba.author_position FROM"
                        + " library_book_author ba JOIN library_author a ON a.id=ba.author_id AND"
                        + " a.tenant_id=ba.tenant_id WHERE ba.tenant_id=? ORDER BY"
                        + " ba.book_id,ba.author_position",
                (rs, row) ->
                        new BookAuthorView(
                                uuid(rs, "book_id"),
                                uuid(rs, "author_id"),
                                rs.getString("display_name"),
                                rs.getInt("author_position")),
                tenantId);
    }

    private List<BookView> books(UUID tenantId, List<BookAuthorView> authorLinks) {
        return jdbc.query(
                bookSql() + " WHERE b.tenant_id=? ORDER BY b.title",
                (rs, row) -> mapBook(rs, authorLinks),
                tenantId);
    }

    private BookView book(UUID tenantId, UUID id) {
        List<BookAuthorView> links = bookAuthors(tenantId);
        return jdbc
                .query(
                        bookSql() + " WHERE b.tenant_id=? AND b.id=?",
                        (rs, row) -> mapBook(rs, links),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Livre introuvable"));
    }

    private String bookSql() {
        return "SELECT b.*,c.code category_code,c.name category_name,c.color category_color,(SELECT"
                + " count(*) FROM library_book_copy cp WHERE cp.tenant_id=b.tenant_id AND"
                + " cp.book_id=b.id) copy_count,(SELECT count(*) FROM library_book_copy cp WHERE"
                + " cp.tenant_id=b.tenant_id AND cp.book_id=b.id AND cp.status='AVAILABLE')"
                + " available_count FROM library_book b JOIN library_category c ON"
                + " c.id=b.category_id AND c.tenant_id=b.tenant_id";
    }

    private BookView mapBook(ResultSet rs, List<BookAuthorView> links) throws SQLException {
        UUID id = uuid(rs, "id");
        return new BookView(
                id,
                uuid(rs, "category_id"),
                rs.getString("category_code"),
                rs.getString("category_name"),
                rs.getString("category_color"),
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("publisher"),
                (Integer) rs.getObject("publication_year"),
                rs.getString("language_code"),
                rs.getString("description"),
                rs.getString("cover_key"),
                rs.getBoolean("active"),
                rs.getInt("copy_count"),
                rs.getInt("available_count"),
                links.stream().filter(link -> id.equals(link.bookId())).toList());
    }

    private List<CopyView> copies(UUID tenantId) {
        return jdbc.query(
                copySql() + " WHERE cp.tenant_id=? ORDER BY b.title,cp.accession_number",
                this::mapCopy,
                tenantId);
    }

    private CopyView copy(UUID tenantId, UUID id) {
        return jdbc
                .query(copySql() + " WHERE cp.tenant_id=? AND cp.id=?", this::mapCopy, tenantId, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Exemplaire introuvable"));
    }

    private String copySql() {
        return "SELECT cp.*,b.title,l.code location_code,l.name location_name FROM"
                + " library_book_copy cp JOIN library_book b ON b.id=cp.book_id AND"
                + " b.tenant_id=cp.tenant_id LEFT JOIN inventory_location l ON"
                + " l.id=cp.location_id AND l.tenant_id=cp.tenant_id";
    }

    private CopyView mapCopy(ResultSet rs, int row) throws SQLException {
        return new CopyView(
                uuid(rs, "id"),
                uuid(rs, "book_id"),
                rs.getString("title"),
                nullableUuid(rs, "location_id"),
                rs.getString("location_code"),
                rs.getString("location_name"),
                rs.getString("accession_number"),
                rs.getString("barcode"),
                rs.getString("qr_code"),
                rs.getObject("acquired_on", LocalDate.class),
                rs.getString("condition_status"),
                rs.getString("status"),
                rs.getString("notes"));
    }

    private List<LoanView> loans(UUID tenantId, UUID studentId) {
        String filter = studentId == null ? "" : " AND ln.student_id=?";
        Object[] arguments =
                studentId == null ? new Object[] {tenantId} : new Object[] {tenantId, studentId};
        return jdbc.query(
                loanSql()
                        + " WHERE ln.tenant_id=?"
                        + filter
                        + " ORDER BY ln.loaned_on DESC,ln.created_at DESC",
                this::mapLoan,
                arguments);
    }

    private LoanView loan(UUID tenantId, UUID id) {
        return jdbc
                .query(loanSql() + " WHERE ln.tenant_id=? AND ln.id=?", this::mapLoan, tenantId, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Prêt introuvable"));
    }

    private String loanSql() {
        return "SELECT"
                   + " ln.*,cp.book_id,cp.accession_number,cp.barcode,b.title,p.first_name,p.last_name,f.id"
                   + " fine_id,f.amount fine_amount,f.status fine_status FROM library_loan ln JOIN"
                   + " library_book_copy cp ON cp.id=ln.book_copy_id AND cp.tenant_id=ln.tenant_id"
                   + " JOIN library_book b ON b.id=cp.book_id AND b.tenant_id=cp.tenant_id JOIN"
                   + " student_profile sp ON sp.id=ln.student_id AND sp.tenant_id=ln.tenant_id JOIN"
                   + " person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id LEFT JOIN"
                   + " library_fine f ON f.loan_id=ln.id AND f.tenant_id=ln.tenant_id";
    }

    private LoanView mapLoan(ResultSet rs, int row) throws SQLException {
        LocalDate dueOn = rs.getObject("due_on", LocalDate.class);
        LocalDate returnedOn = rs.getObject("returned_on", LocalDate.class);
        boolean overdue =
                "ACTIVE".equals(rs.getString("status")) && dueOn.isBefore(LocalDate.now(clock));
        return new LoanView(
                uuid(rs, "id"),
                uuid(rs, "book_copy_id"),
                uuid(rs, "book_id"),
                rs.getString("accession_number"),
                rs.getString("barcode"),
                rs.getString("title"),
                uuid(rs, "student_id"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                nullableUuid(rs, "reservation_id"),
                rs.getObject("loaned_on", LocalDate.class),
                dueOn,
                returnedOn,
                rs.getString("condition_out"),
                rs.getString("condition_in"),
                rs.getString("status"),
                rs.getString("note"),
                overdue,
                nullableUuid(rs, "fine_id"),
                rs.getBigDecimal("fine_amount"),
                rs.getString("fine_status"));
    }

    private List<ReservationView> reservations(UUID tenantId, UUID studentId) {
        String filter = studentId == null ? "" : " AND r.student_id=?";
        Object[] arguments =
                studentId == null ? new Object[] {tenantId} : new Object[] {tenantId, studentId};
        return jdbc.query(
                reservationSql() + " WHERE r.tenant_id=?" + filter + " ORDER BY r.reserved_at DESC",
                this::mapReservation,
                arguments);
    }

    private ReservationView reservation(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        reservationSql() + " WHERE r.tenant_id=? AND r.id=?",
                        this::mapReservation,
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Réservation introuvable"));
    }

    private String reservationSql() {
        return "SELECT r.*,b.title,cp.accession_number,p.first_name,p.last_name FROM"
                + " library_reservation r JOIN library_book b ON b.id=r.book_id AND"
                + " b.tenant_id=r.tenant_id JOIN student_profile sp ON sp.id=r.student_id AND"
                + " sp.tenant_id=r.tenant_id JOIN person p ON p.id=sp.person_id AND"
                + " p.tenant_id=sp.tenant_id LEFT JOIN library_book_copy cp ON"
                + " cp.id=r.ready_copy_id AND cp.tenant_id=r.tenant_id";
    }

    private ReservationView mapReservation(ResultSet rs, int row) throws SQLException {
        return new ReservationView(
                uuid(rs, "id"),
                uuid(rs, "book_id"),
                rs.getString("title"),
                uuid(rs, "student_id"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                nullableUuid(rs, "ready_copy_id"),
                rs.getString("accession_number"),
                rs.getInt("queue_position"),
                rs.getString("status"),
                instant(rs, "reserved_at"),
                instant(rs, "ready_at"),
                instant(rs, "expires_at"),
                instant(rs, "fulfilled_at"),
                instant(rs, "cancelled_at"));
    }

    private List<FineView> fines(UUID tenantId, UUID studentId) {
        String filter = studentId == null ? "" : " AND f.student_id=?";
        Object[] arguments =
                studentId == null ? new Object[] {tenantId} : new Object[] {tenantId, studentId};
        return jdbc.query(
                fineSql() + " WHERE f.tenant_id=?" + filter + " ORDER BY f.assessed_at DESC",
                this::mapFine,
                arguments);
    }

    private FineView fine(UUID tenantId, UUID id) {
        return jdbc
                .query(fineSql() + " WHERE f.tenant_id=? AND f.id=?", this::mapFine, tenantId, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new LibraryNotFoundException("Amende introuvable"));
    }

    private String fineSql() {
        return "SELECT f.*,b.title,p.first_name,p.last_name FROM library_fine f JOIN library_loan"
                + " ln ON ln.id=f.loan_id AND ln.tenant_id=f.tenant_id JOIN library_book_copy cp"
                + " ON cp.id=ln.book_copy_id AND cp.tenant_id=ln.tenant_id JOIN library_book b"
                + " ON b.id=cp.book_id AND b.tenant_id=cp.tenant_id JOIN student_profile sp ON"
                + " sp.id=f.student_id AND sp.tenant_id=f.tenant_id JOIN person p ON"
                + " p.id=sp.person_id AND p.tenant_id=sp.tenant_id";
    }

    private FineView mapFine(ResultSet rs, int row) throws SQLException {
        return new FineView(
                uuid(rs, "id"),
                uuid(rs, "loan_id"),
                uuid(rs, "student_id"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("title"),
                rs.getInt("overdue_days"),
                money(rs.getBigDecimal("amount")),
                rs.getString("currency"),
                rs.getString("status"),
                rs.getString("resolution_note"),
                instant(rs, "assessed_at"),
                instant(rs, "resolved_at"));
    }

    private List<StudentOption> students(UUID tenantId) {
        return jdbc.query(
                "SELECT sp.id,p.first_name,p.last_name,(SELECT si.identifier_value FROM"
                    + " student_identifier si WHERE si.tenant_id=sp.tenant_id AND"
                    + " si.student_id=sp.id ORDER BY si.primary_identifier DESC,si.created_at LIMIT"
                    + " 1) student_number FROM student_profile sp JOIN person p ON"
                    + " p.id=sp.person_id AND p.tenant_id=sp.tenant_id WHERE sp.tenant_id=? AND"
                    + " sp.status='ACTIVE' ORDER BY p.first_name,p.last_name",
                (rs, row) ->
                        new StudentOption(
                                uuid(rs, "id"),
                                rs.getString("first_name") + " " + rs.getString("last_name"),
                                rs.getString("student_number")),
                tenantId);
    }

    private void ensureAuthor(UUID tenantId, UUID id) {
        ensure(
                "SELECT count(*) FROM library_author WHERE tenant_id=? AND id=?",
                tenantId,
                id,
                "Auteur introuvable");
    }

    private void ensureCategory(UUID tenantId, UUID id) {
        ensure(
                "SELECT count(*) FROM library_category WHERE tenant_id=? AND id=? AND active=TRUE",
                tenantId,
                id,
                "Catégorie introuvable");
    }

    private void ensureBook(UUID tenantId, UUID id) {
        ensure(
                "SELECT count(*) FROM library_book WHERE tenant_id=? AND id=? AND active=TRUE",
                tenantId,
                id,
                "Livre introuvable");
    }

    private void ensureStudent(UUID tenantId, UUID id) {
        ensure(
                "SELECT count(*) FROM student_profile WHERE tenant_id=? AND id=? AND"
                        + " status='ACTIVE'",
                tenantId,
                id,
                "Élève introuvable");
    }

    private void ensureLocation(UUID tenantId, UUID id) {
        ensure(
                "SELECT count(*) FROM inventory_location WHERE tenant_id=? AND id=? AND"
                        + " active=TRUE",
                tenantId,
                id,
                "Emplacement introuvable");
    }

    private void ensure(String sql, UUID tenantId, UUID id, String message) {
        if (count(sql, tenantId, id) == 0) throw new LibraryNotFoundException(message);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private String required(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new LibraryValidationException(label + " est requis");
        if (normalized.length() > max)
            throw new LibraryValidationException(label + " dépasse " + max + " caractères");
        return normalized;
    }

    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max)
            throw new LibraryValidationException("Un texte dépasse " + max + " caractères");
        return normalized;
    }

    private String condition(String value) {
        return enumValue(value, CONDITIONS, "L’état physique");
    }

    private String enumValue(String value, Set<String> allowed, String label) {
        String normalized = required(value, 30, label).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized))
            throw new LibraryValidationException(label + " n’est pas reconnu");
        return normalized;
    }

    private String language(String value) {
        String normalized = required(value, 10, "La langue").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2}(-[a-z]{2})?"))
            throw new LibraryValidationException("Le code langue est invalide");
        return normalized;
    }

    private String color(String value) {
        if (value == null || value.isBlank()) return "#2f80ed";
        String normalized = value.trim();
        if (!normalized.matches("#[0-9A-Fa-f]{6}"))
            throw new LibraryValidationException("La couleur doit être hexadécimale");
        return normalized;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
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
                resourceId == null ? actor.tenantId().toString() : resourceId.toString(),
                ipAddress,
                Map.of("module", "library"),
                null,
                after);
    }

    public record Dashboard(
            Instant generatedAt,
            ConfigurationView configuration,
            Statistics statistics,
            List<AuthorView> authors,
            List<CategoryView> categories,
            List<BookView> books,
            List<CopyView> copies,
            List<LoanView> loans,
            List<ReservationView> reservations,
            List<FineView> fines,
            List<StudentOption> students) {}

    public record Statistics(
            long books,
            long copies,
            long availableCopies,
            long activeLoans,
            long overdueLoans,
            long activeReservations,
            long openFines) {}

    public record ConfigurationCommand(
            boolean fineEnabled,
            BigDecimal finePerOverdueDay,
            BigDecimal maximumFine,
            int defaultLoanDays,
            int reservationHoldDays) {}

    public record ConfigurationView(
            boolean fineEnabled,
            BigDecimal finePerOverdueDay,
            BigDecimal maximumFine,
            int defaultLoanDays,
            int reservationHoldDays) {}

    public record AuthorCommand(String displayName, String sortName, String biography) {}

    public record AuthorView(UUID id, String displayName, String sortName, String biography) {}

    public record CategoryCommand(String code, String name, String color) {}

    public record CategoryView(UUID id, String code, String name, String color, boolean active) {}

    public record BookCommand(
            UUID categoryId,
            String isbn,
            String title,
            String subtitle,
            String publisher,
            Integer publicationYear,
            String languageCode,
            String description,
            String coverKey,
            List<UUID> authorIds) {}

    public record BookAuthorView(UUID bookId, UUID authorId, String authorName, int position) {}

    public record BookView(
            UUID id,
            UUID categoryId,
            String categoryCode,
            String categoryName,
            String categoryColor,
            String isbn,
            String title,
            String subtitle,
            String publisher,
            Integer publicationYear,
            String languageCode,
            String description,
            String coverKey,
            boolean active,
            int copyCount,
            int availableCount,
            List<BookAuthorView> authors) {}

    public record CopyCommand(
            UUID locationId,
            String accessionNumber,
            String barcode,
            String qrCode,
            LocalDate acquiredOn,
            String condition,
            String notes) {}

    public record CopyView(
            UUID id,
            UUID bookId,
            String bookTitle,
            UUID locationId,
            String locationCode,
            String locationName,
            String accessionNumber,
            String barcode,
            String qrCode,
            LocalDate acquiredOn,
            String condition,
            String status,
            String notes) {}

    public record LoanCommand(
            UUID copyId,
            UUID studentId,
            LocalDate loanedOn,
            LocalDate dueOn,
            String conditionOut,
            String note) {}

    public record ReturnCommand(LocalDate returnedOn, String conditionIn, String note) {}

    public record LoanView(
            UUID id,
            UUID copyId,
            UUID bookId,
            String accessionNumber,
            String barcode,
            String bookTitle,
            UUID studentId,
            String studentName,
            UUID reservationId,
            LocalDate loanedOn,
            LocalDate dueOn,
            LocalDate returnedOn,
            String conditionOut,
            String conditionIn,
            String status,
            String note,
            boolean overdue,
            UUID fineId,
            BigDecimal fineAmount,
            String fineStatus) {}

    public record ReservationCommand(UUID bookId, UUID studentId) {}

    public record ReservationView(
            UUID id,
            UUID bookId,
            String bookTitle,
            UUID studentId,
            String studentName,
            UUID readyCopyId,
            String accessionNumber,
            int queuePosition,
            String status,
            Instant reservedAt,
            Instant readyAt,
            Instant expiresAt,
            Instant fulfilledAt,
            Instant cancelledAt) {}

    public record FineResolutionCommand(String resolution, String note) {}

    public record FineView(
            UUID id,
            UUID loanId,
            UUID studentId,
            String studentName,
            String bookTitle,
            int overdueDays,
            BigDecimal amount,
            String currency,
            String status,
            String resolutionNote,
            Instant assessedAt,
            Instant resolvedAt) {}

    public record StudentOption(UUID id, String displayName, String studentNumber) {}

    public record PortalDashboard(
            Instant generatedAt, ConfigurationView configuration, List<BorrowerView> borrowers) {}

    public record BorrowerView(
            StudentOption student,
            List<LoanView> loans,
            List<ReservationView> reservations,
            List<FineView> fines) {}
}
