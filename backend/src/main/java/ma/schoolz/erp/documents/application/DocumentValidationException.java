package ma.schoolz.erp.documents.application;

public class DocumentValidationException extends RuntimeException {
    public DocumentValidationException(String message) {
        super(message);
    }
}
