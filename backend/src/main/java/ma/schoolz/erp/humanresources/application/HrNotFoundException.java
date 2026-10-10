package ma.schoolz.erp.humanresources.application;

public class HrNotFoundException extends RuntimeException {
    public HrNotFoundException(String message) {
        super(message);
    }
}
