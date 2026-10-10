package ma.schoolz.erp.workflow.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.workflow.application.WorkflowService;
import ma.schoolz.erp.workflow.application.WorkflowService.ActivationCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.Dashboard;
import ma.schoolz.erp.workflow.application.WorkflowService.DispatchView;
import ma.schoolz.erp.workflow.application.WorkflowService.EventCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.WorkflowCommand;
import ma.schoolz.erp.workflow.application.WorkflowService.WorkflowView;
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
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return service.dashboard(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowView create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody WorkflowCommand command,
            HttpServletRequest request) {
        return service.create(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/{workflowId}/activation")
    public WorkflowView activation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID workflowId,
            @RequestBody ActivationCommand command,
            HttpServletRequest request) {
        return service.activation(actor, workflowId, command, request.getRemoteAddr());
    }

    @PostMapping("/events")
    public DispatchView dispatch(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody EventCommand command,
            HttpServletRequest request) {
        return service.dispatch(actor, command, request.getRemoteAddr());
    }
}
