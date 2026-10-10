package ma.schoolz.erp.communications.application;

import java.util.UUID;

public interface NotificationChannelAdapter {
    boolean supports(String channel);

    DispatchResult dispatch(NotificationEnvelope notification);

    record NotificationEnvelope(
            UUID tenantId,
            UUID notificationId,
            String channel,
            String recipient,
            String subject,
            String body,
            String idempotencyKey) {}

    record DispatchResult(String status, String adapterKey) {}
}
