package ma.schoolz.erp.grading.application;

public class GradingNotFoundException extends RuntimeException {
    public GradingNotFoundException(String message) {
        super(message);
    }
}
