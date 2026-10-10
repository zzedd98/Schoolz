package ma.schoolz.erp.tasks.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.tasks.application.TaskService;
import ma.schoolz.erp.tasks.application.TaskService.AssignCommand;
import ma.schoolz.erp.tasks.application.TaskService.Dashboard;
import ma.schoolz.erp.tasks.application.TaskService.StatusCommand;
import ma.schoolz.erp.tasks.application.TaskService.TaskCommand;
import ma.schoolz.erp.tasks.application.TaskService.TaskView;
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
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tasks.dashboard(actor, status, priority, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskView create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody TaskCommand command,
            HttpServletRequest request) {
        return tasks.create(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/{taskId}/status")
    public TaskView status(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID taskId,
            @RequestBody StatusCommand command,
            HttpServletRequest request) {
        return tasks.updateStatus(actor, taskId, command, request.getRemoteAddr());
    }

    @PutMapping("/{taskId}/assignee")
    public TaskView assign(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID taskId,
            @RequestBody AssignCommand command,
            HttpServletRequest request) {
        return tasks.assign(actor, taskId, command, request.getRemoteAddr());
    }
}
