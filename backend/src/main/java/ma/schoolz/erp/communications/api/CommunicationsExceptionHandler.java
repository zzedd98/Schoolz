package ma.schoolz.erp.communications.api;

import ma.schoolz.erp.communications.application.CommunicationNotFoundException;
import ma.schoolz.erp.communications.application.CommunicationValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {
            CommunicationsController.class,
            FamilyCommunicationsController.class,
            NotificationCenterController.class
        })
public class CommunicationsExceptionHandler {
    @ExceptionHandler(CommunicationValidationException.class)
    ProblemDetail validation(CommunicationValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Communication invalide", exception);
    }

    @ExceptionHandler(CommunicationNotFoundException.class)
    ProblemDetail notFound(CommunicationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Communication introuvable", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
