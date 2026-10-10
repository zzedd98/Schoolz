package ma.schoolz.erp.communications.application;

public class CommunicationNotFoundException extends RuntimeException {
    public CommunicationNotFoundException(String message) {
        super(message);
    }
}
