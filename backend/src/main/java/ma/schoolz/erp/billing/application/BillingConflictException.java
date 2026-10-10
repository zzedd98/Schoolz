package ma.schoolz.erp.billing.application;

public class BillingConflictException extends RuntimeException {
    public BillingConflictException(String message) {
        super(message);
    }
}
