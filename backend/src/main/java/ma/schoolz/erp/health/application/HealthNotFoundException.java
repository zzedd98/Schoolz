package ma.schoolz.erp.health.application;

public class HealthNotFoundException extends RuntimeException {
    public HealthNotFoundException(String message) {
        super(message);
    }
}
