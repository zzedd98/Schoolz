package ma.schoolz.erp.identity.api;

import java.net.URI;
import ma.schoolz.erp.identity.application.AuthenticationFailedException;
import ma.schoolz.erp.identity.application.IdentityConflictException;
import ma.schoolz.erp.identity.application.IdentityNotFoundException;
import ma.schoolz.erp.identity.application.PasswordPolicyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.identity")
public class IdentityExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    ProblemDetail authenticationFailed() {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed",
                "The supplied credentials or token could not be accepted.");
    }

    @ExceptionHandler(IdentityNotFoundException.class)
    ProblemDetail notFound() {
        return problem(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                "The requested resource does not exist.");
    }

    @ExceptionHandler(IdentityConflictException.class)
    ProblemDetail conflict(IdentityConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(PasswordPolicyException.class)
    ProblemDetail passwordPolicy(PasswordPolicyException exception) {
        ProblemDetail detail =
                problem(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Password policy violation",
                        "The password does not satisfy the security policy.");
        detail.setProperty("violations", exception.violations());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail detail =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Validation failed",
                        "One or more request fields are invalid.");
        detail.setProperty(
                "errors",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        java.util.Map.of(
                                                "field", error.getField(),
                                                "code", error.getCode()))
                        .toList());
        return detail;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://schoolz.ma/problems/" + status.value()));
        return problem;
    }
}
