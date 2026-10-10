package ma.schoolz.erp.identity.application;

public final class IdentityNotFoundException extends RuntimeException {
    public IdentityNotFoundException() {
        super("Identity resource not found");
    }
}
