package ma.schoolz.erp.activities.api;

import ma.schoolz.erp.activities.application.ActivityService;
import ma.schoolz.erp.activities.application.ActivityService.*;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/activities")
public class FamilyActivityController {
    private final AdmissionPortalAccess auth;
    private final ActivityService service;

    public FamilyActivityController(AdmissionPortalAccess a, ActivityService s) {
        auth = a;
        service = s;
    }

    @GetMapping
    public Dashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false) String t) {
        return service.family(auth.require(t));
    }

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResult enroll(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false) String t,
            @RequestBody EnrollCommand c) {
        return service.enrollFamily(auth.require(t), c);
    }
}
