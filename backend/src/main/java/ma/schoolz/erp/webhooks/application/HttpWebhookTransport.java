package ma.schoolz.erp.webhooks.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class HttpWebhookTransport implements WebhookTransport {
    private final HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public Result send(
            URI endpoint, String payload, Map<String, String> headers, Duration timeout) {
        try {
            HttpRequest.Builder request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .POST(HttpRequest.BodyPublishers.ofString(payload));
            headers.forEach(request::header);
            HttpResponse<Void> response =
                    client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return new Result(response.statusCode(), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(null, "Livraison interrompue");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return new Result(
                    null,
                    message == null
                            ? exception.getClass().getSimpleName()
                            : message.substring(0, Math.min(message.length(), 900)));
        }
    }
}
