package ma.schoolz.erp.admissions.application;

public class AdmissionValidationException extends RuntimeException {
    public AdmissionValidationException(String message) {
        super(message);
    }
}
