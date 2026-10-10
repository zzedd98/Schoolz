package ma.schoolz.erp.attendance.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.attendance.application.AttendanceService;
import ma.schoolz.erp.attendance.application.AttendanceService.IncidentCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.IncidentView;
import ma.schoolz.erp.attendance.application.AttendanceService.JustificationView;
import ma.schoolz.erp.attendance.application.AttendanceService.ObservationCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.ObservationView;
import ma.schoolz.erp.attendance.application.AttendanceService.RecordsCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.ReferenceData;
import ma.schoolz.erp.attendance.application.AttendanceService.ReviewCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.SanctionCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.SanctionView;
import ma.schoolz.erp.attendance.application.AttendanceService.SessionCommand;
import ma.schoolz.erp.attendance.application.AttendanceService.SessionSummary;
import ma.schoolz.erp.attendance.application.AttendanceService.SessionView;
import ma.schoolz.erp.attendance.application.AttendanceService.Statistics;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {
    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @GetMapping("/references")
    public ReferenceData references(@AuthenticationPrincipal SchoolPrincipal actor) {
        return attendance.references(actor);
    }

    @GetMapping("/sessions")
    public List<SessionSummary> sessions(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate date) {
        return attendance.sessions(actor, date);
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionView session(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID sessionId) {
        return attendance.session(actor, sessionId);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView createSession(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SessionCommand command,
            HttpServletRequest request) {
        return attendance.createSession(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/sessions/{sessionId}/records")
    public SessionView records(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID sessionId,
            @RequestBody RecordsCommand command,
            HttpServletRequest request) {
        return attendance.updateRecords(actor, sessionId, command, request.getRemoteAddr());
    }

    @PostMapping("/sessions/{sessionId}/submit")
    public SessionView submit(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        return attendance.submit(actor, sessionId, request.getRemoteAddr());
    }

    @GetMapping("/justifications")
    public List<JustificationView> justifications(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) String status) {
        return attendance.justifications(actor, status);
    }

    @PatchMapping("/justifications/{justificationId}")
    public JustificationView review(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID justificationId,
            @RequestBody ReviewCommand command,
            HttpServletRequest request) {
        return attendance.review(actor, justificationId, command, request.getRemoteAddr());
    }

    @GetMapping("/statistics")
    public Statistics statistics(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID classroomId,
            @RequestParam(required = false) UUID studentId) {
        return attendance.statistics(actor, from, to, classroomId, studentId);
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentView incident(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody IncidentCommand command,
            HttpServletRequest request) {
        return attendance.createIncident(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/incidents/{incidentId}/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    public SanctionView sanction(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID incidentId,
            @RequestBody SanctionCommand command,
            HttpServletRequest request) {
        return attendance.createSanction(actor, incidentId, command, request.getRemoteAddr());
    }

    @PostMapping("/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationView observation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ObservationCommand command,
            HttpServletRequest request) {
        return attendance.createObservation(actor, command, request.getRemoteAddr());
    }
}
