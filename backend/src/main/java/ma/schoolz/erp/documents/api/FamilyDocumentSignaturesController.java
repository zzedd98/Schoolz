package ma.schoolz.erp.documents.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.documents.application.DocumentSignatureService;
import ma.schoolz.erp.documents.application.DocumentSignatureService.AcceptCommand;
import ma.schoolz.erp.documents.application.DocumentSignatureService.FamilySignatureDashboard;
import ma.schoolz.erp.documents.application.DocumentSignatureService.SignCommand;
import ma.schoolz.erp.documents.application.DocumentSignatureService.WorkflowView;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/document-signatures")
public class FamilyDocumentSignaturesController {
    private final AdmissionPortalAccess authentication;
    private final DocumentSignatureService signatures;

    public FamilyDocumentSignaturesController(
            AdmissionPortalAccess authentication, DocumentSignatureService signatures) {
        this.authentication = authentication;
        this.signatures = signatures;
    }

    @GetMapping
    public FamilySignatureDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return signatures.familyDashboard(authentication.require(rawToken));
    }

    @PostMapping("/{workflowId}/view")
    public WorkflowView view(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            HttpServletRequest request) {
        return signatures.view(
                authentication.require(rawToken), workflowId, request.getRemoteAddr());
    }

    @PostMapping("/{workflowId}/accept")
    public WorkflowView accept(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestBody AcceptCommand command,
            HttpServletRequest request) {
        return signatures.accept(
                authentication.require(rawToken), workflowId, command, request.getRemoteAddr());
    }

    @PostMapping("/{workflowId}/sign")
    public WorkflowView sign(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestBody SignCommand command,
            HttpServletRequest request) {
        return signatures.sign(
                authentication.require(rawToken), workflowId, command, request.getRemoteAddr());
    }
}
