package ma.schoolz.erp.documents.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.documents.application.DocumentSignatureService;
import ma.schoolz.erp.documents.application.DocumentSignatureService.CreateWorkflowCommand;
import ma.schoolz.erp.documents.application.DocumentSignatureService.SignatureDashboard;
import ma.schoolz.erp.documents.application.DocumentSignatureService.WorkflowView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-signatures")
public class DocumentSignaturesController {
    private final DocumentSignatureService signatures;

    public DocumentSignaturesController(DocumentSignatureService signatures) {
        this.signatures = signatures;
    }

    @GetMapping("/dashboard")
    public SignatureDashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return signatures.dashboard(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowView create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CreateWorkflowCommand command,
            HttpServletRequest request) {
        return signatures.create(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/{workflowId}/send")
    public WorkflowView send(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID workflowId,
            HttpServletRequest request) {
        return signatures.send(actor, workflowId, request.getRemoteAddr());
    }
}
