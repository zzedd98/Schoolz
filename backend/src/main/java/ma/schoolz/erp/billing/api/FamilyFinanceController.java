package ma.schoolz.erp.billing.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.billing.application.BillingService;
import ma.schoolz.erp.billing.application.BillingService.FamilyLedger;
import ma.schoolz.erp.billing.application.BillingService.ReceiptView;
import ma.schoolz.erp.billing.application.CollectionsService;
import ma.schoolz.erp.billing.application.CollectionsService.InstallmentPlanView;
import ma.schoolz.erp.billing.application.OnlinePaymentService;
import ma.schoolz.erp.billing.application.OnlinePaymentService.InitializeOnlinePaymentCommand;
import ma.schoolz.erp.billing.application.OnlinePaymentService.OnlinePaymentView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/finance")
public class FamilyFinanceController {
    private final AdmissionPortalAccess authentication;
    private final BillingService billing;
    private final CollectionsService collections;
    private final OnlinePaymentService onlinePayments;

    public FamilyFinanceController(
            AdmissionPortalAccess authentication,
            BillingService billing,
            CollectionsService collections,
            OnlinePaymentService onlinePayments) {
        this.authentication = authentication;
        this.billing = billing;
        this.collections = collections;
        this.onlinePayments = onlinePayments;
    }

    @GetMapping
    public FamilyLedger ledger(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return billing.familyLedger(authentication.require(rawToken));
    }

    @GetMapping("/receipts/{receiptId}")
    public ReceiptView receipt(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID receiptId) {
        return billing.familyReceipt(authentication.require(rawToken), receiptId);
    }

    @GetMapping("/installment-plans")
    public java.util.List<InstallmentPlanView> installmentPlans(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
                    java.time.LocalDate asOf) {
        return collections.familyPlans(authentication.require(rawToken), asOf);
    }

    @GetMapping("/online-payments")
    public java.util.List<OnlinePaymentView> onlinePayments(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return onlinePayments.familyPayments(authentication.require(rawToken));
    }

    @GetMapping("/online-payments/{paymentId}")
    public OnlinePaymentView onlinePayment(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID paymentId) {
        return onlinePayments.familyPayment(authentication.require(rawToken), paymentId);
    }

    @PostMapping("/online-payments")
    @ResponseStatus(HttpStatus.CREATED)
    public OnlinePaymentView initializeOnlinePayment(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody InitializeOnlinePaymentCommand command,
            HttpServletRequest request) {
        PortalIdentity parent = authentication.require(rawToken);
        return onlinePayments.initialize(parent, idempotencyKey, command, request.getRemoteAddr());
    }

    @PostMapping("/online-payments/{paymentId}/sandbox-complete")
    public OnlinePaymentView completeSandboxPayment(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID paymentId,
            HttpServletRequest request) {
        return onlinePayments.completeSandbox(
                authentication.require(rawToken), paymentId, request.getRemoteAddr());
    }
}
