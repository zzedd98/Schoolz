package ma.schoolz.erp.billing.application;

public class OnlinePaymentSignatureException extends RuntimeException {
    public OnlinePaymentSignatureException(String message) {
        super(message);
    }
}
