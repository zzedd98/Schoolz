package ma.schoolz.erp.transport.application;

public class TransportNotFoundException extends RuntimeException {
    public TransportNotFoundException(String message) {
        super(message);
    }
}
