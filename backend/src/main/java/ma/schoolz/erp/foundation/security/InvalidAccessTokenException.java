package ma.schoolz.erp.foundation.security;

public final class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException() {
        super("Invalid access token");
    }
}
