package ma.schoolz.erp.transport.api;

import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.transport.application.TransportService;
import ma.schoolz.erp.transport.application.TransportService.FamilyTransportDashboard;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/transport")
public class FamilyTransportController {
    private final AdmissionPortalAccess authentication;
    private final TransportService transport;

    public FamilyTransportController(
            AdmissionPortalAccess authentication, TransportService transport) {
        this.authentication = authentication;
        this.transport = transport;
    }

    @GetMapping
    public FamilyTransportDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return transport.familyDashboard(authentication.require(rawToken));
    }
}
