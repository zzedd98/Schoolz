package ma.schoolz.erp.provisioning.api;

import ma.schoolz.erp.provisioning.application.ProvisioningConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SchoolProvisioningController.class)
class ProvisioningExceptionHandler {

    @ExceptionHandler(ProvisioningConflictException.class)
    ProblemDetail conflict(ProvisioningConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
