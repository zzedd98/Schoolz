package ma.schoolz.erp.accounting.api;

import ma.schoolz.erp.accounting.application.AccountingConflictException;
import ma.schoolz.erp.accounting.application.AccountingNotFoundException;
import ma.schoolz.erp.accounting.application.AccountingValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.accounting")
public class AccountingExceptionHandler {
    @ExceptionHandler(AccountingValidationException.class)
    ProblemDetail validation(AccountingValidationException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Opération comptable invalide");
        return problem;
    }

    @ExceptionHandler(AccountingConflictException.class)
    ProblemDetail conflict(AccountingConflictException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Conflit comptable");
        return problem;
    }

    @ExceptionHandler(AccountingNotFoundException.class)
    ProblemDetail notFound(AccountingNotFoundException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Donnée comptable introuvable");
        return problem;
    }
}
