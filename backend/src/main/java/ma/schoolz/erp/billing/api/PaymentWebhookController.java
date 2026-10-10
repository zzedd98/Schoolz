package ma.schoolz.erp.billing.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.billing.application.OnlinePaymentService;
import ma.schoolz.erp.billing.application.OnlinePaymentService.WebhookReceipt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {
    private final OnlinePaymentService payments;

    public PaymentWebhookController(OnlinePaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{providerKey}")
    public WebhookReceipt webhook(
            @PathVariable String providerKey,
            @RequestHeader(name = "X-SchoolZ-Event-Id", required = false) String eventId,
            @RequestHeader(name = "X-SchoolZ-Signature", required = false) String signature,
            @RequestBody(required = false) String payload,
            HttpServletRequest request) {
        String effectiveEventId =
                eventId == null || eventId.isBlank()
                        ? "missing_" + UUID.randomUUID().toString().replace("-", "")
                        : eventId;
        return payments.handleWebhook(
                providerKey, effectiveEventId, signature, payload, request.getRemoteAddr());
    }
}
