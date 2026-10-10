package ma.schoolz.erp.enrollment.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.ClassOption;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.WorkflowView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentAdminController {
    private final EnrollmentWorkflowService enrollments;

    public EnrollmentAdminController(EnrollmentWorkflowService enrollments) {
        this.enrollments = enrollments;
    }

    @GetMapping
    public List<WorkflowView> workflows(@AuthenticationPrincipal SchoolPrincipal actor) {
        return enrollments.adminWorkflows(actor);
    }

    @GetMapping("/{workflowId}/classes")
    public List<ClassOption> classes(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID workflowId) {
        return enrollments.classes(actor, workflowId);
    }

    @PatchMapping("/{workflowId}/class")
    public WorkflowView assignClass(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID workflowId,
            @RequestBody ClassCommand command) {
        return enrollments.assignClass(actor, workflowId, command.classroomId());
    }

    @PostMapping("/{workflowId}/activate")
    public WorkflowView activate(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID workflowId,
            HttpServletRequest request) {
        return enrollments.activate(actor, workflowId, request.getRemoteAddr());
    }

    public record ClassCommand(UUID classroomId) {}
}
