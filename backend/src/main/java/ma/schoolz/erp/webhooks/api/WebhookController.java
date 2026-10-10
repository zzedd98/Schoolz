package ma.schoolz.erp.webhooks.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.webhooks.application.WebhookService;
import ma.schoolz.erp.webhooks.application.WebhookService.CreatedSubscription;
import ma.schoolz.erp.webhooks.application.WebhookService.Dashboard;
import ma.schoolz.erp.webhooks.application.WebhookService.DeliveryView;
import ma.schoolz.erp.webhooks.application.WebhookService.EventCommand;
import ma.schoolz.erp.webhooks.application.WebhookService.EventView;
import ma.schoolz.erp.webhooks.application.WebhookService.ProcessResult;
import ma.schoolz.erp.webhooks.application.WebhookService.ReasonCommand;
import ma.schoolz.erp.webhooks.application.WebhookService.SubscriptionCommand;
import ma.schoolz.erp.webhooks.application.WebhookService.SubscriptionView;
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
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return webhooks.dashboard(actor);
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedSubscription create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SubscriptionCommand command,
            HttpServletRequest request) {
        return webhooks.create(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/subscriptions/{subscriptionId}/revoke")
    public SubscriptionView revoke(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID subscriptionId,
            @RequestBody ReasonCommand command,
            HttpServletRequest request) {
        return webhooks.revoke(actor, subscriptionId, command, request.getRemoteAddr());
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventView publish(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody EventCommand command,
            HttpServletRequest request) {
        return webhooks.publish(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/deliveries/process-due")
    public ProcessResult processDue(@AuthenticationPrincipal SchoolPrincipal actor) {
        return webhooks.processDue(actor);
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public DeliveryView retry(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID deliveryId,
            @RequestBody ReasonCommand command,
            HttpServletRequest request) {
        return webhooks.retry(actor, deliveryId, command, request.getRemoteAddr());
    }
}
