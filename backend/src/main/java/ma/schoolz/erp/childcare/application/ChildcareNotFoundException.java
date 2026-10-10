package ma.schoolz.erp.childcare.application;

public class ChildcareNotFoundException extends RuntimeException {
    public ChildcareNotFoundException(String message) {
        super(message);
    }
}
