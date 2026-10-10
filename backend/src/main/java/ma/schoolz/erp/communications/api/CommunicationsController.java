package ma.schoolz.erp.communications.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.communications.application.CommunicationsService;
import ma.schoolz.erp.communications.application.CommunicationsService.AnnouncementView;
import ma.schoolz.erp.communications.application.CommunicationsService.AutomatedNotificationCommand;
import ma.schoolz.erp.communications.application.CommunicationsService.CommunicationDashboard;
import ma.schoolz.erp.communications.application.CommunicationsService.ConversationView;
import ma.schoolz.erp.communications.application.CommunicationsService.NotificationView;
import ma.schoolz.erp.communications.application.CommunicationsService.PublishAnnouncementCommand;
import ma.schoolz.erp.communications.application.CommunicationsService.ReplyCommand;
import ma.schoolz.erp.communications.application.CommunicationsService.StartConversationCommand;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationsController {
    private final CommunicationsService communications;

    public CommunicationsController(CommunicationsService communications) {
        this.communications = communications;
    }

    @GetMapping("/dashboard")
    public CommunicationDashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return communications.dashboard(actor);
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementView publish(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody PublishAnnouncementCommand command,
            HttpServletRequest request) {
        return communications.publishAnnouncement(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView startConversation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody StartConversationCommand command,
            HttpServletRequest request) {
        return communications.startConversation(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView reply(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID conversationId,
            @RequestBody ReplyCommand command,
            HttpServletRequest request) {
        return communications.reply(actor, conversationId, command, request.getRemoteAddr());
    }

    @PostMapping("/automated-notifications")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationView automated(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AutomatedNotificationCommand command,
            HttpServletRequest request) {
        return communications.sendAutomated(actor, command, request.getRemoteAddr());
    }

    @PatchMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID notificationId) {
        communications.readInternalNotification(actor, notificationId);
    }
}
