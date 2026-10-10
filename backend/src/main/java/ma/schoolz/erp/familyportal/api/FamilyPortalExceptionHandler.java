package ma.schoolz.erp.familyportal.api;

import java.net.URI;
import ma.schoolz.erp.familyportal.application.FamilyPortalNotFoundException;
import ma.schoolz.erp.familyportal.application.FamilyPortalValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FamilyPortalController.class)
public class FamilyPortalExceptionHandler {
    @ExceptionHandler(FamilyPortalNotFoundException.class)
    ProblemDetail notFound(FamilyPortalNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource familiale introuvable", exception);
    }

    @ExceptionHandler(FamilyPortalValidationException.class)
    ProblemDetail validation(FamilyPortalValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête familiale invalide", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        detail.setType(URI.create("https://schoolz.ma/problems/family-portal"));
        return detail;
    }
}
