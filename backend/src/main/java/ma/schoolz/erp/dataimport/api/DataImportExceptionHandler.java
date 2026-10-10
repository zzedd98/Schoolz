package ma.schoolz.erp.dataimport.api;

import ma.schoolz.erp.dataimport.application.ImportNotFoundException;
import ma.schoolz.erp.dataimport.application.ImportValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class DataImportExceptionHandler {

    @ExceptionHandler(ImportNotFoundException.class)
    ProblemDetail notFound(ImportNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ImportValidationException.class)
    ProblemDetail invalid(ImportValidationException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }
}
