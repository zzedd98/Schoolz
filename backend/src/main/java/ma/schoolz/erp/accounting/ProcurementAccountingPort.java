package ma.schoolz.erp.accounting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;

public interface ProcurementAccountingPort {
    ReferenceData procurementReferences(SchoolPrincipal actor);

    SupplierView createProcurementSupplier(
            SchoolPrincipal actor, SupplierCommand command, String ipAddress);

    SupplierInvoiceView postProcurementInvoice(
            SchoolPrincipal actor, SupplierInvoiceCommand command, String ipAddress);

    ExpenseView postProcurementExpense(
            SchoolPrincipal actor, ExpenseCommand command, String ipAddress);

    EntryResult postProcurementPayment(SchoolPrincipal actor, PaymentPosting command);

    record SupplierCommand(
            String code,
            String name,
            String taxIdentifier,
            String email,
            String phone,
            String address) {}

    record SupplierInvoiceCommand(
            UUID supplierId,
            UUID journalId,
            UUID expenseAccountId,
            UUID payableAccountId,
            UUID costCenterId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String description,
            BigDecimal totalAmount,
            String currency) {}

    record ExpenseCommand(
            UUID supplierId,
            UUID journalId,
            UUID expenseAccountId,
            UUID paymentAccountId,
            UUID costCenterId,
            String expenseReference,
            LocalDate expenseDate,
            String description,
            BigDecimal amount,
            String currency,
            String paymentMethod) {}

    record PaymentPosting(
            UUID paymentId,
            UUID supplierId,
            UUID journalId,
            UUID payableAccountId,
            UUID paymentAccountId,
            LocalDate paidOn,
            String reference,
            String description,
            BigDecimal amount,
            String currency) {}

    record SupplierView(
            UUID id,
            String code,
            String name,
            String taxIdentifier,
            String email,
            String phone,
            String address,
            String status) {}

    record JournalView(UUID id, String code, String name, String journalType, boolean active) {}

    record AccountView(
            UUID id,
            UUID parentAccountId,
            String code,
            String name,
            String accountType,
            String description,
            boolean allowPosting,
            boolean active) {}

    record CostCenterView(UUID id, String code, String name, String description, boolean active) {}

    record SupplierInvoiceView(
            UUID id,
            UUID supplierId,
            String supplierName,
            UUID journalId,
            UUID expenseAccountId,
            UUID payableAccountId,
            UUID costCenterId,
            UUID journalEntryId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String description,
            BigDecimal totalAmount,
            String currency,
            String status,
            Instant createdAt,
            Instant postedAt) {}

    record ExpenseView(
            UUID id,
            UUID supplierId,
            String supplierName,
            UUID journalId,
            UUID expenseAccountId,
            UUID paymentAccountId,
            UUID costCenterId,
            UUID journalEntryId,
            String expenseReference,
            LocalDate expenseDate,
            String description,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String status,
            Instant createdAt,
            Instant postedAt) {}

    record EntryResult(
            UUID id, String entryNumber, BigDecimal totalDebit, BigDecimal totalCredit) {}

    record ReferenceData(
            List<SupplierView> suppliers,
            List<JournalView> journals,
            List<AccountView> accounts,
            List<CostCenterView> costCenters,
            List<SupplierInvoiceView> supplierInvoices,
            List<ExpenseView> expenses) {}
}
