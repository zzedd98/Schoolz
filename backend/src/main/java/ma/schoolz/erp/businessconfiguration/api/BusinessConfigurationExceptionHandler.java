package ma.schoolz.erp.businessconfiguration.api;

import ma.schoolz.erp.businessconfiguration.application.BusinessConfigurationConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.businessconfiguration")
public class BusinessConfigurationExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Configuration métier invalide");
        return problem;
    }

    @ExceptionHandler(BusinessConfigurationConflictException.class)
    ProblemDetail conflict(BusinessConfigurationConflictException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Configuration modifiée simultanément");
        return problem;
    }
}
