package ma.schoolz.erp.studentportal.api;

import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.studentportal.application.StudentPortalService;
import ma.schoolz.erp.studentportal.application.StudentPortalService.HomeworkStatusCommand;
import ma.schoolz.erp.studentportal.application.StudentPortalService.StudentDashboard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student-portal")
public class StudentPortalController {
    private final StudentPortalService portal;

    public StudentPortalController(StudentPortalService portal) {
        this.portal = portal;
    }

    @GetMapping("/dashboard")
    public StudentDashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate date) {
        return portal.dashboard(actor, date);
    }

    @GetMapping("/all")
    public StudentDashboard all(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate date) {
        return portal.fullDashboard(actor, date);
    }

    @PutMapping("/homework/{homeworkId}/status")
    public StudentDashboard markHomework(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID homeworkId,
            @RequestBody HomeworkStatusCommand command) {
        portal.markHomework(actor, homeworkId, command);
        return portal.dashboard(actor, null);
    }
}
