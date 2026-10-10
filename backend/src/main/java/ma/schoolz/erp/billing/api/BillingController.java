package ma.schoolz.erp.billing.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.billing.application.BillingService;
import ma.schoolz.erp.billing.application.BillingService.AccountCommand;
import ma.schoolz.erp.billing.application.BillingService.CreditCommand;
import ma.schoolz.erp.billing.application.BillingService.FamilyAccountView;
import ma.schoolz.erp.billing.application.BillingService.FamilyLedger;
import ma.schoolz.erp.billing.application.BillingService.FinanceDashboard;
import ma.schoolz.erp.billing.application.BillingService.FinancialAdjustmentCommand;
import ma.schoolz.erp.billing.application.BillingService.InvoiceCommand;
import ma.schoolz.erp.billing.application.BillingService.InvoiceView;
import ma.schoolz.erp.billing.application.BillingService.PaymentCommand;
import ma.schoolz.erp.billing.application.BillingService.PaymentView;
import ma.schoolz.erp.billing.application.BillingService.ReceiptView;
import ma.schoolz.erp.billing.application.BillingService.RefundCommand;
import ma.schoolz.erp.billing.application.BillingService.RefundView;
import ma.schoolz.erp.billing.application.BillingService.ReversalCommand;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
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
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/dashboard")
    public FinanceDashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return billing.dashboard(actor);
    }

    @GetMapping("/accounts/{accountId}")
    public FamilyLedger account(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID accountId) {
        return billing.ledger(actor, accountId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyAccountView createAccount(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AccountCommand command,
            HttpServletRequest request) {
        return billing.createAccount(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/accounts/{accountId}/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceView createInvoice(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestBody InvoiceCommand command,
            HttpServletRequest request) {
        return billing.createInvoice(actor, accountId, command, request.getRemoteAddr());
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    public InvoiceView issueInvoice(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID invoiceId,
            HttpServletRequest request) {
        return billing.issueInvoice(actor, invoiceId, request.getRemoteAddr());
    }

    @PostMapping("/accounts/{accountId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView recordPayment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestBody PaymentCommand command,
            HttpServletRequest request) {
        return billing.recordPayment(actor, accountId, command, request.getRemoteAddr());
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView reversePayment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID paymentId,
            @RequestBody ReversalCommand command,
            HttpServletRequest request) {
        return billing.reversePayment(actor, paymentId, command, request.getRemoteAddr());
    }

    @PostMapping("/accounts/{accountId}/credits")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyLedger createCredit(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestBody CreditCommand command,
            HttpServletRequest request) {
        billing.createCredit(actor, accountId, command, request.getRemoteAddr());
        return billing.ledger(actor, accountId);
    }

    @PostMapping("/accounts/{accountId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundView refund(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestBody RefundCommand command,
            HttpServletRequest request) {
        return billing.refund(actor, accountId, command, request.getRemoteAddr());
    }

    @PostMapping("/accounts/{accountId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyLedger adjustment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID accountId,
            @RequestBody FinancialAdjustmentCommand command,
            HttpServletRequest request) {
        billing.adjust(actor, accountId, command, request.getRemoteAddr());
        return billing.ledger(actor, accountId);
    }

    @GetMapping("/receipts/{receiptId}")
    public ReceiptView receipt(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID receiptId) {
        return billing.receipt(actor, receiptId);
    }
}
