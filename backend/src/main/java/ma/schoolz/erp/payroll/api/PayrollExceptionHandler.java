package ma.schoolz.erp.payroll.api;

import ma.schoolz.erp.payroll.application.PayrollNotFoundException;
import ma.schoolz.erp.payroll.application.PayrollValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.payroll")
public class PayrollExceptionHandler {
    @ExceptionHandler(PayrollValidationException.class)
    ProblemDetail validation(PayrollValidationException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Configuration de paie invalide");
        return problem;
    }

    @ExceptionHandler(PayrollNotFoundException.class)
    ProblemDetail notFound(PayrollNotFoundException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Donnée de paie introuvable");
        return problem;
    }
}
