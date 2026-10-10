package ma.schoolz.erp.webhooks.application;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface WebhookTransport {
    Result send(URI endpoint, String payload, Map<String, String> headers, Duration timeout);

    record Result(Integer statusCode, String error) {
        public boolean delivered() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }
    }
}
