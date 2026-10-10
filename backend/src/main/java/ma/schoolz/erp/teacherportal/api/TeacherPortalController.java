package ma.schoolz.erp.teacherportal.api;

import java.time.LocalDate;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.teacherportal.application.TeacherPortalService;
import ma.schoolz.erp.teacherportal.application.TeacherPortalService.TeacherDashboard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher-portal")
public class TeacherPortalController {
    private final TeacherPortalService portal;

    public TeacherPortalController(TeacherPortalService portal) {
        this.portal = portal;
    }

    @GetMapping("/today")
    public TeacherDashboard today(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate date) {
        return portal.dashboard(actor, date);
    }
}
