package ma.schoolz.erp.health.application;

public class HealthValidationException extends RuntimeException {
    public HealthValidationException(String message) {
        super(message);
    }
}
