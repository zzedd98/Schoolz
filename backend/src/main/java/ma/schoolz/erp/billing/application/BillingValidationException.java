package ma.schoolz.erp.billing.application;

public class BillingValidationException extends RuntimeException {
    public BillingValidationException(String message) {
        super(message);
    }
}
