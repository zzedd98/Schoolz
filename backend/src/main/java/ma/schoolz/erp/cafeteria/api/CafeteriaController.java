package ma.schoolz.erp.cafeteria.api;

import jakarta.servlet.http.HttpServletRequest;
import ma.schoolz.erp.cafeteria.application.CafeteriaService;
import ma.schoolz.erp.cafeteria.application.CafeteriaService.*;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cafeteria")
public class CafeteriaController {
    private final CafeteriaService service;

    public CafeteriaController(CafeteriaService s) {
        service = s;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal a) {
        return service.dashboard(a);
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public Subscription subscribe(
            @AuthenticationPrincipal SchoolPrincipal a,
            @RequestBody SubscribeCommand c,
            HttpServletRequest r) {
        return service.subscribe(a, c, r.getRemoteAddr());
    }

    @PostMapping("/attendances")
    @ResponseStatus(HttpStatus.CREATED)
    public Attendance attend(
            @AuthenticationPrincipal SchoolPrincipal a,
            @RequestBody AttendanceCommand c,
            HttpServletRequest r) {
        return service.attend(a, c, r.getRemoteAddr());
    }
}
