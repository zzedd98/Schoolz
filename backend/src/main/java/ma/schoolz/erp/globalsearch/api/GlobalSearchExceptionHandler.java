package ma.schoolz.erp.globalsearch.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.globalsearch")
public class GlobalSearchExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Recherche invalide");
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
