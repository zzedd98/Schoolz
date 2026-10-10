package ma.schoolz.erp.businessconfiguration.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.businessconfiguration.application.BusinessConfigurationService;
import ma.schoolz.erp.businessconfiguration.application.BusinessConfigurationService.Dashboard;
import ma.schoolz.erp.businessconfiguration.application.BusinessConfigurationService.ItemView;
import ma.schoolz.erp.businessconfiguration.application.BusinessConfigurationService.ResetCommand;
import ma.schoolz.erp.businessconfiguration.application.BusinessConfigurationService.SettingCommand;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-configuration")
public class BusinessConfigurationController {
    private final BusinessConfigurationService configuration;

    public BusinessConfigurationController(BusinessConfigurationService configuration) {
        this.configuration = configuration;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) String category) {
        return configuration.dashboard(actor, category);
    }

    @PutMapping("/items/{itemId}")
    public ItemView update(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID itemId,
            @RequestBody SettingCommand command,
            HttpServletRequest request) {
        return configuration.update(actor, itemId, command, request.getRemoteAddr());
    }

    @PostMapping("/items/{itemId}/reset")
    public ItemView reset(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID itemId,
            @RequestBody(required = false) ResetCommand command,
            HttpServletRequest request) {
        return configuration.reset(actor, itemId, command, request.getRemoteAddr());
    }
}
