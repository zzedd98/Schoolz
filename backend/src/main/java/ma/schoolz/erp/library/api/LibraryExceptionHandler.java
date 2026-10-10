package ma.schoolz.erp.library.api;

import ma.schoolz.erp.library.application.LibraryConflictException;
import ma.schoolz.erp.library.application.LibraryNotFoundException;
import ma.schoolz.erp.library.application.LibraryValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.library")
public class LibraryExceptionHandler {
    @ExceptionHandler(LibraryValidationException.class)
    ProblemDetail validation(LibraryValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Opération bibliothèque invalide", exception);
    }

    @ExceptionHandler(LibraryNotFoundException.class)
    ProblemDetail notFound(LibraryNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource bibliothèque introuvable", exception);
    }

    @ExceptionHandler(LibraryConflictException.class)
    ProblemDetail conflict(LibraryConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Transition bibliothèque impossible", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
