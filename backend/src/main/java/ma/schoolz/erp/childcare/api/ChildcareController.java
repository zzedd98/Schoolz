package ma.schoolz.erp.childcare.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.childcare.application.ChildcareService;
import ma.schoolz.erp.childcare.application.ChildcareService.ChildView;
import ma.schoolz.erp.childcare.application.ChildcareService.Dashboard;
import ma.schoolz.erp.childcare.application.ChildcareService.DepartureCommand;
import ma.schoolz.erp.childcare.application.ChildcareService.EventCommand;
import ma.schoolz.erp.childcare.application.ChildcareService.GroupView;
import ma.schoolz.erp.childcare.application.ChildcareService.ProfileCommand;
import ma.schoolz.erp.childcare.application.ChildcareService.PublishCommand;
import ma.schoolz.erp.childcare.application.ChildcareService.TimeCommand;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/childcare")
public class ChildcareController {
    private final ChildcareService childcare;

    public ChildcareController(ChildcareService childcare) {
        this.childcare = childcare;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) UUID groupId) {
        return childcare.dashboard(actor, date, groupId);
    }

    @GetMapping("/groups")
    public List<GroupView> groups(@AuthenticationPrincipal SchoolPrincipal actor) {
        return childcare.groups(actor);
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ChildView profile(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ProfileCommand command,
            HttpServletRequest request) {
        return childcare.createProfile(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/children/{studentId}/arrival")
    public ChildView arrival(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody(required = false) TimeCommand command,
            HttpServletRequest request) {
        return childcare.arrival(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/children/{studentId}/departure")
    public ChildView departure(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody DepartureCommand command,
            HttpServletRequest request) {
        return childcare.departure(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/children/{studentId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ChildView event(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody EventCommand command,
            HttpServletRequest request) {
        return childcare.addEvent(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/children/{studentId}/publish")
    public ChildView publish(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody PublishCommand command,
            HttpServletRequest request) {
        return childcare.publish(actor, studentId, command, request.getRemoteAddr());
    }
}
