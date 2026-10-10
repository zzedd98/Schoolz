package ma.schoolz.erp.procurement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.accounting.ProcurementAccountingPort;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.EntryResult;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.ExpenseCommand;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.ExpenseView;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.PaymentPosting;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.ReferenceData;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierCommand;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierInvoiceCommand;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierInvoiceView;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcurementService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> URGENCIES = Set.of("NORMAL", "HIGH", "CRITICAL");
    private static final Set<String> PAYMENT_METHODS =
            Set.of("CASH", "BANK_TRANSFER", "CHECK", "CARD", "ONLINE_PAYMENT", "OTHER");

    private final JdbcTemplate jdbc;
    private final ProcurementAccountingPort accounting;
    private final IdentityAuditTrail audit;
    private final Clock clock;

    public ProcurementService(
            JdbcTemplate jdbc,
            ProcurementAccountingPort accounting,
            IdentityAuditTrail audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.accounting = accounting;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('procurement.read')")
    public Dashboard dashboard(SchoolPrincipal actor) {
        UUID tenantId = actor.tenantId();
        List<RequestView> requests = requests(tenantId);
        List<OrderView> orders = orders(tenantId);
        List<ReceiptView> receipts = receipts(tenantId);
        List<InvoiceMatchView> invoiceMatches = invoiceMatches(tenantId);
        List<PaymentView> payments = payments(tenantId);
        BigDecimal committed =
                orders.stream()
                        .filter(order -> !"CANCELLED".equals(order.status()))
                        .map(OrderView::totalAmount)
                        .reduce(ZERO, BigDecimal::add);
        BigDecimal paid = payments.stream().map(PaymentView::amount).reduce(ZERO, BigDecimal::add);
        long awaitingApproval =
                requests.stream().filter(request -> "SUBMITTED".equals(request.status())).count();
        long awaitingReceipt =
                orders.stream()
                        .filter(
                                order ->
                                        "ISSUED".equals(order.status())
                                                || "PARTIALLY_RECEIVED".equals(order.status()))
                        .count();
        return new Dashboard(
                clock.instant(),
                configuration(tenantId),
                new Statistics(
                        awaitingApproval,
                        awaitingReceipt,
                        money(committed),
                        money(paid),
                        invoiceMatches.stream()
                                .filter(match -> "EXCEPTION".equals(match.status()))
                                .count()),
                accounting.procurementReferences(actor),
                thresholds(tenantId),
                requests,
                orders,
                receipts,
                invoiceMatches,
                payments);
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.configure')")
    public ConfigurationView configure(
            SchoolPrincipal actor, ConfigurationCommand command, String ipAddress) {
        if (command == null) {
            throw new ProcurementValidationException("La configuration achats est requise");
        }
        String currency = currency(command.baseCurrency());
        Instant now = clock.instant();
        int updated =
                jdbc.update(
                        "UPDATE procurement_configuration SET base_currency=?,"
                                + "allow_partial_receipts=?,updated_at=? WHERE tenant_id=?",
                        currency,
                        command.allowPartialReceipts(),
                        timestamp(now),
                        actor.tenantId());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO procurement_configuration"
                        + " (tenant_id,base_currency,allow_partial_receipts,created_at,updated_at)"
                        + " VALUES (?,?,?,?,?)",
                    actor.tenantId(),
                    currency,
                    command.allowPartialReceipts(),
                    timestamp(now),
                    timestamp(now));
        }
        ConfigurationView result = configuration(actor.tenantId());
        audit(
                actor,
                "procurement.configuration.updated",
                "ProcurementConfiguration",
                null,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.configure')")
    public ThresholdView createThreshold(
            SchoolPrincipal actor, ThresholdCommand command, String ipAddress) {
        if (command == null || command.minAmount() == null) {
            throw new ProcurementValidationException("Le seuil et son montant minimum sont requis");
        }
        BigDecimal minimum = nonNegativeMoney(command.minAmount(), "Le minimum");
        BigDecimal maximum =
                command.maxAmount() == null
                        ? null
                        : nonNegativeMoney(command.maxAmount(), "Le maximum");
        if (maximum != null && maximum.compareTo(minimum) <= 0) {
            throw new ProcurementValidationException("Le maximum doit être supérieur au minimum");
        }
        if (command.requiredApprovals() < 1 || command.requiredApprovals() > 5) {
            throw new ProcurementValidationException(
                    "Le nombre d’approbations doit être compris entre 1 et 5");
        }
        int overlaps =
                maximum == null
                        ? jdbc.queryForObject(
                                "SELECT count(*) FROM procurement_approval_threshold WHERE"
                                        + " tenant_id=? AND active=TRUE AND"
                                        + " (max_amount IS NULL OR max_amount>?)",
                                Integer.class,
                                actor.tenantId(),
                                minimum)
                        : jdbc.queryForObject(
                                "SELECT count(*) FROM procurement_approval_threshold WHERE"
                                        + " tenant_id=? AND active=TRUE AND min_amount<? AND"
                                        + " (max_amount IS NULL OR max_amount>?)",
                                Integer.class,
                                actor.tenantId(),
                                maximum,
                                minimum);
        if (overlaps > 0) {
            throw new ProcurementConflictException("Ce seuil chevauche une plage active existante");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO procurement_approval_threshold"
                    + " (id,tenant_id,code,label,min_amount,max_amount,required_approvals,active,created_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,TRUE,?,?)",
                id,
                actor.tenantId(),
                required(command.code(), 40, "Le code du seuil").toUpperCase(Locale.ROOT),
                required(command.label(), 180, "Le libellé du seuil"),
                minimum,
                maximum,
                command.requiredApprovals(),
                timestamp(now),
                timestamp(now));
        ThresholdView result = threshold(actor.tenantId(), id);
        audit(actor, "procurement.threshold.created", "ApprovalThreshold", id, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.configure')")
    public SupplierView createSupplier(
            SchoolPrincipal actor, SupplierCommand command, String ipAddress) {
        return accounting.createProcurementSupplier(actor, command, ipAddress);
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.request')")
    public RequestView createRequest(
            SchoolPrincipal actor, RequestCommand command, String ipAddress) {
        validateRequest(command);
        UUID requestId = UUID.randomUUID();
        Instant now = clock.instant();
        BigDecimal estimatedAmount = requestTotal(command.lines());
        String baseCurrency = configuration(actor.tenantId()).baseCurrency();
        jdbc.update(
                "INSERT INTO procurement_purchase_request"
                    + " (id,tenant_id,approval_threshold_id,cost_center_id,requester_user_id,request_number,requested_on,title,business_need,urgency,estimated_amount,currency,status,submitted_at,approved_at,rejected_at,created_at,updated_at)"
                    + " VALUES (?,?,NULL,?,?,?,?,?,?,?,?,?,'DRAFT',NULL,NULL,NULL,?,?)",
                requestId,
                actor.tenantId(),
                command.costCenterId(),
                actor.accountId(),
                requestNumber(now, requestId),
                command.requestedOn(),
                required(command.title(), 180, "Le titre"),
                required(command.businessNeed(), 1000, "Le besoin métier"),
                enumValue(command.urgency(), URGENCIES, "L’urgence"),
                estimatedAmount,
                baseCurrency,
                timestamp(now),
                timestamp(now));
        int position = 1;
        for (RequestLineCommand line : command.lines()) {
            BigDecimal quantity = positiveQuantity(line.quantity());
            BigDecimal unitPrice = nonNegativeMoney(line.estimatedUnitPrice(), "Le prix unitaire");
            jdbc.update(
                    "INSERT INTO procurement_purchase_request_line"
                        + " (id,tenant_id,purchase_request_id,line_position,description,quantity,unit,estimated_unit_price,estimated_total)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    requestId,
                    position++,
                    required(line.description(), 500, "La désignation"),
                    quantity,
                    required(line.unit(), 40, "L’unité"),
                    unitPrice,
                    money(quantity.multiply(unitPrice)));
        }
        RequestView result = request(actor.tenantId(), requestId);
        audit(
                actor,
                "procurement.request.created",
                "PurchaseRequest",
                requestId,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.request')")
    public RequestView submitRequest(SchoolPrincipal actor, UUID requestId, String ipAddress) {
        RequestView before = request(actor.tenantId(), requestId);
        if (!"DRAFT".equals(before.status())) {
            throw new ProcurementConflictException("Seule une demande brouillon peut être soumise");
        }
        ThresholdView threshold = matchingThreshold(actor.tenantId(), before.estimatedAmount());
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE procurement_purchase_request SET"
                        + " status='SUBMITTED',approval_threshold_id=?,submitted_at=?,updated_at=?"
                        + " WHERE tenant_id=? AND id=? AND status='DRAFT'",
                threshold.id(),
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                requestId);
        RequestView result = request(actor.tenantId(), requestId);
        audit(
                actor,
                "procurement.request.submitted",
                "PurchaseRequest",
                requestId,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.approve')")
    public RequestView decideRequest(
            SchoolPrincipal actor, UUID requestId, DecisionCommand command, String ipAddress) {
        if (command == null) {
            throw new ProcurementValidationException("La décision est requise");
        }
        String decision =
                enumValue(command.decision(), Set.of("APPROVED", "REJECTED"), "La décision");
        RequestView before = request(actor.tenantId(), requestId);
        if (!"SUBMITTED".equals(before.status())) {
            throw new ProcurementConflictException(
                    "Seule une demande soumise peut être approuvée ou rejetée");
        }
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM procurement_request_approval WHERE tenant_id=?"
                                + " AND purchase_request_id=? AND approver_user_id=?",
                        Integer.class,
                        actor.tenantId(),
                        requestId,
                        actor.accountId());
        if (existing != null && existing > 0) {
            throw new ProcurementConflictException("Cet approbateur a déjà statué sur la demande");
        }
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO procurement_request_approval"
                    + " (id,tenant_id,purchase_request_id,approver_user_id,decision,note,decided_at)"
                    + " VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                actor.tenantId(),
                requestId,
                actor.accountId(),
                decision,
                optional(command.note(), 700),
                timestamp(now));
        if ("REJECTED".equals(decision)) {
            jdbc.update(
                    "UPDATE procurement_purchase_request SET"
                        + " status='REJECTED',rejected_at=?,updated_at=? WHERE tenant_id=? AND id=?"
                        + " AND status='SUBMITTED'",
                    timestamp(now),
                    timestamp(now),
                    actor.tenantId(),
                    requestId);
        } else {
            int approvals =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM procurement_request_approval WHERE tenant_id=?"
                                    + " AND purchase_request_id=? AND decision='APPROVED'",
                            Integer.class,
                            actor.tenantId(),
                            requestId);
            if (approvals >= before.requiredApprovals()) {
                jdbc.update(
                        "UPDATE procurement_purchase_request SET"
                            + " status='APPROVED',approved_at=?,updated_at=? WHERE tenant_id=? AND"
                            + " id=? AND status='SUBMITTED'",
                        timestamp(now),
                        timestamp(now),
                        actor.tenantId(),
                        requestId);
            }
        }
        RequestView result = request(actor.tenantId(), requestId);
        audit(
                actor,
                "procurement.request.decided",
                "PurchaseRequest",
                requestId,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.order')")
    public OrderView createOrder(
            SchoolPrincipal actor, UUID requestId, OrderCommand command, String ipAddress) {
        if (command == null || command.supplierId() == null || command.orderedOn() == null) {
            throw new ProcurementValidationException(
                    "Le fournisseur et la date de commande sont requis");
        }
        RequestView request = request(actor.tenantId(), requestId);
        if (!"APPROVED".equals(request.status())) {
            throw new ProcurementConflictException(
                    "La demande doit être entièrement approuvée avant commande");
        }
        ReferenceData referenceData = accounting.procurementReferences(actor);
        boolean supplierExists =
                referenceData.suppliers().stream()
                        .anyMatch(item -> item.id().equals(command.supplierId()));
        if (!supplierExists) {
            throw new ProcurementNotFoundException("Fournisseur introuvable");
        }
        UUID orderId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO procurement_purchase_order"
                    + " (id,tenant_id,purchase_request_id,supplier_id,order_number,ordered_on,expected_on,delivery_address,notes,total_amount,currency,status,issued_by_user_id,issued_at,updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,'ISSUED',?,?,?)",
                orderId,
                actor.tenantId(),
                requestId,
                command.supplierId(),
                orderNumber(now, orderId),
                command.orderedOn(),
                command.expectedOn(),
                optional(command.deliveryAddress(), 700),
                optional(command.notes(), 1000),
                request.estimatedAmount(),
                request.currency(),
                actor.accountId(),
                timestamp(now),
                timestamp(now));
        for (RequestLineView line : request.lines()) {
            jdbc.update(
                    "INSERT INTO procurement_purchase_order_line"
                        + " (id,tenant_id,purchase_order_id,request_line_id,line_position,description,quantity_ordered,quantity_received,unit,unit_price,line_total)"
                        + " VALUES (?,?,?,?,?,?,?,0,?,?,?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    orderId,
                    line.id(),
                    line.position(),
                    line.description(),
                    line.quantity(),
                    line.unit(),
                    line.estimatedUnitPrice(),
                    line.estimatedTotal());
        }
        jdbc.update(
                "UPDATE procurement_purchase_request SET status='ORDERED',updated_at=?"
                        + " WHERE tenant_id=? AND id=? AND status='APPROVED'",
                timestamp(now),
                actor.tenantId(),
                requestId);
        OrderView result = order(actor.tenantId(), orderId);
        audit(actor, "procurement.order.issued", "PurchaseOrder", orderId, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.receive')")
    public ReceiptView receiveGoods(
            SchoolPrincipal actor, UUID orderId, ReceiptCommand command, String ipAddress) {
        if (command == null
                || command.receivedOn() == null
                || command.lines() == null
                || command.lines().isEmpty()) {
            throw new ProcurementValidationException(
                    "La date et les lignes réceptionnées sont requises");
        }
        OrderView before = order(actor.tenantId(), orderId);
        if (!Set.of("ISSUED", "PARTIALLY_RECEIVED").contains(before.status())) {
            throw new ProcurementConflictException("Cette commande ne peut plus être réceptionnée");
        }
        boolean allowPartial = configuration(actor.tenantId()).allowPartialReceipts();
        Map<UUID, OrderLineView> orderLines =
                before.lines().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(OrderLineView::id, line -> line));
        UUID receiptId = UUID.randomUUID();
        Instant now = clock.instant();
        List<ReceivedQuantity> quantities = new ArrayList<>();
        for (ReceiptLineCommand line : command.lines()) {
            OrderLineView orderLine = orderLines.get(line.orderLineId());
            if (orderLine == null) {
                throw new ProcurementValidationException(
                        "Une ligne ne dépend pas de cette commande");
            }
            BigDecimal quantity = positiveQuantity(line.quantityReceived());
            BigDecimal remaining =
                    orderLine.quantityOrdered().subtract(orderLine.quantityReceived());
            if (quantity.compareTo(remaining) > 0) {
                throw new ProcurementConflictException(
                        "La quantité reçue dépasse la quantité commandée");
            }
            quantities.add(new ReceivedQuantity(orderLine.id(), quantity));
        }
        BigDecimal futureTotalReceived =
                before.lines().stream()
                        .map(
                                line ->
                                        line.quantityReceived()
                                                .add(
                                                        quantities.stream()
                                                                .filter(
                                                                        q ->
                                                                                q.orderLineId()
                                                                                        .equals(
                                                                                                line
                                                                                                        .id()))
                                                                .map(ReceivedQuantity::quantity)
                                                                .reduce(
                                                                        BigDecimal.ZERO,
                                                                        BigDecimal::add)))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderedQuantity =
                before.lines().stream()
                        .map(OrderLineView::quantityOrdered)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean complete = futureTotalReceived.compareTo(orderedQuantity) == 0;
        if (!allowPartial && !complete) {
            throw new ProcurementConflictException(
                    "La politique achats exige une réception complète");
        }
        jdbc.update(
                "INSERT INTO procurement_goods_receipt"
                    + " (id,tenant_id,purchase_order_id,receipt_number,received_on,delivery_note,note,received_by_user_id,status,confirmed_at)"
                    + " VALUES (?,?,?,?,?,?,?,?, 'CONFIRMED',?)",
                receiptId,
                actor.tenantId(),
                orderId,
                receiptNumber(now, receiptId),
                command.receivedOn(),
                optional(command.deliveryNote(), 120),
                optional(command.note(), 700),
                actor.accountId(),
                timestamp(now));
        for (ReceivedQuantity quantity : quantities) {
            jdbc.update(
                    "INSERT INTO procurement_goods_receipt_line"
                        + " (id,tenant_id,goods_receipt_id,purchase_order_line_id,quantity_received)"
                        + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    actor.tenantId(),
                    receiptId,
                    quantity.orderLineId(),
                    quantity.quantity());
            jdbc.update(
                    "UPDATE procurement_purchase_order_line SET"
                            + " quantity_received=quantity_received+? WHERE tenant_id=? AND id=?",
                    quantity.quantity(),
                    actor.tenantId(),
                    quantity.orderLineId());
        }
        jdbc.update(
                "UPDATE procurement_purchase_order SET status=?,updated_at=? WHERE tenant_id=? AND"
                        + " id=?",
                complete ? "RECEIVED" : "PARTIALLY_RECEIVED",
                timestamp(now),
                actor.tenantId(),
                orderId);
        if (complete) {
            jdbc.update(
                    "UPDATE procurement_purchase_request SET status='RECEIVED',updated_at=?"
                            + " WHERE tenant_id=? AND id=? AND status='ORDERED'",
                    timestamp(now),
                    actor.tenantId(),
                    before.requestId());
        }
        ReceiptView result = receipt(actor.tenantId(), receiptId);
        audit(actor, "procurement.goods.received", "GoodsReceipt", receiptId, ipAddress, result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.invoice')")
    public InvoiceMatchView recordInvoice(
            SchoolPrincipal actor, UUID orderId, InvoiceCommand command, String ipAddress) {
        validateInvoice(command);
        OrderView order = order(actor.tenantId(), orderId);
        if (!"RECEIVED".equals(order.status())) {
            throw new ProcurementConflictException(
                    "La commande doit être entièrement réceptionnée avant facture");
        }
        BigDecimal invoiceAmount = money(command.totalAmount());
        BigDecimal variance =
                invoiceAmount.subtract(order.totalAmount()).setScale(2, RoundingMode.HALF_UP);
        if (variance.signum() != 0) {
            throw new ProcurementConflictException(
                    "La facture ne correspond pas au total commandé et réceptionné");
        }
        SupplierInvoiceView invoice =
                accounting.postProcurementInvoice(
                        actor,
                        new SupplierInvoiceCommand(
                                order.supplierId(),
                                command.journalId(),
                                command.expenseAccountId(),
                                command.payableAccountId(),
                                command.costCenterId(),
                                command.invoiceNumber(),
                                command.invoiceDate(),
                                command.dueDate(),
                                command.description(),
                                invoiceAmount,
                                order.currency()),
                        ipAddress);
        UUID matchId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO procurement_invoice_match"
                    + " (id,tenant_id,purchase_order_id,supplier_invoice_id,matched_amount,variance_amount,status,matched_by_user_id,matched_at)"
                    + " VALUES (?,?,?,?,?,?,'MATCHED',?,?)",
                matchId,
                actor.tenantId(),
                orderId,
                invoice.id(),
                invoiceAmount,
                variance,
                actor.accountId(),
                timestamp(now));
        jdbc.update(
                "UPDATE procurement_purchase_order SET status='INVOICED',updated_at=? WHERE"
                        + " tenant_id=? AND id=?",
                timestamp(now),
                actor.tenantId(),
                orderId);
        jdbc.update(
                "UPDATE procurement_purchase_request SET status='INVOICED',updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(now),
                actor.tenantId(),
                order.requestId());
        InvoiceMatchView result = invoiceMatch(actor.tenantId(), matchId);
        audit(
                actor,
                "procurement.invoice.matched",
                "SupplierInvoiceMatch",
                matchId,
                ipAddress,
                result);
        return result;
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.invoice')")
    public ExpenseView recordExpense(
            SchoolPrincipal actor, ExpenseCommand command, String ipAddress) {
        return accounting.postProcurementExpense(actor, command, ipAddress);
    }

    @Transactional
    @PreAuthorize("hasAuthority('procurement.pay')")
    public PaymentView payInvoice(
            SchoolPrincipal actor, UUID invoiceMatchId, PaymentCommand command, String ipAddress) {
        validatePayment(command);
        InvoiceMatchView match = invoiceMatch(actor.tenantId(), invoiceMatchId);
        if (!"MATCHED".equals(match.status())) {
            throw new ProcurementConflictException("Cette facture n’est plus payable");
        }
        BigDecimal alreadyPaid = paidAmount(actor.tenantId(), invoiceMatchId);
        BigDecimal amount = positiveMoney(command.amount(), "Le montant du paiement");
        if (alreadyPaid.add(amount).compareTo(match.matchedAmount()) > 0) {
            throw new ProcurementConflictException("Le paiement dépasse le solde fournisseur");
        }
        OrderView order = order(actor.tenantId(), match.orderId());
        UUID paymentId = UUID.randomUUID();
        String reference = required(command.paymentReference(), 90, "La référence du paiement");
        EntryResult entry =
                accounting.postProcurementPayment(
                        actor,
                        new PaymentPosting(
                                paymentId,
                                order.supplierId(),
                                command.journalId(),
                                command.payableAccountId(),
                                command.paymentAccountId(),
                                command.paidOn(),
                                reference,
                                "Paiement fournisseur " + reference,
                                amount,
                                order.currency()));
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO procurement_supplier_payment"
                    + " (id,tenant_id,invoice_match_id,supplier_id,journal_entry_id,payment_reference,paid_on,amount,currency,payment_method,status,recorded_by_user_id,recorded_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,'RECORDED',?,?)",
                paymentId,
                actor.tenantId(),
                invoiceMatchId,
                order.supplierId(),
                entry.id(),
                reference,
                command.paidOn(),
                amount,
                order.currency(),
                enumValue(command.paymentMethod(), PAYMENT_METHODS, "Le mode de paiement"),
                actor.accountId(),
                timestamp(now));
        if (alreadyPaid.add(amount).compareTo(match.matchedAmount()) == 0) {
            jdbc.update(
                    "UPDATE procurement_invoice_match SET status='PAID' WHERE tenant_id=? AND id=?",
                    actor.tenantId(),
                    invoiceMatchId);
            jdbc.update(
                    "UPDATE procurement_purchase_order SET status='PAID',updated_at=? WHERE"
                            + " tenant_id=? AND id=?",
                    timestamp(now),
                    actor.tenantId(),
                    match.orderId());
            jdbc.update(
                    "UPDATE procurement_purchase_request SET status='PAID',updated_at=?"
                            + " WHERE tenant_id=? AND id=?",
                    timestamp(now),
                    actor.tenantId(),
                    order.requestId());
        }
        PaymentView result = payment(actor.tenantId(), paymentId);
        audit(actor, "procurement.supplier.paid", "SupplierPayment", paymentId, ipAddress, result);
        return result;
    }

    private ConfigurationView configuration(UUID tenantId) {
        return jdbc
                .query(
                        "SELECT base_currency,allow_partial_receipts FROM procurement_configuration"
                                + " WHERE tenant_id=?",
                        (result, row) ->
                                new ConfigurationView(
                                        result.getString("base_currency"),
                                        result.getBoolean("allow_partial_receipts")),
                        tenantId)
                .stream()
                .findFirst()
                .orElse(new ConfigurationView("MAD", true));
    }

    private List<ThresholdView> thresholds(UUID tenantId) {
        return jdbc.query(
                "SELECT id,code,label,min_amount,max_amount,required_approvals,active FROM"
                        + " procurement_approval_threshold WHERE tenant_id=? ORDER BY min_amount",
                (result, row) -> thresholdView(result),
                tenantId);
    }

    private ThresholdView threshold(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT id,code,label,min_amount,max_amount,required_approvals,active"
                                + " FROM procurement_approval_threshold WHERE tenant_id=? AND id=?",
                        (result, row) -> thresholdView(result),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new ProcurementNotFoundException("Seuil d’approbation introuvable"));
    }

    private ThresholdView matchingThreshold(UUID tenantId, BigDecimal amount) {
        return jdbc
                .query(
                        "SELECT id,code,label,min_amount,max_amount,required_approvals,active FROM"
                            + " procurement_approval_threshold WHERE tenant_id=? AND active=TRUE"
                            + " AND min_amount<=? AND (max_amount IS NULL OR max_amount>?) ORDER BY"
                            + " min_amount DESC LIMIT 1",
                        (result, row) -> thresholdView(result),
                        tenantId,
                        amount,
                        amount)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ProcurementConflictException(
                                        "Aucun seuil d’approbation ne couvre ce montant"));
    }

    private ThresholdView thresholdView(ResultSet result) throws SQLException {
        return new ThresholdView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("label"),
                money(result.getBigDecimal("min_amount")),
                result.getBigDecimal("max_amount") == null
                        ? null
                        : money(result.getBigDecimal("max_amount")),
                result.getInt("required_approvals"),
                result.getBoolean("active"));
    }

    private List<RequestView> requests(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM procurement_purchase_request WHERE tenant_id=?"
                        + " ORDER BY requested_on DESC,created_at DESC",
                (result, row) -> request(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private RequestView request(UUID tenantId, UUID id) {
        RequestHeader header =
                jdbc
                        .query(
                                "SELECT r.id,r.requester_user_id,u.display_name"
                                    + " requester_name,r.request_number,"
                                    + "r.requested_on,r.title,r.business_need,r.urgency,r.estimated_amount,r.currency,r.status,r.approval_threshold_id,t.label"
                                    + " threshold_label,COALESCE(t.required_approvals,0)"
                                    + " required_approvals,r.cost_center_id,c.code"
                                    + " cost_center_code,r.submitted_at,r.approved_at,r.rejected_at"
                                    + " FROM procurement_purchase_request r JOIN"
                                    + " identity_user_account u ON u.id=r.requester_user_id LEFT"
                                    + " JOIN procurement_approval_threshold t ON"
                                    + " t.id=r.approval_threshold_id AND t.tenant_id=r.tenant_id"
                                    + " LEFT JOIN accounting_cost_center c ON c.id=r.cost_center_id"
                                    + " AND c.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.id=?",
                                (result, row) -> requestHeader(result),
                                tenantId,
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ProcurementNotFoundException(
                                                "Demande d’achat introuvable"));
        List<RequestLineView> lines =
                jdbc.query(
                        "SELECT"
                            + " id,line_position,description,quantity,unit,estimated_unit_price,estimated_total"
                            + " FROM procurement_purchase_request_line WHERE tenant_id=? AND"
                            + " purchase_request_id=? ORDER BY line_position",
                        (result, row) ->
                                new RequestLineView(
                                        result.getObject("id", UUID.class),
                                        result.getInt("line_position"),
                                        result.getString("description"),
                                        result.getBigDecimal("quantity"),
                                        result.getString("unit"),
                                        money(result.getBigDecimal("estimated_unit_price")),
                                        money(result.getBigDecimal("estimated_total"))),
                        tenantId,
                        id);
        List<ApprovalView> approvals =
                jdbc.query(
                        "SELECT a.id,a.approver_user_id,u.display_name"
                                + " approver_name,a.decision,a.note,a.decided_at FROM"
                                + " procurement_request_approval a JOIN identity_user_account u ON"
                                + " u.id=a.approver_user_id WHERE a.tenant_id=? AND"
                                + " a.purchase_request_id=? ORDER BY a.decided_at",
                        (result, row) ->
                                new ApprovalView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("approver_user_id", UUID.class),
                                        result.getString("approver_name"),
                                        result.getString("decision"),
                                        result.getString("note"),
                                        instant(result, "decided_at")),
                        tenantId,
                        id);
        return new RequestView(
                header.id(),
                header.requesterId(),
                header.requesterName(),
                header.requestNumber(),
                header.requestedOn(),
                header.title(),
                header.businessNeed(),
                header.urgency(),
                header.estimatedAmount(),
                header.currency(),
                header.status(),
                header.thresholdId(),
                header.thresholdLabel(),
                header.requiredApprovals(),
                header.costCenterId(),
                header.costCenterCode(),
                header.submittedAt(),
                header.approvedAt(),
                header.rejectedAt(),
                lines,
                approvals);
    }

    private RequestHeader requestHeader(ResultSet result) throws SQLException {
        return new RequestHeader(
                result.getObject("id", UUID.class),
                result.getObject("requester_user_id", UUID.class),
                result.getString("requester_name"),
                result.getString("request_number"),
                result.getObject("requested_on", LocalDate.class),
                result.getString("title"),
                result.getString("business_need"),
                result.getString("urgency"),
                money(result.getBigDecimal("estimated_amount")),
                result.getString("currency"),
                result.getString("status"),
                result.getObject("approval_threshold_id", UUID.class),
                result.getString("threshold_label"),
                result.getInt("required_approvals"),
                result.getObject("cost_center_id", UUID.class),
                result.getString("cost_center_code"),
                nullableInstant(result, "submitted_at"),
                nullableInstant(result, "approved_at"),
                nullableInstant(result, "rejected_at"));
    }

    private List<OrderView> orders(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM procurement_purchase_order WHERE tenant_id=?"
                        + " ORDER BY ordered_on DESC,issued_at DESC",
                (result, row) -> order(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private OrderView order(UUID tenantId, UUID id) {
        OrderHeader header =
                jdbc
                        .query(
                                "SELECT o.id,o.purchase_request_id,o.supplier_id,s.code"
                                    + " supplier_code,s.name supplier_name,"
                                    + "o.order_number,o.ordered_on,o.expected_on,o.delivery_address,o.notes,o.total_amount,o.currency,o.status,o.issued_at"
                                    + " FROM procurement_purchase_order o JOIN accounting_supplier"
                                    + " s ON s.id=o.supplier_id AND s.tenant_id=o.tenant_id WHERE"
                                    + " o.tenant_id=? AND o.id=?",
                                (result, row) -> orderHeader(result),
                                tenantId,
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ProcurementNotFoundException(
                                                "Commande fournisseur introuvable"));
        List<OrderLineView> lines =
                jdbc.query(
                        "SELECT"
                            + " id,request_line_id,line_position,description,quantity_ordered,quantity_received,unit,unit_price,line_total"
                            + " FROM procurement_purchase_order_line WHERE tenant_id=? AND"
                            + " purchase_order_id=? ORDER BY line_position",
                        (result, row) ->
                                new OrderLineView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("request_line_id", UUID.class),
                                        result.getInt("line_position"),
                                        result.getString("description"),
                                        result.getBigDecimal("quantity_ordered"),
                                        result.getBigDecimal("quantity_received"),
                                        result.getString("unit"),
                                        money(result.getBigDecimal("unit_price")),
                                        money(result.getBigDecimal("line_total"))),
                        tenantId,
                        id);
        return new OrderView(
                header.id(),
                header.requestId(),
                header.supplierId(),
                header.supplierCode(),
                header.supplierName(),
                header.orderNumber(),
                header.orderedOn(),
                header.expectedOn(),
                header.deliveryAddress(),
                header.notes(),
                header.totalAmount(),
                header.currency(),
                header.status(),
                header.issuedAt(),
                lines);
    }

    private OrderHeader orderHeader(ResultSet result) throws SQLException {
        return new OrderHeader(
                result.getObject("id", UUID.class),
                result.getObject("purchase_request_id", UUID.class),
                result.getObject("supplier_id", UUID.class),
                result.getString("supplier_code"),
                result.getString("supplier_name"),
                result.getString("order_number"),
                result.getObject("ordered_on", LocalDate.class),
                result.getObject("expected_on", LocalDate.class),
                result.getString("delivery_address"),
                result.getString("notes"),
                money(result.getBigDecimal("total_amount")),
                result.getString("currency"),
                result.getString("status"),
                instant(result, "issued_at"));
    }

    private List<ReceiptView> receipts(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM procurement_goods_receipt WHERE tenant_id=? ORDER BY received_on"
                        + " DESC,confirmed_at DESC",
                (result, row) -> receipt(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private ReceiptView receipt(UUID tenantId, UUID id) {
        ReceiptHeader header =
                jdbc
                        .query(
                                "SELECT"
                                    + " r.id,r.purchase_order_id,r.receipt_number,r.received_on,r.delivery_note,r.note,r.received_by_user_id,u.display_name"
                                    + " received_by_name,r.status,r.confirmed_at FROM"
                                    + " procurement_goods_receipt r JOIN identity_user_account u ON"
                                    + " u.id=r.received_by_user_id WHERE r.tenant_id=? AND r.id=?",
                                (result, row) ->
                                        new ReceiptHeader(
                                                result.getObject("id", UUID.class),
                                                result.getObject("purchase_order_id", UUID.class),
                                                result.getString("receipt_number"),
                                                result.getObject("received_on", LocalDate.class),
                                                result.getString("delivery_note"),
                                                result.getString("note"),
                                                result.getObject("received_by_user_id", UUID.class),
                                                result.getString("received_by_name"),
                                                result.getString("status"),
                                                instant(result, "confirmed_at")),
                                tenantId,
                                id)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ProcurementNotFoundException(
                                                "Bon de réception introuvable"));
        List<ReceiptLineView> lines =
                jdbc.query(
                        "SELECT l.purchase_order_line_id,o.description,l.quantity_received,o.unit"
                            + " FROM procurement_goods_receipt_line l JOIN"
                            + " procurement_purchase_order_line o ON o.id=l.purchase_order_line_id"
                            + " AND o.tenant_id=l.tenant_id WHERE l.tenant_id=? AND"
                            + " l.goods_receipt_id=? ORDER BY o.line_position",
                        (result, row) ->
                                new ReceiptLineView(
                                        result.getObject("purchase_order_line_id", UUID.class),
                                        result.getString("description"),
                                        result.getBigDecimal("quantity_received"),
                                        result.getString("unit")),
                        tenantId,
                        id);
        return new ReceiptView(
                header.id(),
                header.orderId(),
                header.receiptNumber(),
                header.receivedOn(),
                header.deliveryNote(),
                header.note(),
                header.receivedById(),
                header.receivedByName(),
                header.status(),
                header.confirmedAt(),
                lines);
    }

    private List<InvoiceMatchView> invoiceMatches(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM procurement_invoice_match WHERE tenant_id=? ORDER BY matched_at"
                        + " DESC",
                (result, row) -> invoiceMatch(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private InvoiceMatchView invoiceMatch(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT"
                            + " m.id,m.purchase_order_id,m.supplier_invoice_id,i.invoice_number,m.matched_amount,m.variance_amount,m.status,m.matched_at"
                            + " FROM procurement_invoice_match m JOIN accounting_supplier_invoice i"
                            + " ON i.id=m.supplier_invoice_id AND i.tenant_id=m.tenant_id WHERE"
                            + " m.tenant_id=? AND m.id=?",
                        (result, row) ->
                                new InvoiceMatchView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("purchase_order_id", UUID.class),
                                        result.getObject("supplier_invoice_id", UUID.class),
                                        result.getString("invoice_number"),
                                        money(result.getBigDecimal("matched_amount")),
                                        money(result.getBigDecimal("variance_amount")),
                                        result.getString("status"),
                                        money(paidAmount(tenantId, id)),
                                        instant(result, "matched_at")),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ProcurementNotFoundException(
                                        "Rapprochement de facture introuvable"));
    }

    private List<PaymentView> payments(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM procurement_supplier_payment WHERE tenant_id=? ORDER BY paid_on"
                        + " DESC,recorded_at DESC",
                (result, row) -> payment(tenantId, result.getObject("id", UUID.class)),
                tenantId);
    }

    private PaymentView payment(UUID tenantId, UUID id) {
        return jdbc
                .query(
                        "SELECT p.id,p.invoice_match_id,p.supplier_id,s.name"
                            + " supplier_name,p.journal_entry_id,"
                            + "e.entry_number,p.payment_reference,p.paid_on,p.amount,p.currency,p.payment_method,p.status,p.recorded_at"
                            + " FROM procurement_supplier_payment p JOIN accounting_supplier s ON"
                            + " s.id=p.supplier_id AND s.tenant_id=p.tenant_id JOIN"
                            + " accounting_journal_entry e ON e.id=p.journal_entry_id AND"
                            + " e.tenant_id=p.tenant_id WHERE p.tenant_id=? AND p.id=?",
                        (result, row) ->
                                new PaymentView(
                                        result.getObject("id", UUID.class),
                                        result.getObject("invoice_match_id", UUID.class),
                                        result.getObject("supplier_id", UUID.class),
                                        result.getString("supplier_name"),
                                        result.getObject("journal_entry_id", UUID.class),
                                        result.getString("entry_number"),
                                        result.getString("payment_reference"),
                                        result.getObject("paid_on", LocalDate.class),
                                        money(result.getBigDecimal("amount")),
                                        result.getString("currency"),
                                        result.getString("payment_method"),
                                        result.getString("status"),
                                        instant(result, "recorded_at")),
                        tenantId,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new ProcurementNotFoundException("Paiement fournisseur introuvable"));
    }

    private BigDecimal paidAmount(UUID tenantId, UUID invoiceMatchId) {
        BigDecimal value =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount),0) FROM procurement_supplier_payment"
                                + " WHERE tenant_id=? AND invoice_match_id=? AND status='RECORDED'",
                        BigDecimal.class,
                        tenantId,
                        invoiceMatchId);
        return value == null ? ZERO : money(value);
    }

    private void validateRequest(RequestCommand command) {
        if (command == null
                || command.requestedOn() == null
                || command.lines() == null
                || command.lines().isEmpty()) {
            throw new ProcurementValidationException(
                    "La demande, sa date et au moins une ligne sont requises");
        }
        if (command.lines().size() > 100) {
            throw new ProcurementValidationException("Une demande est limitée à 100 lignes");
        }
    }

    private BigDecimal requestTotal(List<RequestLineCommand> lines) {
        BigDecimal total = ZERO;
        for (RequestLineCommand line : lines) {
            if (line == null) {
                throw new ProcurementValidationException("Une ligne de demande est vide");
            }
            BigDecimal quantity = positiveQuantity(line.quantity());
            BigDecimal unitPrice = nonNegativeMoney(line.estimatedUnitPrice(), "Le prix unitaire");
            total = total.add(quantity.multiply(unitPrice));
        }
        total = money(total);
        if (total.signum() <= 0) {
            throw new ProcurementValidationException(
                    "Le total estimé doit être strictement positif");
        }
        return total;
    }

    private void validateInvoice(InvoiceCommand command) {
        if (command == null
                || command.journalId() == null
                || command.expenseAccountId() == null
                || command.payableAccountId() == null
                || command.invoiceDate() == null
                || command.dueDate() == null
                || command.totalAmount() == null) {
            throw new ProcurementValidationException(
                    "La facture, ses comptes et ses dates sont requis");
        }
        if (command.dueDate().isBefore(command.invoiceDate())) {
            throw new ProcurementValidationException("L’échéance précède la date de facture");
        }
        positiveMoney(command.totalAmount(), "Le montant de facture");
    }

    private void validatePayment(PaymentCommand command) {
        if (command == null
                || command.journalId() == null
                || command.payableAccountId() == null
                || command.paymentAccountId() == null
                || command.paidOn() == null) {
            throw new ProcurementValidationException(
                    "Le paiement, ses comptes et sa date sont requis");
        }
    }

    private String requestNumber(Instant now, UUID id) {
        return "DA-"
                + java.time.ZonedDateTime.ofInstant(now, java.time.ZoneOffset.UTC).getYear()
                + "-"
                + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String orderNumber(Instant now, UUID id) {
        return "BC-"
                + java.time.ZonedDateTime.ofInstant(now, java.time.ZoneOffset.UTC).getYear()
                + "-"
                + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String receiptNumber(Instant now, UUID id) {
        return "BR-"
                + java.time.ZonedDateTime.ofInstant(now, java.time.ZoneOffset.UTC).getYear()
                + "-"
                + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String currency(String value) {
        String result =
                value == null || value.isBlank() ? "MAD" : value.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) {
            throw new ProcurementValidationException("La devise doit utiliser trois lettres ISO");
        }
        return result;
    }

    private String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new ProcurementValidationException(label + " est requis");
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw new ProcurementValidationException(
                    label + " dépasse " + maxLength + " caractères");
        }
        return result;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw new ProcurementValidationException(
                    "Le texte dépasse " + maxLength + " caractères");
        }
        return result;
    }

    private String enumValue(String value, Set<String> accepted, String label) {
        String result = required(value, 40, label).toUpperCase(Locale.ROOT);
        if (!accepted.contains(result)) {
            throw new ProcurementValidationException(label + " n’est pas reconnu");
        }
        return result;
    }

    private BigDecimal positiveMoney(BigDecimal value, String label) {
        BigDecimal result = money(value);
        if (result.signum() <= 0) {
            throw new ProcurementValidationException(label + " doit être strictement positif");
        }
        return result;
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String label) {
        BigDecimal result = money(value);
        if (result.signum() < 0) {
            throw new ProcurementValidationException(label + " ne peut pas être négatif");
        }
        return result;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ProcurementValidationException("Un montant est requis");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ProcurementValidationException("La quantité doit être strictement positive");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private void audit(
            SchoolPrincipal actor,
            String action,
            String resourceType,
            UUID resourceId,
            String ipAddress,
            Object after) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                resourceType,
                resourceId == null ? null : resourceId.toString(),
                ipAddress,
                Map.of(),
                null,
                after);
    }

    private record ReceivedQuantity(UUID orderLineId, BigDecimal quantity) {}

    private record RequestHeader(
            UUID id,
            UUID requesterId,
            String requesterName,
            String requestNumber,
            LocalDate requestedOn,
            String title,
            String businessNeed,
            String urgency,
            BigDecimal estimatedAmount,
            String currency,
            String status,
            UUID thresholdId,
            String thresholdLabel,
            int requiredApprovals,
            UUID costCenterId,
            String costCenterCode,
            Instant submittedAt,
            Instant approvedAt,
            Instant rejectedAt) {}

    private record OrderHeader(
            UUID id,
            UUID requestId,
            UUID supplierId,
            String supplierCode,
            String supplierName,
            String orderNumber,
            LocalDate orderedOn,
            LocalDate expectedOn,
            String deliveryAddress,
            String notes,
            BigDecimal totalAmount,
            String currency,
            String status,
            Instant issuedAt) {}

    private record ReceiptHeader(
            UUID id,
            UUID orderId,
            String receiptNumber,
            LocalDate receivedOn,
            String deliveryNote,
            String note,
            UUID receivedById,
            String receivedByName,
            String status,
            Instant confirmedAt) {}

    public record ConfigurationCommand(String baseCurrency, boolean allowPartialReceipts) {}

    public record ThresholdCommand(
            String code,
            String label,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int requiredApprovals) {}

    public record RequestLineCommand(
            String description, BigDecimal quantity, String unit, BigDecimal estimatedUnitPrice) {}

    public record RequestCommand(
            UUID costCenterId,
            LocalDate requestedOn,
            String title,
            String businessNeed,
            String urgency,
            List<RequestLineCommand> lines) {}

    public record DecisionCommand(String decision, String note) {}

    public record OrderCommand(
            UUID supplierId,
            LocalDate orderedOn,
            LocalDate expectedOn,
            String deliveryAddress,
            String notes) {}

    public record ReceiptLineCommand(UUID orderLineId, BigDecimal quantityReceived) {}

    public record ReceiptCommand(
            LocalDate receivedOn,
            String deliveryNote,
            String note,
            List<ReceiptLineCommand> lines) {}

    public record InvoiceCommand(
            UUID journalId,
            UUID expenseAccountId,
            UUID payableAccountId,
            UUID costCenterId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String description,
            BigDecimal totalAmount) {}

    public record PaymentCommand(
            UUID journalId,
            UUID payableAccountId,
            UUID paymentAccountId,
            LocalDate paidOn,
            String paymentReference,
            BigDecimal amount,
            String paymentMethod) {}

    public record ConfigurationView(String baseCurrency, boolean allowPartialReceipts) {}

    public record ThresholdView(
            UUID id,
            String code,
            String label,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int requiredApprovals,
            boolean active) {}

    public record RequestLineView(
            UUID id,
            int position,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitPrice,
            BigDecimal estimatedTotal) {}

    public record ApprovalView(
            UUID id,
            UUID approverId,
            String approverName,
            String decision,
            String note,
            Instant decidedAt) {}

    public record RequestView(
            UUID id,
            UUID requesterId,
            String requesterName,
            String requestNumber,
            LocalDate requestedOn,
            String title,
            String businessNeed,
            String urgency,
            BigDecimal estimatedAmount,
            String currency,
            String status,
            UUID thresholdId,
            String thresholdLabel,
            int requiredApprovals,
            UUID costCenterId,
            String costCenterCode,
            Instant submittedAt,
            Instant approvedAt,
            Instant rejectedAt,
            List<RequestLineView> lines,
            List<ApprovalView> approvals) {}

    public record OrderLineView(
            UUID id,
            UUID requestLineId,
            int position,
            String description,
            BigDecimal quantityOrdered,
            BigDecimal quantityReceived,
            String unit,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {}

    public record OrderView(
            UUID id,
            UUID requestId,
            UUID supplierId,
            String supplierCode,
            String supplierName,
            String orderNumber,
            LocalDate orderedOn,
            LocalDate expectedOn,
            String deliveryAddress,
            String notes,
            BigDecimal totalAmount,
            String currency,
            String status,
            Instant issuedAt,
            List<OrderLineView> lines) {}

    public record ReceiptLineView(
            UUID orderLineId, String description, BigDecimal quantityReceived, String unit) {}

    public record ReceiptView(
            UUID id,
            UUID orderId,
            String receiptNumber,
            LocalDate receivedOn,
            String deliveryNote,
            String note,
            UUID receivedById,
            String receivedByName,
            String status,
            Instant confirmedAt,
            List<ReceiptLineView> lines) {}

    public record InvoiceMatchView(
            UUID id,
            UUID orderId,
            UUID supplierInvoiceId,
            String invoiceNumber,
            BigDecimal matchedAmount,
            BigDecimal varianceAmount,
            String status,
            BigDecimal paidAmount,
            Instant matchedAt) {}

    public record PaymentView(
            UUID id,
            UUID invoiceMatchId,
            UUID supplierId,
            String supplierName,
            UUID journalEntryId,
            String entryNumber,
            String paymentReference,
            LocalDate paidOn,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String status,
            Instant recordedAt) {}

    public record Statistics(
            long awaitingApproval,
            long awaitingReceipt,
            BigDecimal committedAmount,
            BigDecimal paidAmount,
            long matchingExceptions) {}

    public record Dashboard(
            Instant generatedAt,
            ConfigurationView configuration,
            Statistics statistics,
            ReferenceData accounting,
            List<ThresholdView> thresholds,
            List<RequestView> requests,
            List<OrderView> orders,
            List<ReceiptView> receipts,
            List<InvoiceMatchView> invoiceMatches,
            List<PaymentView> payments) {}
}
