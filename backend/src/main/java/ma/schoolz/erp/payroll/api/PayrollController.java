package ma.schoolz.erp.payroll.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.payroll.application.PayrollService;
import ma.schoolz.erp.payroll.application.PayrollService.AccountingExportView;
import ma.schoolz.erp.payroll.application.PayrollService.AdjustmentCommand;
import ma.schoolz.erp.payroll.application.PayrollService.AdjustmentView;
import ma.schoolz.erp.payroll.application.PayrollService.ComponentCommand;
import ma.schoolz.erp.payroll.application.PayrollService.ComponentView;
import ma.schoolz.erp.payroll.application.PayrollService.Dashboard;
import ma.schoolz.erp.payroll.application.PayrollService.PayslipRecord;
import ma.schoolz.erp.payroll.application.PayrollService.PayslipSummary;
import ma.schoolz.erp.payroll.application.PayrollService.PeriodCommand;
import ma.schoolz.erp.payroll.application.PayrollService.PeriodView;
import ma.schoolz.erp.payroll.application.PayrollService.ProfileCommand;
import ma.schoolz.erp.payroll.application.PayrollService.RuleCommand;
import ma.schoolz.erp.payroll.application.PayrollService.RuleView;
import ma.schoolz.erp.payroll.application.PayrollService.RunCommand;
import ma.schoolz.erp.payroll.application.PayrollService.RunView;
import ma.schoolz.erp.payroll.application.PayrollService.SalaryProfileView;
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
@RequestMapping("/api/v1/payroll")
public class PayrollController {
    private final PayrollService payroll;

    public PayrollController(PayrollService payroll) {
        this.payroll = payroll;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor, HttpServletRequest request) {
        return payroll.dashboard(actor, request.getRemoteAddr());
    }

    @PostMapping("/periods")
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodView createPeriod(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody PeriodCommand command,
            HttpServletRequest request) {
        return payroll.createPeriod(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/components")
    @ResponseStatus(HttpStatus.CREATED)
    public ComponentView createComponent(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ComponentCommand command,
            HttpServletRequest request) {
        return payroll.createComponent(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView createRule(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RuleCommand command,
            HttpServletRequest request) {
        return payroll.createRule(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryProfileView createProfile(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ProfileCommand command,
            HttpServletRequest request) {
        return payroll.createProfile(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentView createAdjustment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AdjustmentCommand command,
            HttpServletRequest request) {
        return payroll.createAdjustment(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public RunView createRun(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RunCommand command,
            HttpServletRequest request) {
        return payroll.createRun(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/runs/{runId}/calculate")
    public RunView calculateRun(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID runId,
            HttpServletRequest request) {
        return payroll.calculateRun(actor, runId, request.getRemoteAddr());
    }

    @PostMapping("/runs/{runId}/finalize")
    public RunView finalizeRun(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID runId,
            HttpServletRequest request) {
        return payroll.finalizeRun(actor, runId, request.getRemoteAddr());
    }

    @GetMapping("/runs/{runId}/payslips")
    public List<PayslipSummary> payslips(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID runId,
            HttpServletRequest request) {
        return payroll.payslips(actor, runId, request.getRemoteAddr());
    }

    @GetMapping("/payslips/{payslipId}")
    public PayslipRecord payslip(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID payslipId,
            HttpServletRequest request) {
        return payroll.payslip(actor, payslipId, request.getRemoteAddr());
    }

    @PostMapping("/runs/{runId}/accounting-export")
    public AccountingExportView exportAccounting(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "CSV") String format,
            HttpServletRequest request) {
        return payroll.exportAccounting(actor, runId, format, request.getRemoteAddr());
    }
}
