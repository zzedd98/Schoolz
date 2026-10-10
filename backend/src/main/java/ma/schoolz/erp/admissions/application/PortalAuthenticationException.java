package ma.schoolz.erp.admissions.application;

public class PortalAuthenticationException extends RuntimeException {
    public PortalAuthenticationException() {
        super("Session du portail invalide ou expirée");
    }
}
