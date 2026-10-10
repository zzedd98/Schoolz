package ma.schoolz.erp.workflow.api;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.workflow")
public class WorkflowExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Workflow ou événement invalide");
        return problem;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate(DuplicateKeyException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT, "Ce code ou cet événement a déjà été traité.");
        problem.setTitle("Workflow déjà existant");
        return problem;
    }
}
