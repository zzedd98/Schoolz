package ma.schoolz.erp.transport.api;

import ma.schoolz.erp.transport.application.TransportNotFoundException;
import ma.schoolz.erp.transport.application.TransportValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {TransportController.class, FamilyTransportController.class})
public class TransportExceptionHandler {
    @ExceptionHandler(TransportValidationException.class)
    ProblemDetail validation(TransportValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Transport invalide", exception);
    }

    @ExceptionHandler(TransportNotFoundException.class)
    ProblemDetail notFound(TransportNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Transport introuvable", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
