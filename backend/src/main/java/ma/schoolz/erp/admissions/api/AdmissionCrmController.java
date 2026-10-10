package ma.schoolz.erp.admissions.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.admissions.application.AdmissionCrmService;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.ActivityCommand;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.ActivityView;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.AdmissionBoard;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.CampaignCommand;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.Dashboard;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.DecisionCommand;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.DecisionView;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.DocumentReview;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.DocumentReviewCommand;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.MoveCommand;
import ma.schoolz.erp.admissions.application.AdmissionCrmService.ProspectView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.CampaignView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ProspectCommand;
import ma.schoolz.erp.admissions.application.AdmissionStage;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admissions")
public class AdmissionCrmController {

    private final AdmissionCrmService admissions;

    public AdmissionCrmController(AdmissionCrmService admissions) {
        this.admissions = admissions;
    }

    @GetMapping("/campaigns")
    public List<CampaignView> campaigns(@AuthenticationPrincipal SchoolPrincipal actor) {
        return admissions.campaigns(actor);
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignView campaign(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CampaignCommand command,
            HttpServletRequest request) {
        return admissions.createCampaign(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/prospects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProspectView prospect(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ProspectCommand command,
            HttpServletRequest request) {
        return admissions.createProspect(actor, command, request.getRemoteAddr());
    }

    @GetMapping("/board")
    public AdmissionBoard board(
            @AuthenticationPrincipal SchoolPrincipal actor, @RequestParam UUID campaignId) {
        return admissions.board(actor, campaignId);
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor, @RequestParam UUID campaignId) {
        return admissions.dashboard(actor, campaignId);
    }

    @PatchMapping("/pipeline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody MoveCommand command,
            HttpServletRequest request) {
        admissions.move(
                actor,
                command.cardType(),
                command.cardId(),
                AdmissionStage.from(command.targetStatus()),
                command.comment(),
                request.getRemoteAddr());
    }

    @PostMapping("/applications/{applicationId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityView activity(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID applicationId,
            @RequestBody ActivityCommand command) {
        return admissions.schedule(actor, applicationId, command);
    }

    @PostMapping("/applications/{applicationId}/decision")
    public DecisionView decision(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID applicationId,
            @RequestBody DecisionCommand command,
            HttpServletRequest request) {
        return admissions.decide(actor, applicationId, command, request.getRemoteAddr());
    }

    @PatchMapping("/documents/{documentId}/review")
    public DocumentReview review(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID documentId,
            @RequestBody DocumentReviewCommand command) {
        return admissions.reviewDocument(actor, documentId, command);
    }
}
