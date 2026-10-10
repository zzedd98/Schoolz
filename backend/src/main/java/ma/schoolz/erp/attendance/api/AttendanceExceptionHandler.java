package ma.schoolz.erp.attendance.api;

import ma.schoolz.erp.attendance.application.AttendanceNotFoundException;
import ma.schoolz.erp.attendance.application.AttendanceValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.attendance")
public class AttendanceExceptionHandler {
    @ExceptionHandler(AttendanceValidationException.class)
    ProblemDetail validation(AttendanceValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Opération de vie scolaire invalide", exception);
    }

    @ExceptionHandler(AttendanceNotFoundException.class)
    ProblemDetail notFound(AttendanceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource de vie scolaire introuvable", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
