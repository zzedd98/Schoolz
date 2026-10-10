package ma.schoolz.erp.communications.api;

import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.communications.application.NotificationCenterService;
import ma.schoolz.erp.communications.application.NotificationCenterService.CenterResponse;
import ma.schoolz.erp.communications.application.NotificationCenterService.NotificationItem;
import ma.schoolz.erp.communications.application.NotificationCenterService.PreferenceCommand;
import ma.schoolz.erp.communications.application.NotificationCenterService.PreferenceView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-center")
public class NotificationCenterController {
    private final NotificationCenterService notifications;

    public NotificationCenterController(NotificationCenterService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public CenterResponse center(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notifications.center(actor, status, page, size);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationItem read(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID notificationId) {
        return notifications.markRead(actor, notificationId);
    }

    @PatchMapping("/{notificationId}/archive")
    public NotificationItem archive(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID notificationId) {
        return notifications.archive(actor, notificationId);
    }

    @PutMapping("/preferences")
    public List<PreferenceView> preferences(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody PreferenceCommand command) {
        return notifications.savePreferences(actor, command);
    }
}
