package ma.schoolz.erp.enrollment.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.FamilyCommand;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.PaymentCommand;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.RenewalCommand;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.ServicesCommand;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.SignatureCommand;
import ma.schoolz.erp.enrollment.application.EnrollmentWorkflowService.WorkflowView;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/public/enrollments")
public class EnrollmentPortalController {
    private final AdmissionPortalAccess authentication;
    private final EnrollmentWorkflowService enrollments;

    public EnrollmentPortalController(
            AdmissionPortalAccess authentication, EnrollmentWorkflowService enrollments) {
        this.authentication = authentication;
        this.enrollments = enrollments;
    }

    @GetMapping
    public List<WorkflowView> workflows(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return enrollments.portalWorkflows(authentication.require(rawToken));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowView start(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestBody InitialCommand command) {
        return enrollments.startInitial(authentication.require(rawToken), command.applicationId());
    }

    @PostMapping("/re-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowView renew(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestBody RenewalCommand command) {
        return enrollments.startRenewal(authentication.require(rawToken), command);
    }

    @PostMapping("/{workflowId}/contract/accept")
    public WorkflowView acceptContract(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId) {
        return enrollments.acceptContract(authentication.require(rawToken), workflowId);
    }

    @PutMapping("/{workflowId}/family")
    public WorkflowView family(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestBody FamilyCommand command) {
        return enrollments.confirmFamily(authentication.require(rawToken), workflowId, command);
    }

    @PutMapping("/{workflowId}/services")
    public WorkflowView services(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestBody ServicesCommand command) {
        return enrollments.confirmServices(authentication.require(rawToken), workflowId, command);
    }

    @PostMapping(value = "/{workflowId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowView document(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestParam String documentType,
            @RequestPart MultipartFile file) {
        return enrollments.upload(authentication.require(rawToken), workflowId, documentType, file);
    }

    @PostMapping("/{workflowId}/signature")
    public WorkflowView signature(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestBody SignatureCommand command,
            HttpServletRequest request) {
        return enrollments.sign(
                authentication.require(rawToken), workflowId, command, request.getRemoteAddr());
    }

    @PostMapping("/{workflowId}/fee-payments")
    public WorkflowView pay(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID workflowId,
            @RequestBody PaymentCommand command) {
        return enrollments.pay(authentication.require(rawToken), workflowId, command);
    }

    public record InitialCommand(UUID applicationId) {}
}
