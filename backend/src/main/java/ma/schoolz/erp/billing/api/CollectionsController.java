package ma.schoolz.erp.billing.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.billing.application.CollectionsService;
import ma.schoolz.erp.billing.application.CollectionsService.AutomaticRunResult;
import ma.schoolz.erp.billing.application.CollectionsService.CollectionActionView;
import ma.schoolz.erp.billing.application.CollectionsService.CollectionsDashboard;
import ma.schoolz.erp.billing.application.CollectionsService.InstallmentPlanCommand;
import ma.schoolz.erp.billing.application.CollectionsService.InstallmentPlanView;
import ma.schoolz.erp.billing.application.CollectionsService.ManualReminderCommand;
import ma.schoolz.erp.billing.application.CollectionsService.ReminderRuleCommand;
import ma.schoolz.erp.billing.application.CollectionsService.ReminderRuleView;
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
@RequestMapping("/api/v1/billing/collections")
public class CollectionsController {
    private final CollectionsService collections;

    public CollectionsController(CollectionsService collections) {
        this.collections = collections;
    }

    @GetMapping("/dashboard")
    public CollectionsDashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate asOf) {
        return collections.dashboard(actor, asOf);
    }

    @GetMapping("/accounts/{accountId}/installment-plans")
    public List<InstallmentPlanView> accountPlans(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestParam(required = false) LocalDate asOf) {
        return collections.accountPlans(actor, accountId, asOf);
    }

    @PostMapping("/accounts/{accountId}/installment-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public InstallmentPlanView createPlan(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestBody InstallmentPlanCommand command,
            HttpServletRequest request) {
        return collections.createPlan(actor, accountId, command, request.getRemoteAddr());
    }

    @PostMapping("/reminder-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderRuleView createRule(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ReminderRuleCommand command,
            HttpServletRequest request) {
        return collections.createReminderRule(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/reminder-rules/{ruleId}")
    public ReminderRuleView updateRule(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID ruleId,
            @RequestBody ReminderRuleCommand command,
            HttpServletRequest request) {
        return collections.updateReminderRule(actor, ruleId, command, request.getRemoteAddr());
    }

    @PostMapping("/payment-schedules/{scheduleId}/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionActionView manualReminder(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @RequestBody ManualReminderCommand command,
            HttpServletRequest request) {
        return collections.manualReminder(actor, scheduleId, command, request.getRemoteAddr());
    }

    @PostMapping("/reminders/run")
    public AutomaticRunResult runAutomatic(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) LocalDate asOf,
            HttpServletRequest request) {
        return collections.runAutomaticReminders(actor, asOf, request.getRemoteAddr());
    }
}
