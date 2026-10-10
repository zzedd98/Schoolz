package ma.schoolz.erp.dataimport.application;

public class ImportValidationException extends RuntimeException {
    public ImportValidationException(String message) {
        super(message);
    }
}
