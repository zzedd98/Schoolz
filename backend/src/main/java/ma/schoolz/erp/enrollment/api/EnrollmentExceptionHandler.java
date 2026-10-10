package ma.schoolz.erp.enrollment.api;

import ma.schoolz.erp.enrollment.application.EnrollmentNotFoundException;
import ma.schoolz.erp.enrollment.application.EnrollmentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.enrollment")
public class EnrollmentExceptionHandler {
    @ExceptionHandler(EnrollmentValidationException.class)
    ProblemDetail validation(EnrollmentValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Parcours d'inscription invalide", exception);
    }

    @ExceptionHandler(EnrollmentNotFoundException.class)
    ProblemDetail notFound(EnrollmentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Parcours d'inscription introuvable", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
