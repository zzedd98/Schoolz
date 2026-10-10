package ma.schoolz.erp.billing.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.billing.application.OnlinePaymentService;
import ma.schoolz.erp.billing.application.OnlinePaymentService.OnlinePaymentView;
import ma.schoolz.erp.billing.application.OnlinePaymentService.OnlineRefundView;
import ma.schoolz.erp.billing.application.OnlinePaymentService.RefundOnlinePaymentCommand;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/online-payments")
public class OnlinePaymentController {
    private final OnlinePaymentService payments;

    public OnlinePaymentController(OnlinePaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public List<OnlinePaymentView> payments(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) UUID accountId) {
        return payments.payments(actor, accountId);
    }

    @PostMapping("/{paymentId}/verify")
    public OnlinePaymentView verify(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID paymentId,
            HttpServletRequest request) {
        return payments.verify(actor, paymentId, request.getRemoteAddr());
    }

    @PostMapping("/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public OnlineRefundView refund(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RefundOnlinePaymentCommand command,
            HttpServletRequest request) {
        return payments.refund(actor, paymentId, idempotencyKey, command, request.getRemoteAddr());
    }
}
