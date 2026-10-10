package ma.schoolz.erp.earlywarning.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.earlywarning")
public class EarlyWarningExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Règle ou transition d’alerte invalide");
        return problem;
    }
}
