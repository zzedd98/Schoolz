package ma.schoolz.erp.timetable.api;

import java.util.Map;
import ma.schoolz.erp.timetable.application.TimetableConflictException;
import ma.schoolz.erp.timetable.application.TimetableNotFoundException;
import ma.schoolz.erp.timetable.application.TimetableValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TimetableController.class)
public class TimetableExceptionHandler {
    @ExceptionHandler(TimetableNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(TimetableNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "TIMETABLE_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(TimetableValidationException.class)
    ResponseEntity<Map<String, Object>> validation(TimetableValidationException exception) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("code", "TIMETABLE_VALIDATION", "message", exception.getMessage()));
    }

    @ExceptionHandler(TimetableConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(TimetableConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "code",
                                "TIMETABLE_CONFLICT",
                                "message",
                                exception.getMessage(),
                                "conflicts",
                                exception.conflicts()));
    }
}
