package ma.schoolz.erp.customfields.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.customfields.application.CustomFieldService;
import ma.schoolz.erp.customfields.application.CustomFieldService.Dashboard;
import ma.schoolz.erp.customfields.application.CustomFieldService.DefinitionCommand;
import ma.schoolz.erp.customfields.application.CustomFieldService.DefinitionView;
import ma.schoolz.erp.customfields.application.CustomFieldService.ValueCommand;
import ma.schoolz.erp.customfields.application.CustomFieldService.ValueView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/custom-fields")
public class CustomFieldController {
    private final CustomFieldService service;

    public CustomFieldController(CustomFieldService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(defaultValue = "STUDENT") String entityType,
            @RequestParam(required = false) UUID entityId) {
        return service.dashboard(actor, entityType, entityId);
    }

    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public DefinitionView create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody DefinitionCommand command,
            HttpServletRequest request) {
        return service.create(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/definitions/{definitionId}")
    public DefinitionView update(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID definitionId,
            @RequestBody DefinitionCommand command,
            HttpServletRequest request) {
        return service.update(actor, definitionId, command, request.getRemoteAddr());
    }

    @PutMapping("/definitions/{definitionId}/entities/{entityId}")
    public ValueView putValue(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID definitionId,
            @PathVariable UUID entityId,
            @RequestBody ValueCommand command,
            HttpServletRequest request) {
        return service.putValue(actor, definitionId, entityId, command, request.getRemoteAddr());
    }

    @DeleteMapping("/definitions/{definitionId}/entities/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearValue(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID definitionId,
            @PathVariable UUID entityId,
            HttpServletRequest request) {
        service.clearValue(actor, definitionId, entityId, request.getRemoteAddr());
    }
}
