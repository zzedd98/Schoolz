package ma.schoolz.erp.billing.api;

import ma.schoolz.erp.billing.application.BillingConflictException;
import ma.schoolz.erp.billing.application.BillingNotFoundException;
import ma.schoolz.erp.billing.application.BillingValidationException;
import ma.schoolz.erp.billing.application.OnlinePaymentSignatureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "ma.schoolz.erp.billing")
public class BillingExceptionHandler {
    @ExceptionHandler(BillingValidationException.class)
    ProblemDetail validation(BillingValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Opération financière invalide", exception);
    }

    @ExceptionHandler(BillingConflictException.class)
    ProblemDetail conflict(BillingConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflit du ledger famille", exception);
    }

    @ExceptionHandler(BillingNotFoundException.class)
    ProblemDetail notFound(BillingNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Ressource financière introuvable", exception);
    }

    @ExceptionHandler(OnlinePaymentSignatureException.class)
    ProblemDetail invalidSignature(OnlinePaymentSignatureException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Signature de paiement invalide", exception);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, RuntimeException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
