package ma.schoolz.erp.billing.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SandboxPaymentProvider implements PaymentProvider {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper json;
    private final Clock clock;
    private final String webhookSecret;
    private final Map<String, SandboxState> payments = new ConcurrentHashMap<>();

    public SandboxPaymentProvider(
            ObjectMapper json,
            Clock clock,
            @Value("${schoolz.payments.sandbox.webhook-secret:}") String webhookSecret) {
        this.json = json;
        this.clock = clock;
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    @Override
    public String key() {
        return "sandbox";
    }

    @Override
    public PaymentInitialization initializePayment(InitializePaymentRequest request) {
        requireConfigured();
        String providerPaymentId = "sbx_pay_" + UUID.randomUUID().toString().replace("-", "");
        SandboxState state =
                new SandboxState(
                        providerPaymentId,
                        "PENDING",
                        request.amount(),
                        request.currencyCode(),
                        request.expiresAt());
        payments.put(providerPaymentId, state);
        return new PaymentInitialization(
                providerPaymentId,
                "/payments/sandbox/" + request.merchantReference(),
                state.status(),
                state.expiresAt());
    }

    @Override
    public PaymentWebhookEvent handleWebhook(WebhookRequest request) {
        requireConfigured();
        String expected = sign(request.payload());
        if (request.signature() == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        request.signature()
                                .trim()
                                .toLowerCase(Locale.ROOT)
                                .getBytes(StandardCharsets.US_ASCII))) {
            throw new OnlinePaymentSignatureException("Signature de webhook invalide");
        }
        SandboxWebhook payload;
        try {
            payload = json.readValue(request.payload(), SandboxWebhook.class);
        } catch (JsonProcessingException exception) {
            throw new BillingValidationException("Payload de webhook sandbox invalide");
        }
        if (payload.eventId() == null
                || payload.paymentId() == null
                || payload.status() == null
                || payload.amount() == null
                || payload.currency() == null) {
            throw new BillingValidationException("Webhook sandbox incomplet");
        }
        if (!payload.eventId().equals(request.eventId())) {
            throw new OnlinePaymentSignatureException("Identifiant d'événement incohérent");
        }
        String status = normalizeStatus(payload.status());
        payments.put(
                payload.paymentId(),
                new SandboxState(
                        payload.paymentId(),
                        status,
                        money(payload.amount()),
                        payload.currency().toUpperCase(Locale.ROOT),
                        clock.instant().plusSeconds(900)));
        return new PaymentWebhookEvent(
                payload.eventId(),
                payload.paymentId(),
                status,
                money(payload.amount()),
                payload.currency().toUpperCase(Locale.ROOT),
                Map.of("sandbox", "true"));
    }

    @Override
    public PaymentVerification verifyPayment(String providerPaymentId) {
        requireConfigured();
        SandboxState state = payments.get(providerPaymentId);
        if (state == null) {
            return new PaymentVerification(
                    providerPaymentId, "UNKNOWN", BigDecimal.ZERO, "MAD", clock.instant());
        }
        return new PaymentVerification(
                state.providerPaymentId(),
                state.status(),
                state.amount(),
                state.currencyCode(),
                clock.instant());
    }

    @Override
    public ProviderRefund refund(RefundRequest request) {
        requireConfigured();
        SandboxState state = payments.get(request.providerPaymentId());
        if (state == null || !"PAID".equals(state.status())) {
            throw new BillingConflictException("Le paiement sandbox n'est pas remboursable");
        }
        if (money(request.amount()).compareTo(state.amount()) != 0
                || !request.currencyCode().equals(state.currencyCode())) {
            throw new BillingValidationException("Le remboursement sandbox doit être intégral");
        }
        payments.put(
                state.providerPaymentId(),
                new SandboxState(
                        state.providerPaymentId(),
                        "REFUNDED",
                        state.amount(),
                        state.currencyCode(),
                        state.expiresAt()));
        return new ProviderRefund(
                "sbx_ref_" + UUID.randomUUID().toString().replace("-", ""),
                "SUCCEEDED",
                state.amount(),
                state.currencyCode(),
                clock.instant());
    }

    public SignedSandboxEvent signedEvent(
            String providerPaymentId, String status, BigDecimal amount, String currencyCode) {
        requireConfigured();
        String eventId = "sbx_evt_" + UUID.randomUUID().toString().replace("-", "");
        try {
            String payload =
                    json.writeValueAsString(
                            new SandboxWebhook(
                                    eventId,
                                    providerPaymentId,
                                    status,
                                    money(amount),
                                    currencyCode));
            return new SignedSandboxEvent(eventId, payload, sign(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Impossible de créer l'événement sandbox", exception);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(
                    new SecretKeySpec(
                            webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de signer le webhook sandbox", exception);
        }
    }

    private void requireConfigured() {
        if (webhookSecret.length() < 24) {
            throw new BillingConflictException(
                    "Le fournisseur sandbox exige un secret webhook d'au moins 24 caractères");
        }
    }

    private static String normalizeStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("PENDING", "AUTHORIZED", "PAID", "FAILED", "CANCELLED", "REFUNDED")
                .contains(normalized)) {
            throw new BillingValidationException("Statut de paiement sandbox invalide");
        }
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private record SandboxState(
            String providerPaymentId,
            String status,
            BigDecimal amount,
            String currencyCode,
            Instant expiresAt) {}

    private record SandboxWebhook(
            String eventId, String paymentId, String status, BigDecimal amount, String currency) {}

    public record SignedSandboxEvent(String eventId, String payload, String signature) {}
}
