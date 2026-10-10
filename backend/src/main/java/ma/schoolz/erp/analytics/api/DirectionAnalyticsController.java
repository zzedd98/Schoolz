package ma.schoolz.erp.analytics.api;

import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.analytics.application.DirectionAnalyticsService;
import ma.schoolz.erp.analytics.application.DirectionAnalyticsService.DirectionDashboard;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class DirectionAnalyticsController {
    private final DirectionAnalyticsService analytics;

    public DirectionAnalyticsController(DirectionAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/direction")
    public DirectionDashboard direction(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf,
            @RequestParam(required = false) UUID campusId) {
        return analytics.direction(actor, asOf, campusId);
    }
}
