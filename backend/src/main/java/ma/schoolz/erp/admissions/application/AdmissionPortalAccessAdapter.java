package ma.schoolz.erp.admissions.application;

import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import org.springframework.stereotype.Service;

@Service
class AdmissionPortalAccessAdapter implements AdmissionPortalAccess {
    private final PortalAuthenticationService authentication;

    AdmissionPortalAccessAdapter(PortalAuthenticationService authentication) {
        this.authentication = authentication;
    }

    @Override
    public PortalIdentity require(String rawToken) {
        PortalAuthenticationService.PortalPrincipal principal = authentication.require(rawToken);
        return new PortalIdentity(
                principal.tenantId(),
                principal.accountId(),
                principal.email(),
                principal.displayName(),
                principal.locale());
    }
}
