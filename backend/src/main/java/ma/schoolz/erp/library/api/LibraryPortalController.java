package ma.schoolz.erp.library.api;

import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.library.application.LibraryService;
import ma.schoolz.erp.library.application.LibraryService.PortalDashboard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LibraryPortalController {
    private final AdmissionPortalAccess authentication;
    private final LibraryService library;

    public LibraryPortalController(AdmissionPortalAccess authentication, LibraryService library) {
        this.authentication = authentication;
        this.library = library;
    }

    @GetMapping("/api/v1/student-portal/library")
    public PortalDashboard student(@AuthenticationPrincipal SchoolPrincipal actor) {
        return library.studentPortal(actor);
    }

    @GetMapping("/api/v1/public/library")
    public PortalDashboard family(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestParam(required = false) UUID studentId) {
        return library.familyPortal(authentication.require(rawToken), studentId);
    }
}
