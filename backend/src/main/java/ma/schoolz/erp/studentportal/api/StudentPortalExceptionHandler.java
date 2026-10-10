package ma.schoolz.erp.studentportal.api;

import ma.schoolz.erp.studentportal.application.StudentPortalNotFoundException;
import ma.schoolz.erp.studentportal.application.StudentPortalValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StudentPortalController.class)
public class StudentPortalExceptionHandler {
    @ExceptionHandler(StudentPortalNotFoundException.class)
    ProblemDetail notFound(StudentPortalNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Profil élève introuvable", exception);
    }

    @ExceptionHandler(StudentPortalValidationException.class)
    ProblemDetail validation(StudentPortalValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Date du portail invalide", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
