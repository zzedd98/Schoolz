package ma.schoolz.erp.humanresources.api;

import ma.schoolz.erp.humanresources.application.HrNotFoundException;
import ma.schoolz.erp.humanresources.application.HrValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.humanresources")
public class HumanResourcesExceptionHandler {
    @ExceptionHandler(HrValidationException.class)
    ProblemDetail validation(HrValidationException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Donnée RH invalide");
        return problem;
    }

    @ExceptionHandler(HrNotFoundException.class)
    ProblemDetail notFound(HrNotFoundException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Donnée RH introuvable");
        return problem;
    }
}
