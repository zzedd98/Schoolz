package ma.schoolz.erp.earlywarning.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.earlywarning.application.EarlyWarningService;
import ma.schoolz.erp.earlywarning.application.EarlyWarningService.AlertAction;
import ma.schoolz.erp.earlywarning.application.EarlyWarningService.AlertView;
import ma.schoolz.erp.earlywarning.application.EarlyWarningService.Dashboard;
import ma.schoolz.erp.earlywarning.application.EarlyWarningService.RuleCommand;
import ma.schoolz.erp.earlywarning.application.EarlyWarningService.RuleView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/early-warning")
public class EarlyWarningController {
    private final EarlyWarningService service;

    public EarlyWarningController(EarlyWarningService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return service.dashboard(actor, asOf);
    }

    @PostMapping("/evaluate")
    public Dashboard evaluate(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf,
            HttpServletRequest request) {
        return service.evaluate(actor, asOf, request.getRemoteAddr());
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView createRule(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RuleCommand command,
            HttpServletRequest request) {
        return service.createRule(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public AlertView acknowledge(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID alertId,
            @RequestBody AlertAction command,
            HttpServletRequest request) {
        return service.acknowledge(actor, alertId, command, request.getRemoteAddr());
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public AlertView resolve(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID alertId,
            @RequestBody AlertAction command,
            HttpServletRequest request) {
        return service.resolve(actor, alertId, command, request.getRemoteAddr());
    }
}
