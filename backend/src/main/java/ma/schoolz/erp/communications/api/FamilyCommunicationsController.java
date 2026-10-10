package ma.schoolz.erp.communications.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.communications.application.CommunicationsService;
import ma.schoolz.erp.communications.application.CommunicationsService.ConversationView;
import ma.schoolz.erp.communications.application.CommunicationsService.FamilyCommunicationDashboard;
import ma.schoolz.erp.communications.application.CommunicationsService.PreferenceView;
import ma.schoolz.erp.communications.application.CommunicationsService.ReplyCommand;
import ma.schoolz.erp.communications.application.CommunicationsService.UpdatePreferencesCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/communications")
public class FamilyCommunicationsController {
    private final AdmissionPortalAccess authentication;
    private final CommunicationsService communications;

    public FamilyCommunicationsController(
            AdmissionPortalAccess authentication, CommunicationsService communications) {
        this.authentication = authentication;
        this.communications = communications;
    }

    @GetMapping
    public FamilyCommunicationDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return communications.familyDashboard(authentication.require(rawToken));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView reply(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID conversationId,
            @RequestBody ReplyCommand command,
            HttpServletRequest request) {
        return communications.familyReply(
                authentication.require(rawToken), conversationId, command, request.getRemoteAddr());
    }

    @PutMapping("/preferences")
    public List<PreferenceView> preferences(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestBody UpdatePreferencesCommand command) {
        PortalIdentity parent = authentication.require(rawToken);
        return communications.updatePreferences(parent, command);
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID notificationId) {
        communications.readFamilyNotification(authentication.require(rawToken), notificationId);
    }
}
