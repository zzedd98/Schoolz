package ma.schoolz.erp.childcare.api;

import java.time.LocalDate;
import java.util.List;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.childcare.application.ChildcareService;
import ma.schoolz.erp.childcare.application.ChildcareService.DailyReport;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/childcare")
public class ChildcarePortalController {
    private final AdmissionPortalAccess authentication;
    private final ChildcareService childcare;

    public ChildcarePortalController(
            AdmissionPortalAccess authentication, ChildcareService childcare) {
        this.authentication = authentication;
        this.childcare = childcare;
    }

    @GetMapping("/reports")
    public List<DailyReport> reports(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestParam(required = false) LocalDate date) {
        return childcare.parentReports(authentication.require(rawToken), date);
    }
}
