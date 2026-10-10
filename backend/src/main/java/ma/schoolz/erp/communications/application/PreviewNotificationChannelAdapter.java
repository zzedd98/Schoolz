package ma.schoolz.erp.communications.application;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PreviewNotificationChannelAdapter implements NotificationChannelAdapter {
    private static final Set<String> CHANNELS = Set.of("PUSH", "EMAIL", "SMS", "WHATSAPP");

    @Override
    public boolean supports(String channel) {
        return CHANNELS.contains(channel);
    }

    @Override
    public DispatchResult dispatch(NotificationEnvelope notification) {
        return new DispatchResult("PREVIEW", "preview:" + notification.channel().toLowerCase());
    }
}
