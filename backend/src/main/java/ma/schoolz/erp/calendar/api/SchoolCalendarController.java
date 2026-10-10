package ma.schoolz.erp.calendar.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import ma.schoolz.erp.calendar.application.SchoolCalendarService;
import ma.schoolz.erp.calendar.application.SchoolCalendarService.CalendarView;
import ma.schoolz.erp.calendar.application.SchoolCalendarService.EventCommand;
import ma.schoolz.erp.calendar.application.SchoolCalendarService.EventView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
public class SchoolCalendarController {
    private final SchoolCalendarService calendar;

    public SchoolCalendarController(SchoolCalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    public CalendarView calendar(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID campusId) {
        return calendar.calendar(actor, from, to, type, campusId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventView create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody EventCommand command,
            HttpServletRequest request) {
        return calendar.create(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/{eventId}/cancel")
    public EventView cancel(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        return calendar.cancel(actor, eventId, request.getRemoteAddr());
    }
}
