package ma.schoolz.erp.teacherportal.api;

import ma.schoolz.erp.teacherportal.application.TeacherPortalNotFoundException;
import ma.schoolz.erp.teacherportal.application.TeacherPortalValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TeacherPortalController.class)
public class TeacherPortalExceptionHandler {
    @ExceptionHandler(TeacherPortalNotFoundException.class)
    ProblemDetail notFound(TeacherPortalNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Profil professeur introuvable", exception);
    }

    @ExceptionHandler(TeacherPortalValidationException.class)
    ProblemDetail validation(TeacherPortalValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Date du portail invalide", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
