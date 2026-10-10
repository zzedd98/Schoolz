package ma.schoolz.erp.publicapi.application;

public class PublicApiUnauthorizedException extends RuntimeException {
    public PublicApiUnauthorizedException(String message) {
        super(message);
    }
}
