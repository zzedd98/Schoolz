package ma.schoolz.erp.organization.api;

import ma.schoolz.erp.organization.application.OrganizationConflictException;
import ma.schoolz.erp.organization.application.OrganizationNotFoundException;
import ma.schoolz.erp.organization.application.OrganizationValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class OrganizationExceptionHandler {

    @ExceptionHandler(OrganizationNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(OrganizationConflictException.class)
    ProblemDetail conflict(OrganizationConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(OrganizationValidationException.class)
    ProblemDetail invalid(OrganizationValidationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
