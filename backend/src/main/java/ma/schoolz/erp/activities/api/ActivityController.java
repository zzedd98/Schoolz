package ma.schoolz.erp.activities.api;

import ma.schoolz.erp.activities.application.ActivityService;
import ma.schoolz.erp.activities.application.ActivityService.Dashboard;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {
    private final ActivityService service;

    public ActivityController(ActivityService s) {
        service = s;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal a) {
        return service.dashboard(a);
    }
}
