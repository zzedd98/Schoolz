package ma.schoolz.erp.cafeteria.api;

import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.cafeteria.application.CafeteriaService;
import ma.schoolz.erp.cafeteria.application.CafeteriaService.Dashboard;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/cafeteria")
public class FamilyCafeteriaController {
    private final AdmissionPortalAccess auth;
    private final CafeteriaService service;

    public FamilyCafeteriaController(AdmissionPortalAccess a, CafeteriaService s) {
        auth = a;
        service = s;
    }

    @GetMapping
    public Dashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false) String token) {
        return service.family(auth.require(token));
    }
}
