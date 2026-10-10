package ma.schoolz.erp.documents.api;

import ma.schoolz.erp.documents.application.DocumentNotFoundException;
import ma.schoolz.erp.documents.application.DocumentStorageException;
import ma.schoolz.erp.documents.application.DocumentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {
            DocumentsController.class,
            FamilyDocumentsController.class,
            DocumentSignaturesController.class,
            FamilyDocumentSignaturesController.class
        })
public class DocumentsExceptionHandler {
    @ExceptionHandler(DocumentValidationException.class)
    ProblemDetail validation(DocumentValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Document invalide", exception);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Document introuvable", exception);
    }

    @ExceptionHandler(DocumentStorageException.class)
    ProblemDetail storage(DocumentStorageException exception) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE, "Stockage documentaire indisponible", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
