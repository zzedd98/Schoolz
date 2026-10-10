package ma.schoolz.erp.billing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess.PortalIdentity;
import ma.schoolz.erp.billing.application.BillingService.AllocationCommand;
import ma.schoolz.erp.billing.application.BillingService.PaymentCommand;
import ma.schoolz.erp.billing.application.BillingService.PaymentView;
import ma.schoolz.erp.billing.application.BillingService.ScheduleAllocationCommand;
import ma.schoolz.erp.billing.application.PaymentProvider.PaymentVerification;
import ma.schoolz.erp.billing.application.PaymentProvider.PaymentWebhookEvent;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OnlinePaymentService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("PAID", "FAILED", "CANCELLED", "REFUNDED");

    private final JdbcTemplate jdbc;
    private final BillingService billing;
    private final IdentityAuditTrail audit;
    private final Clock clock;
    private final Map<String, PaymentProvider> providers;
    private final SandboxPaymentProvider sandbox;
    private final TransactionTemplate transaction;

    public OnlinePaymentService(
            JdbcTemplate jdbc,
            BillingService billing,
            IdentityAuditTrail audit,
            Clock clock,
            List<PaymentProvider> providers,
            SandboxPaymentProvider sandbox,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.audit = audit;
        this.clock = clock;
        Map<String, PaymentProvider> configured = new LinkedHashMap<>();
        for (PaymentProvider provider : providers) {
            if (configured.put(provider.key(), provider) != null) {
                throw new IllegalStateException(
                        "Fournisseur de paiement dupliqué: " + provider.key());
            }
        }
        this.providers = Map.copyOf(configured);
        this.sandbox = sandbox;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public OnlinePaymentView initialize(
            PortalIdentity parent,
            String idempotencyKey,
            InitializeOnlinePaymentCommand command,
            String ipAddress) {
        if (command == null) {
            throw new BillingValidationException("L'intention de paiement est requise");
        }
        String key = idempotencyKey(idempotencyKey);
        OnlinePaymentView previous = paymentByIdempotency(parent.tenantId(), key);
        if (previous != null) {
            requireParentOwner(parent, previous);
            return previous;
        }
        FamilyAccountRef account = parentAccount(parent, true);
        BigDecimal amount = positiveMoney(command.amount(), "Le montant du paiement en ligne");
        if (command.invoiceId() == null && command.paymentScheduleId() == null) {
            throw new BillingValidationException(
                    "Sélectionnez une facture ou une échéance à régler");
        }
        if (command.invoiceId() != null) {
            InvoiceBalance invoice = invoice(parent.tenantId(), command.invoiceId());
            if (!invoice.familyAccountId().equals(account.id())) {
                throw new BillingNotFoundException("Facture du compte famille introuvable");
            }
            if (!Set.of("ISSUED", "PARTIALLY_PAID", "OVERDUE").contains(invoice.status())) {
                throw new BillingConflictException(
                        "Cette facture ne peut pas être réglée en ligne");
            }
            if (amount.compareTo(invoice.balance()) > 0) {
                throw new BillingValidationException("Le paiement dépasse le reste de la facture");
            }
        }
        if (command.paymentScheduleId() != null) {
            ScheduleBalance schedule = schedule(parent.tenantId(), command.paymentScheduleId());
            if (!schedule.familyAccountId().equals(account.id())) {
                throw new BillingNotFoundException("Échéance du compte famille introuvable");
            }
            if (amount.compareTo(schedule.amount().subtract(schedule.paidAmount())) > 0) {
                throw new BillingValidationException("Le paiement dépasse le reste de l'échéance");
            }
        }
        PaymentProvider provider = provider(command.providerKey());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(30 * 60);
        PaymentProvider.PaymentInitialization initialized =
                provider.initializePayment(
                        new PaymentProvider.InitializePaymentRequest(
                                id.toString(),
                                amount,
                                account.currencyCode(),
                                account.accountNumber(),
                                normalizedReturnUrl(command.returnUrl()),
                                expiresAt));
        String status = providerStatus(initialized.status());
        jdbc.update(
                """
                INSERT INTO billing_online_payment
                  (id,tenant_id,family_account_id,invoice_id,payment_schedule_id,provider_key,
                   provider_payment_id,idempotency_key,checkout_url,amount,currency_code,status,
                   resulting_payment_id,expires_at,verified_at,completed_at,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL, NULL, ?, ?)
                """,
                id,
                parent.tenantId(),
                account.id(),
                command.invoiceId(),
                command.paymentScheduleId(),
                provider.key(),
                required(initialized.providerPaymentId(), 160, "Identifiant fournisseur"),
                key,
                required(initialized.checkoutUrl(), 1000, "URL de paiement"),
                amount,
                account.currencyCode(),
                status,
                timestamp(initialized.expiresAt() == null ? expiresAt : initialized.expiresAt()),
                timestamp(now),
                timestamp(now));
        OnlinePaymentView created = payment(parent.tenantId(), id);
        audit.record(
                parent.tenantId(),
                null,
                "billing.online-payment.initialized",
                "OnlinePayment",
                id.toString(),
                ipAddress,
                Map.of(
                        "provider", provider.key(),
                        "accountNumber", account.accountNumber(),
                        "status", status),
                null,
                created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<OnlinePaymentView> familyPayments(PortalIdentity parent) {
        FamilyAccountRef account = parentAccount(parent, false);
        return payments(parent.tenantId(), " AND op.family_account_id=?", account.id());
    }

    @Transactional(readOnly = true)
    public OnlinePaymentView familyPayment(PortalIdentity parent, UUID paymentId) {
        OnlinePaymentView view = payment(parent.tenantId(), paymentId);
        requireParentOwner(parent, view);
        return view;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('online-payment.read')")
    public List<OnlinePaymentView> payments(SchoolPrincipal actor, UUID accountId) {
        if (accountId == null) {
            return payments(actor.tenantId(), "", new Object[0]);
        }
        requireFamilyAccount(actor.tenantId(), accountId);
        return payments(actor.tenantId(), " AND op.family_account_id=?", accountId);
    }

    @PreAuthorize("hasAuthority('online-payment.manage')")
    public OnlinePaymentView verify(SchoolPrincipal actor, UUID paymentId, String ipAddress) {
        OnlinePaymentView before = payment(actor.tenantId(), paymentId);
        PaymentVerification verification =
                provider(before.providerKey()).verifyPayment(before.providerPaymentId());
        validateProviderAmount(before, verification.amount(), verification.currencyCode());
        OnlinePaymentView after =
                transaction.execute(
                        ignored -> synchronizeVerification(actor.tenantId(), before, verification));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.online-payment.verified",
                "OnlinePayment",
                paymentId.toString(),
                ipAddress,
                Map.of("provider", before.providerKey(), "status", verification.status()),
                before,
                after);
        return after;
    }

    public WebhookReceipt handleWebhook(
            String providerKey,
            String eventId,
            String signature,
            String payload,
            String ipAddress) {
        PaymentProvider provider = provider(providerKey);
        String normalizedEventId = required(eventId, 180, "Identifiant d'événement");
        String body = payload == null ? "" : payload;
        String payloadHash = sha256(body);
        Instant receivedAt = clock.instant();
        PaymentWebhookEvent event;
        try {
            event =
                    provider.handleWebhook(
                            new PaymentProvider.WebhookRequest(normalizedEventId, signature, body));
        } catch (OnlinePaymentSignatureException exception) {
            transaction.executeWithoutResult(
                    ignored ->
                            logUnprocessedWebhook(
                                    provider.key(),
                                    normalizedEventId,
                                    payloadHash,
                                    receivedAt,
                                    false,
                                    "REJECTED",
                                    exception.getMessage()));
            throw exception;
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(
                    ignored ->
                            logUnprocessedWebhook(
                                    provider.key(),
                                    normalizedEventId,
                                    payloadHash,
                                    receivedAt,
                                    true,
                                    "FAILED",
                                    exception.getMessage()));
            throw exception;
        }
        if (!normalizedEventId.equals(event.eventId())) {
            transaction.executeWithoutResult(
                    ignored ->
                            logUnprocessedWebhook(
                                    provider.key(),
                                    normalizedEventId,
                                    payloadHash,
                                    receivedAt,
                                    false,
                                    "REJECTED",
                                    "Identifiant de webhook incohérent"));
            throw new OnlinePaymentSignatureException("Identifiant de webhook incohérent");
        }
        WebhookReservation reservation =
                transaction.execute(
                        ignored -> reserveWebhook(provider.key(), event, payloadHash, receivedAt));
        if (reservation.duplicate()) {
            return new WebhookReceipt(
                    reservation.webhookId(),
                    event.eventId(),
                    reservation.onlinePaymentId(),
                    reservation.status(),
                    true,
                    reservation.deliveryCount());
        }
        try {
            PaymentVerification verification = provider.verifyPayment(event.providerPaymentId());
            WebhookReceipt processed =
                    transaction.execute(
                            ignored ->
                                    processWebhook(
                                            reservation.webhookId(),
                                            provider,
                                            event,
                                            verification,
                                            ipAddress));
            return processed;
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(
                    ignored -> failWebhook(reservation.webhookId(), exception.getMessage()));
            throw exception;
        }
    }

    public OnlinePaymentView completeSandbox(
            PortalIdentity parent, UUID paymentId, String ipAddress) {
        OnlinePaymentView intent = familyPayment(parent, paymentId);
        if (!"sandbox".equals(intent.providerKey())) {
            throw new BillingConflictException("Cette action est réservée au fournisseur sandbox");
        }
        if (!"PENDING".equals(intent.status()) && !"AUTHORIZED".equals(intent.status())) {
            return intent;
        }
        SandboxPaymentProvider.SignedSandboxEvent event =
                sandbox.signedEvent(
                        intent.providerPaymentId(), "PAID", intent.amount(), intent.currencyCode());
        handleWebhook("sandbox", event.eventId(), event.signature(), event.payload(), ipAddress);
        return familyPayment(parent, paymentId);
    }

    @PreAuthorize("hasAuthority('online-payment.refund')")
    public OnlineRefundView refund(
            SchoolPrincipal actor,
            UUID paymentId,
            String idempotencyKey,
            RefundOnlinePaymentCommand command,
            String ipAddress) {
        String key = idempotencyKey(idempotencyKey);
        RefundReservation reservation =
                transaction.execute(ignored -> reserveRefund(actor, paymentId, key, command));
        if (reservation.existing() != null) {
            return reservation.existing();
        }
        OnlinePaymentView payment = reservation.payment();
        PaymentProvider.ProviderRefund providerRefund;
        try {
            providerRefund =
                    provider(payment.providerKey())
                            .refund(
                                    new PaymentProvider.RefundRequest(
                                            payment.providerPaymentId(),
                                            reservation.refundId().toString(),
                                            payment.amount(),
                                            payment.currencyCode(),
                                            command.reason()));
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(
                    ignored -> failRefund(reservation.refundId(), exception.getMessage()));
            throw exception;
        }
        OnlineRefundView completed =
                transaction.execute(
                        ignored ->
                                completeRefund(
                                        actor,
                                        reservation,
                                        providerRefund,
                                        command.reason(),
                                        ipAddress));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "billing.online-payment.refunded",
                "OnlinePayment",
                paymentId.toString(),
                ipAddress,
                Map.of(
                        "provider", payment.providerKey(),
                        "providerRefundId", completed.providerRefundId()),
                payment,
                completed);
        return completed;
    }

    private OnlinePaymentView synchronizeVerification(
            UUID tenantId, OnlinePaymentView before, PaymentVerification verification) {
        String status = providerStatus(verification.status());
        Instant now = clock.instant();
        OnlinePaymentView locked = paymentForUpdate(tenantId, before.id());
        if ("PAID".equals(status) && locked.resultingPaymentId() == null) {
            PaymentView booked = bookLedgerPayment(locked, now, "provider-verification");
            jdbc.update(
                    "UPDATE billing_online_payment SET status='PAID',resulting_payment_id=?,"
                        + " verified_at=?,completed_at=?,updated_at=? WHERE tenant_id=? AND id=?",
                    booked.id(),
                    timestamp(verification.verifiedAt()),
                    timestamp(now),
                    timestamp(now),
                    tenantId,
                    locked.id());
        } else if (!"UNKNOWN".equals(status) && !"REFUNDED".equals(locked.status())) {
            jdbc.update(
                    "UPDATE billing_online_payment SET status=?,verified_at=?,updated_at=?"
                            + " WHERE tenant_id=? AND id=?",
                    status,
                    timestamp(verification.verifiedAt()),
                    timestamp(now),
                    tenantId,
                    locked.id());
        }
        return payment(tenantId, locked.id());
    }

    private WebhookReceipt processWebhook(
            UUID webhookId,
            PaymentProvider provider,
            PaymentWebhookEvent event,
            PaymentVerification verification,
            String ipAddress) {
        OnlinePaymentView intent = paymentByProvider(provider.key(), event.providerPaymentId());
        if (intent == null) {
            completeWebhook(webhookId, null, "IGNORED", "Intention de paiement inconnue");
            return new WebhookReceipt(webhookId, event.eventId(), null, "IGNORED", false, 1);
        }
        validateProviderAmount(intent, event.amount(), event.currencyCode());
        validateProviderAmount(intent, verification.amount(), verification.currencyCode());
        String eventStatus = providerStatus(event.status());
        String verifiedStatus = providerStatus(verification.status());
        if (!eventStatus.equals(verifiedStatus)) {
            throw new BillingConflictException(
                    "Le statut du webhook ne correspond pas à la vérification fournisseur");
        }
        OnlinePaymentView locked = paymentForUpdate(intent.tenantId(), intent.id());
        Instant now = clock.instant();
        if ("PAID".equals(eventStatus) && locked.resultingPaymentId() == null) {
            PaymentView booked = bookLedgerPayment(locked, now, ipAddress);
            jdbc.update(
                    "UPDATE billing_online_payment SET status='PAID',resulting_payment_id=?,"
                        + " verified_at=?,completed_at=?,updated_at=? WHERE tenant_id=? AND id=?",
                    booked.id(),
                    timestamp(verification.verifiedAt()),
                    timestamp(now),
                    timestamp(now),
                    locked.tenantId(),
                    locked.id());
            audit.record(
                    locked.tenantId(),
                    null,
                    "billing.online-payment.paid",
                    "OnlinePayment",
                    locked.id().toString(),
                    ipAddress,
                    Map.of(
                            "provider", provider.key(),
                            "eventId", event.eventId(),
                            "paymentNumber", booked.paymentNumber()),
                    locked,
                    booked);
        } else if ("REFUNDED".equals(eventStatus)
                && locked.resultingPaymentId() != null
                && !"REFUNDED".equals(locked.status())) {
            billing.reverseOnlinePayment(
                    locked.tenantId(),
                    locked.resultingPaymentId(),
                    "Remboursement confirmé par " + provider.key(),
                    ipAddress);
            jdbc.update(
                    "UPDATE billing_online_payment SET status='REFUNDED',verified_at=?,updated_at=?"
                            + " WHERE tenant_id=? AND id=?",
                    timestamp(verification.verifiedAt()),
                    timestamp(now),
                    locked.tenantId(),
                    locked.id());
        } else if (!TERMINAL_STATUSES.contains(locked.status())) {
            jdbc.update(
                    "UPDATE billing_online_payment SET status=?,verified_at=?,updated_at=?,"
                            + " completed_at=? WHERE tenant_id=? AND id=?",
                    eventStatus,
                    timestamp(verification.verifiedAt()),
                    timestamp(now),
                    TERMINAL_STATUSES.contains(eventStatus) ? timestamp(now) : null,
                    locked.tenantId(),
                    locked.id());
        }
        completeWebhook(webhookId, locked.id(), "PROCESSED", null);
        return new WebhookReceipt(webhookId, event.eventId(), locked.id(), eventStatus, false, 1);
    }

    private PaymentView bookLedgerPayment(OnlinePaymentView intent, Instant receivedAt, String ip) {
        List<AllocationCommand> invoiceAllocations =
                intent.invoiceId() == null
                        ? List.of()
                        : List.of(new AllocationCommand(intent.invoiceId(), intent.amount()));
        List<ScheduleAllocationCommand> scheduleAllocations =
                intent.paymentScheduleId() == null
                        ? List.of()
                        : List.of(
                                new ScheduleAllocationCommand(
                                        intent.paymentScheduleId(), intent.amount()));
        return billing.recordOnlinePayment(
                intent.tenantId(),
                intent.familyAccountId(),
                new PaymentCommand(
                        intent.amount(),
                        "ONLINE_PAYMENT",
                        "online:" + intent.id(),
                        receivedAt,
                        invoiceAllocations,
                        scheduleAllocations),
                ip);
    }

    private WebhookReservation reserveWebhook(
            String providerKey, PaymentWebhookEvent event, String payloadHash, Instant receivedAt) {
        List<WebhookReservation> existing =
                jdbc.query(
                        "SELECT id,online_payment_id,processing_status,delivery_count FROM"
                                + " billing_payment_webhook WHERE provider_key=? AND event_id=?",
                        (result, row) ->
                                new WebhookReservation(
                                        result.getObject("id", UUID.class),
                                        result.getObject("online_payment_id", UUID.class),
                                        result.getString("processing_status"),
                                        true,
                                        result.getInt("delivery_count") + 1),
                        providerKey,
                        event.eventId());
        if (!existing.isEmpty()) {
            WebhookReservation duplicate = existing.getFirst();
            jdbc.update(
                    "UPDATE billing_payment_webhook SET delivery_count=delivery_count+1,"
                            + " last_received_at=? WHERE id=?",
                    timestamp(receivedAt),
                    duplicate.webhookId());
            return duplicate;
        }
        OnlinePaymentView intent = paymentByProvider(providerKey, event.providerPaymentId());
        UUID webhookId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO billing_payment_webhook
                  (id,tenant_id,online_payment_id,provider_key,event_id,provider_payment_id,
                   signature_valid,processing_status,payload_sha256,received_at,last_received_at,
                   delivery_count,processed_at,error_message)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, 'RECEIVED', ?, ?, ?, 1, NULL, NULL)
                """,
                webhookId,
                intent == null ? null : intent.tenantId(),
                intent == null ? null : intent.id(),
                providerKey,
                event.eventId(),
                event.providerPaymentId(),
                payloadHash,
                timestamp(receivedAt),
                timestamp(receivedAt));
        return new WebhookReservation(
                webhookId, intent == null ? null : intent.id(), "RECEIVED", false, 1);
    }

    private void logUnprocessedWebhook(
            String providerKey,
            String eventId,
            String payloadHash,
            Instant receivedAt,
            boolean signatureValid,
            String status,
            String error) {
        int updated =
                jdbc.update(
                        "UPDATE billing_payment_webhook SET delivery_count=delivery_count+1,"
                                + " last_received_at=?,signature_valid=?,processing_status=?,"
                                + " processed_at=?,error_message=?"
                                + " WHERE provider_key=? AND event_id=?",
                        timestamp(receivedAt),
                        signatureValid,
                        status,
                        timestamp(receivedAt),
                        truncate(error, 500),
                        providerKey,
                        eventId);
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO billing_payment_webhook
                      (id,tenant_id,online_payment_id,provider_key,event_id,provider_payment_id,
                       signature_valid,processing_status,payload_sha256,received_at,last_received_at,
                       delivery_count,processed_at,error_message)
                    VALUES (?, NULL, NULL, ?, ?, NULL, ?, ?, ?, ?, ?, 1, ?, ?)
                    """,
                    UUID.randomUUID(),
                    providerKey,
                    eventId,
                    signatureValid,
                    status,
                    payloadHash,
                    timestamp(receivedAt),
                    timestamp(receivedAt),
                    timestamp(receivedAt),
                    truncate(error, 500));
        }
    }

    private void completeWebhook(
            UUID webhookId, UUID onlinePaymentId, String status, String error) {
        OnlinePaymentView intent =
                onlinePaymentId == null ? null : paymentAnyTenant(onlinePaymentId);
        jdbc.update(
                "UPDATE billing_payment_webhook SET tenant_id=?,online_payment_id=?,"
                        + " processing_status=?,processed_at=?,error_message=? WHERE id=?",
                intent == null ? null : intent.tenantId(),
                onlinePaymentId,
                status,
                timestamp(clock.instant()),
                truncate(error, 500),
                webhookId);
    }

    private void failWebhook(UUID webhookId, String error) {
        jdbc.update(
                "UPDATE billing_payment_webhook SET processing_status='FAILED',processed_at=?,"
                        + " error_message=? WHERE id=?",
                timestamp(clock.instant()),
                truncate(error, 500),
                webhookId);
    }

    private RefundReservation reserveRefund(
            SchoolPrincipal actor,
            UUID paymentId,
            String idempotencyKey,
            RefundOnlinePaymentCommand command) {
        OnlineRefundView existing = refundByIdempotency(actor.tenantId(), idempotencyKey);
        if (existing != null) {
            return new RefundReservation(existing.id(), null, existing);
        }
        OnlinePaymentView payment = paymentForUpdate(actor.tenantId(), paymentId);
        if (!"PAID".equals(payment.status()) || payment.resultingPaymentId() == null) {
            throw new BillingConflictException("Le paiement en ligne n'est pas remboursable");
        }
        if (command == null) {
            throw new BillingValidationException("Le remboursement est requis");
        }
        BigDecimal amount = positiveMoney(command.amount(), "Le montant du remboursement");
        if (amount.compareTo(payment.amount()) != 0) {
            throw new BillingValidationException(
                    "Le jalon 17 autorise uniquement le remboursement intégral");
        }
        String reason = required(command.reason(), 500, "Motif du remboursement");
        UUID refundId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO billing_online_refund
                  (id,tenant_id,online_payment_id,provider_refund_id,idempotency_key,amount,
                   currency_code,status,reason,error_message,resulting_reversal_payment_id,
                   requested_by,
                   created_at,processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, NULL, NULL, ?, ?, NULL)
                """,
                refundId,
                actor.tenantId(),
                paymentId,
                "pending:" + refundId,
                idempotencyKey,
                amount,
                payment.currencyCode(),
                reason,
                actor.accountId(),
                timestamp(now));
        return new RefundReservation(refundId, payment, null);
    }

    private OnlineRefundView completeRefund(
            SchoolPrincipal actor,
            RefundReservation reservation,
            PaymentProvider.ProviderRefund providerRefund,
            String reason,
            String ipAddress) {
        OnlinePaymentView payment = paymentForUpdate(actor.tenantId(), reservation.payment().id());
        if (!"SUCCEEDED".equals(providerRefund.status())) {
            throw new BillingConflictException("Le fournisseur n'a pas confirmé le remboursement");
        }
        if (money(providerRefund.amount()).compareTo(payment.amount()) != 0
                || !payment.currencyCode().equals(providerRefund.currencyCode())) {
            throw new BillingConflictException("Le remboursement fournisseur est incohérent");
        }
        PaymentView reversal =
                billing.reverseOnlinePayment(
                        actor.tenantId(), payment.resultingPaymentId(), reason, ipAddress);
        Instant processedAt =
                providerRefund.processedAt() == null
                        ? clock.instant()
                        : providerRefund.processedAt();
        jdbc.update(
                "UPDATE billing_online_refund SET provider_refund_id=?,status='SUCCEEDED',"
                    + " resulting_reversal_payment_id=?,processed_at=? WHERE tenant_id=? AND id=?",
                required(providerRefund.providerRefundId(), 160, "Référence remboursement"),
                reversal.id(),
                timestamp(processedAt),
                actor.tenantId(),
                reservation.refundId());
        jdbc.update(
                "UPDATE billing_online_payment SET status='REFUNDED',updated_at=?"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(processedAt),
                actor.tenantId(),
                payment.id());
        return refund(actor.tenantId(), reservation.refundId());
    }

    private void failRefund(UUID refundId, String error) {
        jdbc.update(
                "UPDATE billing_online_refund SET status='FAILED',error_message=?,processed_at=?"
                        + " WHERE id=?",
                truncate(error, 500),
                timestamp(clock.instant()),
                refundId);
    }

    private OnlinePaymentView payment(UUID tenantId, UUID id) {
        return paymentQuery(" WHERE op.tenant_id=? AND op.id=?", tenantId, id);
    }

    private OnlinePaymentView paymentForUpdate(UUID tenantId, UUID id) {
        List<OnlinePaymentView> rows =
                jdbc.query(
                        baseSelect() + " WHERE op.tenant_id=? AND op.id=? FOR UPDATE",
                        (result, row) -> paymentView(result),
                        tenantId,
                        id);
        if (rows.isEmpty()) {
            throw new BillingNotFoundException("Paiement en ligne introuvable");
        }
        return rows.getFirst();
    }

    private OnlinePaymentView paymentAnyTenant(UUID id) {
        List<OnlinePaymentView> rows =
                jdbc.query(
                        baseSelect() + " WHERE op.id=?", (result, row) -> paymentView(result), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private OnlinePaymentView paymentByProvider(String providerKey, String providerPaymentId) {
        List<OnlinePaymentView> rows =
                jdbc.query(
                        baseSelect() + " WHERE op.provider_key=? AND op.provider_payment_id=?",
                        (result, row) -> paymentView(result),
                        providerKey,
                        providerPaymentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private OnlinePaymentView paymentByIdempotency(UUID tenantId, String idempotencyKey) {
        List<OnlinePaymentView> rows =
                jdbc.query(
                        baseSelect() + " WHERE op.tenant_id=? AND op.idempotency_key=?",
                        (result, row) -> paymentView(result),
                        tenantId,
                        idempotencyKey);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private OnlinePaymentView paymentQuery(String where, Object... arguments) {
        List<OnlinePaymentView> rows =
                jdbc.query(baseSelect() + where, (result, row) -> paymentView(result), arguments);
        if (rows.isEmpty()) {
            throw new BillingNotFoundException("Paiement en ligne introuvable");
        }
        return rows.getFirst();
    }

    private List<OnlinePaymentView> payments(UUID tenantId, String extra, Object... arguments) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(tenantId);
        parameters.addAll(List.of(arguments));
        return jdbc.query(
                baseSelect() + " WHERE op.tenant_id=?" + extra + " ORDER BY op.created_at DESC",
                (result, row) -> paymentView(result),
                parameters.toArray());
    }

    private static String baseSelect() {
        return """
        SELECT op.id,op.tenant_id,op.family_account_id,fa.account_number,fa.display_name,
               op.invoice_id,i.invoice_number,op.payment_schedule_id,p.plan_number,s.due_date,
               op.provider_key,op.provider_payment_id,op.checkout_url,op.amount,
               op.currency_code,op.status,op.resulting_payment_id,op.expires_at,
               op.verified_at,op.completed_at,op.created_at,op.updated_at
        FROM billing_online_payment op
        JOIN billing_family_account fa
          ON fa.id=op.family_account_id AND fa.tenant_id=op.tenant_id
        LEFT JOIN billing_invoice i
          ON i.id=op.invoice_id AND i.tenant_id=op.tenant_id
        LEFT JOIN billing_payment_schedule s
          ON s.id=op.payment_schedule_id AND s.tenant_id=op.tenant_id
        LEFT JOIN billing_installment_plan p
          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
        """;
    }

    private static OnlinePaymentView paymentView(ResultSet result) throws SQLException {
        return new OnlinePaymentView(
                result.getObject("id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("family_account_id", UUID.class),
                result.getString("account_number"),
                result.getString("display_name"),
                result.getObject("invoice_id", UUID.class),
                result.getString("invoice_number"),
                result.getObject("payment_schedule_id", UUID.class),
                result.getString("plan_number"),
                result.getObject("due_date", java.time.LocalDate.class),
                result.getString("provider_key"),
                result.getString("provider_payment_id"),
                result.getString("checkout_url"),
                money(result.getBigDecimal("amount")),
                result.getString("currency_code"),
                result.getString("status"),
                result.getObject("resulting_payment_id", UUID.class),
                instant(result, "expires_at"),
                instant(result, "verified_at"),
                instant(result, "completed_at"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private OnlineRefundView refund(UUID tenantId, UUID id) {
        List<OnlineRefundView> rows =
                jdbc.query(
                        """
                        SELECT r.id,r.online_payment_id,r.provider_refund_id,r.amount,r.currency_code,
                               r.status,r.reason,r.resulting_reversal_payment_id,r.created_at,
                               r.processed_at
                        FROM billing_online_refund r
                        WHERE r.tenant_id=? AND r.id=?
                        """,
                        (result, row) -> refundView(result),
                        tenantId,
                        id);
        if (rows.isEmpty()) {
            throw new BillingNotFoundException("Remboursement en ligne introuvable");
        }
        return rows.getFirst();
    }

    private OnlineRefundView refundByIdempotency(UUID tenantId, String key) {
        List<OnlineRefundView> rows =
                jdbc.query(
                        """
                        SELECT r.id,r.online_payment_id,r.provider_refund_id,r.amount,r.currency_code,
                               r.status,r.reason,r.resulting_reversal_payment_id,r.created_at,
                               r.processed_at
                        FROM billing_online_refund r
                        WHERE r.tenant_id=? AND r.idempotency_key=?
                        """,
                        (result, row) -> refundView(result),
                        tenantId,
                        key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static OnlineRefundView refundView(ResultSet result) throws SQLException {
        return new OnlineRefundView(
                result.getObject("id", UUID.class),
                result.getObject("online_payment_id", UUID.class),
                result.getString("provider_refund_id"),
                money(result.getBigDecimal("amount")),
                result.getString("currency_code"),
                result.getString("status"),
                result.getString("reason"),
                result.getObject("resulting_reversal_payment_id", UUID.class),
                instant(result, "created_at"),
                instant(result, "processed_at"));
    }

    private FamilyAccountRef parentAccount(PortalIdentity parent, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<FamilyAccountRef> rows =
                jdbc.query(
                        "SELECT id,portal_account_id,account_number,currency_code"
                                + " FROM billing_family_account WHERE tenant_id=?"
                                + " AND portal_account_id=? AND status='ACTIVE'"
                                + suffix,
                        (result, row) ->
                                new FamilyAccountRef(
                                        result.getObject("id", UUID.class),
                                        result.getObject("portal_account_id", UUID.class),
                                        result.getString("account_number"),
                                        result.getString("currency_code")),
                        parent.tenantId(),
                        parent.accountId());
        if (rows.isEmpty()) {
            throw new BillingNotFoundException("Compte financier famille introuvable");
        }
        return rows.getFirst();
    }

    private void requireParentOwner(PortalIdentity parent, OnlinePaymentView payment) {
        FamilyAccountRef account = parentAccount(parent, false);
        if (!payment.tenantId().equals(parent.tenantId())
                || !payment.familyAccountId().equals(account.id())) {
            throw new BillingNotFoundException("Paiement en ligne introuvable");
        }
    }

    private void requireFamilyAccount(UUID tenantId, UUID accountId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM billing_family_account WHERE tenant_id=? AND id=?",
                        Integer.class,
                        tenantId,
                        accountId);
        if (count == null || count == 0) {
            throw new BillingNotFoundException("Compte famille introuvable");
        }
    }

    private InvoiceBalance invoice(UUID tenantId, UUID invoiceId) {
        List<InvoiceBalance> rows =
                jdbc.query(
                        "SELECT family_account_id,balance_amount,status FROM billing_invoice"
                                + " WHERE tenant_id=? AND id=?",
                        (result, row) ->
                                new InvoiceBalance(
                                        result.getObject("family_account_id", UUID.class),
                                        money(result.getBigDecimal("balance_amount")),
                                        result.getString("status")),
                        tenantId,
                        invoiceId);
        if (rows.isEmpty()) {
            throw new BillingNotFoundException("Facture introuvable");
        }
        return rows.getFirst();
    }

    private ScheduleBalance schedule(UUID tenantId, UUID scheduleId) {
        List<ScheduleBalance> rows =
                jdbc.query(
                        """
                        SELECT p.family_account_id,s.amount,s.paid_amount
                        FROM billing_payment_schedule s
                        JOIN billing_installment_plan p
                          ON p.id=s.installment_plan_id AND p.tenant_id=s.tenant_id
                        WHERE s.tenant_id=? AND s.id=? AND p.status='ACTIVE'
                        """,
                        (result, row) ->
                                new ScheduleBalance(
                                        result.getObject("family_account_id", UUID.class),
                                        money(result.getBigDecimal("amount")),
                                        money(result.getBigDecimal("paid_amount"))),
                        tenantId,
                        scheduleId);
        if (rows.isEmpty()) {
            throw new BillingNotFoundException("Échéance active introuvable");
        }
        return rows.getFirst();
    }

    private PaymentProvider provider(String requestedKey) {
        String key =
                requestedKey == null || requestedKey.isBlank()
                        ? "sandbox"
                        : requestedKey.trim().toLowerCase(Locale.ROOT);
        PaymentProvider provider = providers.get(key);
        if (provider == null) {
            throw new BillingValidationException("Fournisseur de paiement inconnu");
        }
        return provider;
    }

    private static void validateProviderAmount(
            OnlinePaymentView intent, BigDecimal amount, String currencyCode) {
        if (amount == null
                || money(amount).compareTo(intent.amount()) != 0
                || currencyCode == null
                || !currencyCode.equalsIgnoreCase(intent.currencyCode())) {
            throw new BillingConflictException("Le montant fournisseur est incohérent");
        }
    }

    private static String providerStatus(String value) {
        if (value == null) {
            throw new BillingValidationException("Statut fournisseur absent");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("UNKNOWN".equals(normalized)) {
            return normalized;
        }
        if (!Set.of("PENDING", "AUTHORIZED", "PAID", "FAILED", "CANCELLED", "REFUNDED")
                .contains(normalized)) {
            throw new BillingValidationException("Statut fournisseur inconnu");
        }
        return normalized;
    }

    private static String normalizedReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "/finance";
        }
        String value = returnUrl.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.length() > 500) {
            throw new BillingValidationException("URL de retour invalide");
        }
        return value;
    }

    private static String idempotencyKey(String value) {
        String key = required(value, 120, "Clé d'idempotence");
        if (key.length() < 8) {
            throw new BillingValidationException("La clé d'idempotence est trop courte");
        }
        return key;
    }

    private static BigDecimal positiveMoney(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new BillingValidationException(label + " doit être strictement positif");
        }
        return money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String required(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            throw new BillingValidationException(label + " requis");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new BillingValidationException(label + " dépasse " + max + " caractères");
        }
        return normalized;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record FamilyAccountRef(
            UUID id, UUID portalAccountId, String accountNumber, String currencyCode) {}

    private record InvoiceBalance(UUID familyAccountId, BigDecimal balance, String status) {}

    private record ScheduleBalance(
            UUID familyAccountId, BigDecimal amount, BigDecimal paidAmount) {}

    private record WebhookReservation(
            UUID webhookId,
            UUID onlinePaymentId,
            String status,
            boolean duplicate,
            int deliveryCount) {}

    private record RefundReservation(
            UUID refundId, OnlinePaymentView payment, OnlineRefundView existing) {}

    public record InitializeOnlinePaymentCommand(
            String providerKey,
            BigDecimal amount,
            UUID invoiceId,
            UUID paymentScheduleId,
            String returnUrl) {}

    public record RefundOnlinePaymentCommand(BigDecimal amount, String reason) {}

    public record OnlinePaymentView(
            UUID id,
            UUID tenantId,
            UUID familyAccountId,
            String accountNumber,
            String familyName,
            UUID invoiceId,
            String invoiceNumber,
            UUID paymentScheduleId,
            String planNumber,
            java.time.LocalDate dueDate,
            String providerKey,
            String providerPaymentId,
            String checkoutUrl,
            BigDecimal amount,
            String currencyCode,
            String status,
            UUID resultingPaymentId,
            Instant expiresAt,
            Instant verifiedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record OnlineRefundView(
            UUID id,
            UUID onlinePaymentId,
            String providerRefundId,
            BigDecimal amount,
            String currencyCode,
            String status,
            String reason,
            UUID resultingReversalPaymentId,
            Instant createdAt,
            Instant processedAt) {}

    public record WebhookReceipt(
            UUID webhookId,
            String eventId,
            UUID onlinePaymentId,
            String status,
            boolean duplicate,
            int deliveryCount) {}
}
