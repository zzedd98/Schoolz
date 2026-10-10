package ma.schoolz.erp.billing.application;

import java.util.UUID;

public interface ReminderChannelAdapter {
    boolean supports(String channel);

    DispatchResult dispatch(ReminderMessage message);

    record ReminderMessage(
            UUID tenantId,
            UUID familyAccountId,
            String recipient,
            String channel,
            String message,
            String idempotencyKey) {}

    record DispatchResult(String status, String adapterKey) {}
}
