package ma.schoolz.erp.identity.application;

public final class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Authentication failed");
    }
}
