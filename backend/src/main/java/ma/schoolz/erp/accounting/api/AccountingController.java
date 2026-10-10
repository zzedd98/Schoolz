package ma.schoolz.erp.accounting.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.accounting.application.AccountingService;
import ma.schoolz.erp.accounting.application.AccountingService.AccountCommand;
import ma.schoolz.erp.accounting.application.AccountingService.AccountView;
import ma.schoolz.erp.accounting.application.AccountingService.BankAccountCommand;
import ma.schoolz.erp.accounting.application.AccountingService.BankAccountView;
import ma.schoolz.erp.accounting.application.AccountingService.ConfigurationCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ConfigurationView;
import ma.schoolz.erp.accounting.application.AccountingService.CostCenterCommand;
import ma.schoolz.erp.accounting.application.AccountingService.CostCenterView;
import ma.schoolz.erp.accounting.application.AccountingService.Dashboard;
import ma.schoolz.erp.accounting.application.AccountingService.EntryView;
import ma.schoolz.erp.accounting.application.AccountingService.ExpenseCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ExpenseView;
import ma.schoolz.erp.accounting.application.AccountingService.FiscalPeriodCommand;
import ma.schoolz.erp.accounting.application.AccountingService.FiscalPeriodView;
import ma.schoolz.erp.accounting.application.AccountingService.JournalCommand;
import ma.schoolz.erp.accounting.application.AccountingService.JournalView;
import ma.schoolz.erp.accounting.application.AccountingService.MatchCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ReconciliationCommand;
import ma.schoolz.erp.accounting.application.AccountingService.ReconciliationView;
import ma.schoolz.erp.accounting.application.AccountingService.ReversalCommand;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierCommand;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierInvoiceCommand;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierInvoiceView;
import ma.schoolz.erp.accounting.application.AccountingService.SupplierView;
import ma.schoolz.erp.accounting.application.AccountingService.SyncResult;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
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
@RequestMapping("/api/v1/accounting")
public class AccountingController {
    private final AccountingService accounting;

    public AccountingController(AccountingService accounting) {
        this.accounting = accounting;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return accounting.dashboard(actor);
    }

    @PutMapping("/configuration")
    public ConfigurationView configure(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ConfigurationCommand command,
            HttpServletRequest request) {
        return accounting.configure(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView createAccount(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AccountCommand command,
            HttpServletRequest request) {
        return accounting.createAccount(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalView createJournal(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody JournalCommand command,
            HttpServletRequest request) {
        return accounting.createJournal(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/fiscal-periods")
    @ResponseStatus(HttpStatus.CREATED)
    public FiscalPeriodView createFiscalPeriod(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody FiscalPeriodCommand command,
            HttpServletRequest request) {
        return accounting.createFiscalPeriod(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/fiscal-periods/{periodId}/close")
    public FiscalPeriodView closeFiscalPeriod(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID periodId,
            HttpServletRequest request) {
        return accounting.closeFiscalPeriod(actor, periodId, request.getRemoteAddr());
    }

    @PostMapping("/cost-centers")
    @ResponseStatus(HttpStatus.CREATED)
    public CostCenterView createCostCenter(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CostCenterCommand command,
            HttpServletRequest request) {
        return accounting.createCostCenter(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierView createSupplier(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SupplierCommand command,
            HttpServletRequest request) {
        return accounting.createSupplier(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountView createBankAccount(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody BankAccountCommand command,
            HttpServletRequest request) {
        return accounting.createBankAccount(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/supplier-invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierInvoiceView createSupplierInvoice(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SupplierInvoiceCommand command,
            HttpServletRequest request) {
        return accounting.createSupplierInvoice(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/supplier-invoices/{invoiceId}/post")
    public SupplierInvoiceView postSupplierInvoice(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID invoiceId,
            HttpServletRequest request) {
        return accounting.postSupplierInvoice(actor, invoiceId, request.getRemoteAddr());
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView createExpense(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ExpenseCommand command,
            HttpServletRequest request) {
        return accounting.createExpense(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/expenses/{expenseId}/post")
    public ExpenseView postExpense(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID expenseId,
            HttpServletRequest request) {
        return accounting.postExpense(actor, expenseId, request.getRemoteAddr());
    }

    @PostMapping("/entries/{entryId}/reverse")
    public EntryView reverseEntry(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID entryId,
            @RequestBody ReversalCommand command,
            HttpServletRequest request) {
        return accounting.reverseEntry(actor, entryId, command, request.getRemoteAddr());
    }

    @PostMapping("/reconciliations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconciliationView createReconciliation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ReconciliationCommand command,
            HttpServletRequest request) {
        return accounting.createReconciliation(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/reconciliations/{reconciliationId}/entries/{entryId}")
    public ReconciliationView matchEntry(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID reconciliationId,
            @PathVariable UUID entryId,
            @RequestBody(required = false) MatchCommand command,
            HttpServletRequest request) {
        return accounting.matchReconciliationEntry(
                actor, reconciliationId, entryId, command, request.getRemoteAddr());
    }

    @PostMapping("/reconciliations/{reconciliationId}/complete")
    public ReconciliationView completeReconciliation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID reconciliationId,
            HttpServletRequest request) {
        return accounting.completeReconciliation(actor, reconciliationId, request.getRemoteAddr());
    }

    @PostMapping("/family-ledger/sync")
    public SyncResult syncFamilyLedger(
            @AuthenticationPrincipal SchoolPrincipal actor, HttpServletRequest request) {
        return accounting.syncFamilyLedger(actor, request.getRemoteAddr());
    }
}
