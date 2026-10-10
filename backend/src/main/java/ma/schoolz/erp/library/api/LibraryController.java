package ma.schoolz.erp.library.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.library.application.LibraryService;
import ma.schoolz.erp.library.application.LibraryService.AuthorCommand;
import ma.schoolz.erp.library.application.LibraryService.AuthorView;
import ma.schoolz.erp.library.application.LibraryService.BookCommand;
import ma.schoolz.erp.library.application.LibraryService.BookView;
import ma.schoolz.erp.library.application.LibraryService.CategoryCommand;
import ma.schoolz.erp.library.application.LibraryService.CategoryView;
import ma.schoolz.erp.library.application.LibraryService.ConfigurationCommand;
import ma.schoolz.erp.library.application.LibraryService.ConfigurationView;
import ma.schoolz.erp.library.application.LibraryService.CopyCommand;
import ma.schoolz.erp.library.application.LibraryService.CopyView;
import ma.schoolz.erp.library.application.LibraryService.Dashboard;
import ma.schoolz.erp.library.application.LibraryService.FineResolutionCommand;
import ma.schoolz.erp.library.application.LibraryService.FineView;
import ma.schoolz.erp.library.application.LibraryService.LoanCommand;
import ma.schoolz.erp.library.application.LibraryService.LoanView;
import ma.schoolz.erp.library.application.LibraryService.ReservationCommand;
import ma.schoolz.erp.library.application.LibraryService.ReservationView;
import ma.schoolz.erp.library.application.LibraryService.ReturnCommand;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {
    private final LibraryService library;

    public LibraryController(LibraryService library) {
        this.library = library;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return library.dashboard(actor);
    }

    @PutMapping("/configuration")
    public ConfigurationView configure(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ConfigurationCommand command,
            HttpServletRequest request) {
        return library.configure(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/authors")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorView createAuthor(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AuthorCommand command,
            HttpServletRequest request) {
        return library.createAuthor(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CategoryCommand command,
            HttpServletRequest request) {
        return library.createCategory(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    public BookView createBook(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody BookCommand command,
            HttpServletRequest request) {
        return library.createBook(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/books/{bookId}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public CopyView createCopy(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID bookId,
            @RequestBody CopyCommand command,
            HttpServletRequest request) {
        return library.createCopy(actor, bookId, command, request.getRemoteAddr());
    }

    @GetMapping("/copies/scan")
    public CopyView scan(
            @AuthenticationPrincipal SchoolPrincipal actor, @RequestParam String code) {
        return library.scan(actor, code);
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanView createLoan(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody LoanCommand command,
            HttpServletRequest request) {
        return library.createLoan(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/loans/{loanId}/return")
    public LoanView returnLoan(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID loanId,
            @RequestBody ReturnCommand command,
            HttpServletRequest request) {
        return library.returnLoan(actor, loanId, command, request.getRemoteAddr());
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationView createReservation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ReservationCommand command,
            HttpServletRequest request) {
        return library.createReservation(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationView cancelReservation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID reservationId,
            HttpServletRequest request) {
        return library.cancelReservation(actor, reservationId, request.getRemoteAddr());
    }

    @PostMapping("/fines/{fineId}/resolve")
    public FineView resolveFine(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID fineId,
            @RequestBody FineResolutionCommand command,
            HttpServletRequest request) {
        return library.resolveFine(actor, fineId, command, request.getRemoteAddr());
    }
}
