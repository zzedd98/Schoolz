package ma.schoolz.erp.health.api;

import ma.schoolz.erp.health.application.HealthNotFoundException;
import ma.schoolz.erp.health.application.HealthValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.health")
public class HealthExceptionHandler {
    @ExceptionHandler(HealthValidationException.class)
    ProblemDetail validation(HealthValidationException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Donnée de santé invalide");
        return problem;
    }

    @ExceptionHandler(HealthNotFoundException.class)
    ProblemDetail notFound(HealthNotFoundException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Donnée de santé introuvable");
        return problem;
    }
}
