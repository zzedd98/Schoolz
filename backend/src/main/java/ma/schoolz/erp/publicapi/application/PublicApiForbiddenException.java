package ma.schoolz.erp.publicapi.application;

public class PublicApiForbiddenException extends RuntimeException {
    public PublicApiForbiddenException(String message) {
        super(message);
    }
}
