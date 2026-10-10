package ma.schoolz.erp.homework.api;

import ma.schoolz.erp.homework.application.HomeworkConflictException;
import ma.schoolz.erp.homework.application.HomeworkNotFoundException;
import ma.schoolz.erp.homework.application.HomeworkValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.homework")
public class HomeworkExceptionHandler {
    @ExceptionHandler(HomeworkValidationException.class)
    ProblemDetail validation(HomeworkValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Cahier de texte invalide", exception);
    }

    @ExceptionHandler(HomeworkConflictException.class)
    ProblemDetail conflict(HomeworkConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflit du cahier de texte", exception);
    }

    @ExceptionHandler(HomeworkNotFoundException.class)
    ProblemDetail notFound(HomeworkNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource du cahier de texte introuvable", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
