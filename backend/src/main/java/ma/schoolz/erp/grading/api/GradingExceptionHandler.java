package ma.schoolz.erp.grading.api;

import java.time.Instant;
import ma.schoolz.erp.grading.application.GradingConflictException;
import ma.schoolz.erp.grading.application.GradingNotFoundException;
import ma.schoolz.erp.grading.application.GradingValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = GradingController.class)
public class GradingExceptionHandler {
    @ExceptionHandler(GradingValidationException.class)
    ResponseEntity<ApiError> validation(GradingValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(GradingNotFoundException.class)
    ResponseEntity<ApiError> notFound(GradingNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(GradingConflictException.class)
    ResponseEntity<ApiError> conflict(GradingConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    private static ResponseEntity<ApiError> response(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(
                        new ApiError(
                                Instant.now(), status.value(), status.getReasonPhrase(), message));
    }

    record ApiError(Instant timestamp, int status, String error, String message) {}
}
