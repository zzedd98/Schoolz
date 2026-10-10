package ma.schoolz.erp.publicapi.api;

import ma.schoolz.erp.publicapi.application.PublicApiForbiddenException;
import ma.schoolz.erp.publicapi.application.PublicApiRateLimitException;
import ma.schoolz.erp.publicapi.application.PublicApiUnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.publicapi")
public class PublicApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail validation(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête API invalide", exception.getMessage());
    }

    @ExceptionHandler(PublicApiUnauthorizedException.class)
    ProblemDetail unauthorized(PublicApiUnauthorizedException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Clé API refusée", exception.getMessage());
    }

    @ExceptionHandler(PublicApiForbiddenException.class)
    ProblemDetail forbidden(PublicApiForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, "Scope API insuffisant", exception.getMessage());
    }

    @ExceptionHandler(PublicApiRateLimitException.class)
    ProblemDetail limited(PublicApiRateLimitException exception) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Quota API atteint", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
