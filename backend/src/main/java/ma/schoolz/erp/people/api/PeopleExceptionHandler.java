package ma.schoolz.erp.people.api;

import ma.schoolz.erp.people.application.StudentConflictException;
import ma.schoolz.erp.people.application.StudentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class PeopleExceptionHandler {

    @ExceptionHandler(StudentNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Student not found");
    }

    @ExceptionHandler(StudentConflictException.class)
    ProblemDetail conflict(StudentConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
