package ma.schoolz.erp.procurement.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.ExpenseCommand;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.ExpenseView;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierCommand;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.procurement.application.ProcurementService;
import ma.schoolz.erp.procurement.application.ProcurementService.ConfigurationCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.ConfigurationView;
import ma.schoolz.erp.procurement.application.ProcurementService.Dashboard;
import ma.schoolz.erp.procurement.application.ProcurementService.DecisionCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.InvoiceCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.InvoiceMatchView;
import ma.schoolz.erp.procurement.application.ProcurementService.OrderCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.OrderView;
import ma.schoolz.erp.procurement.application.ProcurementService.PaymentCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.PaymentView;
import ma.schoolz.erp.procurement.application.ProcurementService.ReceiptCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.ReceiptView;
import ma.schoolz.erp.procurement.application.ProcurementService.RequestCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.RequestView;
import ma.schoolz.erp.procurement.application.ProcurementService.ThresholdCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.ThresholdView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement")
public class ProcurementController {
    private final ProcurementService procurement;

    public ProcurementController(ProcurementService procurement) {
        this.procurement = procurement;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return procurement.dashboard(actor);
    }

    @PutMapping("/configuration")
    public ConfigurationView configure(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ConfigurationCommand command,
            HttpServletRequest request) {
        return procurement.configure(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/approval-thresholds")
    @ResponseStatus(HttpStatus.CREATED)
    public ThresholdView createThreshold(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ThresholdCommand command,
            HttpServletRequest request) {
        return procurement.createThreshold(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierView createSupplier(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SupplierCommand command,
            HttpServletRequest request) {
        return procurement.createSupplier(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView createRequest(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RequestCommand command,
            HttpServletRequest request) {
        return procurement.createRequest(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/requests/{requestId}/submit")
    public RequestView submitRequest(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID requestId,
            HttpServletRequest request) {
        return procurement.submitRequest(actor, requestId, request.getRemoteAddr());
    }

    @PostMapping("/requests/{requestId}/decision")
    public RequestView decideRequest(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID requestId,
            @RequestBody DecisionCommand command,
            HttpServletRequest request) {
        return procurement.decideRequest(actor, requestId, command, request.getRemoteAddr());
    }

    @PostMapping("/requests/{requestId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView createOrder(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID requestId,
            @RequestBody OrderCommand command,
            HttpServletRequest request) {
        return procurement.createOrder(actor, requestId, command, request.getRemoteAddr());
    }

    @PostMapping("/orders/{orderId}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptView receiveGoods(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID orderId,
            @RequestBody ReceiptCommand command,
            HttpServletRequest request) {
        return procurement.receiveGoods(actor, orderId, command, request.getRemoteAddr());
    }

    @PostMapping("/orders/{orderId}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceMatchView recordInvoice(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID orderId,
            @RequestBody InvoiceCommand command,
            HttpServletRequest request) {
        return procurement.recordInvoice(actor, orderId, command, request.getRemoteAddr());
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView recordExpense(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ExpenseCommand command,
            HttpServletRequest request) {
        return procurement.recordExpense(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/invoice-matches/{invoiceMatchId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView payInvoice(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID invoiceMatchId,
            @RequestBody PaymentCommand command,
            HttpServletRequest request) {
        return procurement.payInvoice(actor, invoiceMatchId, command, request.getRemoteAddr());
    }
}
