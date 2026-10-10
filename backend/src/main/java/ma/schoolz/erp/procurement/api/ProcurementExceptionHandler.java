package ma.schoolz.erp.procurement.api;

import ma.schoolz.erp.procurement.application.ProcurementConflictException;
import ma.schoolz.erp.procurement.application.ProcurementNotFoundException;
import ma.schoolz.erp.procurement.application.ProcurementValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.procurement")
public class ProcurementExceptionHandler {
    @ExceptionHandler(ProcurementValidationException.class)
    ProblemDetail validation(ProcurementValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Achat invalide", exception.getMessage());
    }

    @ExceptionHandler(ProcurementNotFoundException.class)
    ProblemDetail notFound(ProcurementNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource achat introuvable", exception.getMessage());
    }

    @ExceptionHandler(ProcurementConflictException.class)
    ProblemDetail conflict(ProcurementConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Transition achat impossible", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
