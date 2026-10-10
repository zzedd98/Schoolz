package ma.schoolz.erp.publicapi.application;

public class PublicApiRateLimitException extends RuntimeException {
    public PublicApiRateLimitException(String message) {
        super(message);
    }
}
