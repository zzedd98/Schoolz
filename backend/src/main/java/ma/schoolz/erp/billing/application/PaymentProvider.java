package ma.schoolz.erp.billing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public interface PaymentProvider {
    String key();

    PaymentInitialization initializePayment(InitializePaymentRequest request);

    PaymentWebhookEvent handleWebhook(WebhookRequest request);

    PaymentVerification verifyPayment(String providerPaymentId);

    ProviderRefund refund(RefundRequest request);

    record InitializePaymentRequest(
            String merchantReference,
            BigDecimal amount,
            String currencyCode,
            String customerReference,
            String returnUrl,
            Instant expiresAt) {}

    record PaymentInitialization(
            String providerPaymentId, String checkoutUrl, String status, Instant expiresAt) {}

    record WebhookRequest(String eventId, String signature, String payload) {}

    record PaymentWebhookEvent(
            String eventId,
            String providerPaymentId,
            String status,
            BigDecimal amount,
            String currencyCode,
            Map<String, String> metadata) {}

    record PaymentVerification(
            String providerPaymentId,
            String status,
            BigDecimal amount,
            String currencyCode,
            Instant verifiedAt) {}

    record RefundRequest(
            String providerPaymentId,
            String merchantReference,
            BigDecimal amount,
            String currencyCode,
            String reason) {}

    record ProviderRefund(
            String providerRefundId,
            String status,
            BigDecimal amount,
            String currencyCode,
            Instant processedAt) {}
}
