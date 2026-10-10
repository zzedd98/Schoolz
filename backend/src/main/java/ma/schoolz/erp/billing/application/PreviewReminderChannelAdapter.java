package ma.schoolz.erp.billing.application;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PreviewReminderChannelAdapter implements ReminderChannelAdapter {
    private static final Set<String> CHANNELS = Set.of("PUSH", "EMAIL", "SMS", "WHATSAPP");

    @Override
    public boolean supports(String channel) {
        return CHANNELS.contains(channel);
    }

    @Override
    public DispatchResult dispatch(ReminderMessage message) {
        return new DispatchResult("PREVIEW", "preview:" + message.channel().toLowerCase());
    }
}
