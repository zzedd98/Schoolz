package ma.schoolz.erp.admissions.application;

public class AdmissionConflictException extends RuntimeException {
    public AdmissionConflictException(String message) {
        super(message);
    }
}
