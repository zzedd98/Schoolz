package ma.schoolz.erp.privacy.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.ConsentCommand;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.Dashboard;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.ExportCommand;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.ExportView;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.HostingCommand;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.HostingView;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.RequestCommand;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.RequestStatusCommand;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.RequestView;
import ma.schoolz.erp.privacy.application.PrivacyGovernanceService.RetentionCommand;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {
    private final PrivacyGovernanceService privacy;

    public PrivacyController(PrivacyGovernanceService privacy) {
        this.privacy = privacy;
    }

    @GetMapping
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return privacy.dashboard(actor);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView createRequest(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RequestCommand command,
            HttpServletRequest request) {
        return privacy.createRequest(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/requests/{requestId}/status")
    public RequestView updateRequest(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID requestId,
            @RequestBody RequestStatusCommand command,
            HttpServletRequest request) {
        return privacy.updateRequest(actor, requestId, command, request.getRemoteAddr());
    }

    @PostMapping("/consents")
    @ResponseStatus(HttpStatus.CREATED)
    public void recordConsent(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ConsentCommand command,
            HttpServletRequest request) {
        privacy.recordConsent(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/retention")
    public void configureRetention(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RetentionCommand command,
            HttpServletRequest request) {
        privacy.configureRetention(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.CREATED)
    public ExportView prepareExport(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ExportCommand command,
            HttpServletRequest request) {
        return privacy.prepareExport(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/hosting")
    public HostingView configureHosting(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody HostingCommand command,
            HttpServletRequest request) {
        return privacy.configureHosting(actor, command, request.getRemoteAddr());
    }
}
