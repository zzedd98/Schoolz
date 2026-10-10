package ma.schoolz.erp.inventory.api;

import ma.schoolz.erp.inventory.application.InventoryConflictException;
import ma.schoolz.erp.inventory.application.InventoryNotFoundException;
import ma.schoolz.erp.inventory.application.InventoryValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.inventory")
public class InventoryExceptionHandler {
    @ExceptionHandler(InventoryValidationException.class)
    ProblemDetail validation(InventoryValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Inventaire invalide", exception.getMessage());
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    ProblemDetail notFound(InventoryNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND, "Élément d’inventaire introuvable", exception.getMessage());
    }

    @ExceptionHandler(InventoryConflictException.class)
    ProblemDetail conflict(InventoryConflictException exception) {
        return problem(
                HttpStatus.CONFLICT, "Opération d’inventaire impossible", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
