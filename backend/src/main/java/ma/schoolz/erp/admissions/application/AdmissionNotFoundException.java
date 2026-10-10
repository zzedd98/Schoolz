package ma.schoolz.erp.admissions.application;

public class AdmissionNotFoundException extends RuntimeException {
    public AdmissionNotFoundException(String message) {
        super(message);
    }
}
