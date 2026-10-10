package ma.schoolz.erp.admissions.api;

import ma.schoolz.erp.admissions.application.AdmissionConflictException;
import ma.schoolz.erp.admissions.application.AdmissionNotFoundException;
import ma.schoolz.erp.admissions.application.AdmissionValidationException;
import ma.schoolz.erp.admissions.application.PortalAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdmissionExceptionHandler {

    @ExceptionHandler(AdmissionValidationException.class)
    ProblemDetail validation(AdmissionValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Données d'admission invalides", exception);
    }

    @ExceptionHandler(AdmissionNotFoundException.class)
    ProblemDetail notFound(AdmissionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource d'admission introuvable", exception);
    }

    @ExceptionHandler(AdmissionConflictException.class)
    ProblemDetail conflict(AdmissionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflit d'admission", exception);
    }

    @ExceptionHandler(PortalAuthenticationException.class)
    ProblemDetail unauthorized(PortalAuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Session parent invalide", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
