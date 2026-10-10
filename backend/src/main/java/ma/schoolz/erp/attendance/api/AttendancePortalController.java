package ma.schoolz.erp.attendance.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.attendance.application.AttendanceService;
import ma.schoolz.erp.attendance.application.AttendanceService.JustificationCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.ParentAbsence;
import ma.schoolz.erp.attendance.application.AttendanceService.ParentDashboard;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/attendance")
public class AttendancePortalController {
    private final AdmissionPortalAccess authentication;
    private final AttendanceService attendance;

    public AttendancePortalController(
            AdmissionPortalAccess authentication, AttendanceService attendance) {
        this.authentication = authentication;
        this.attendance = attendance;
    }

    @GetMapping
    public ParentDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return attendance.parentDashboard(authentication.require(rawToken), from, to);
    }

    @PostMapping("/absences/{absenceId}/justifications")
    @ResponseStatus(HttpStatus.CREATED)
    public ParentAbsence justify(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID absenceId,
            @RequestBody JustificationCommand command,
            HttpServletRequest request) {
        return attendance.justify(
                authentication.require(rawToken), absenceId, command, request.getRemoteAddr());
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readNotification(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID notificationId) {
        attendance.readNotification(authentication.require(rawToken), notificationId);
    }
}
