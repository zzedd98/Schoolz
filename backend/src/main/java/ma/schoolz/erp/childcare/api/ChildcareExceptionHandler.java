package ma.schoolz.erp.childcare.api;

import ma.schoolz.erp.childcare.application.ChildcareNotFoundException;
import ma.schoolz.erp.childcare.application.ChildcareValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.childcare")
public class ChildcareExceptionHandler {
    @ExceptionHandler(ChildcareValidationException.class)
    ProblemDetail validation(ChildcareValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Journal petite enfance invalide", exception);
    }

    @ExceptionHandler(ChildcareNotFoundException.class)
    ProblemDetail notFound(ChildcareNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Profil petite enfance introuvable", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
