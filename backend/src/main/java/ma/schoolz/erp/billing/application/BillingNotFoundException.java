package ma.schoolz.erp.billing.application;

public class BillingNotFoundException extends RuntimeException {
    public BillingNotFoundException(String message) {
        super(message);
    }
}
