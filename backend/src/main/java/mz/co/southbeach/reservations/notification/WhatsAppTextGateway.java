package mz.co.southbeach.reservations.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Sends the customer messages (reservation confirmed, tickets paid...) as plain WhatsApp text through the WhatsApp Cloud API.
 *
 * Plain text, not templates: it carries no per-template charge, but WhatsApp only delivers it to a customer who wrote to the
 * business number in the last 24 hours (or to a number allowed on a test account). Any other recipient is refused by WhatsApp,
 * which is logged and never undoes the payment or the reservation. Off by default.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.whatsapp.enabled", havingValue = "true")
public class WhatsAppTextGateway implements SmsGateway {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppTextGateway.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String endpoint;
    private final String token;

    public WhatsAppTextGateway(
            @Value("${app.notifications.whatsapp.phone-number-id}") String phoneNumberId,
            @Value("${app.notifications.whatsapp.token}") String token,
            @Value("${app.notifications.whatsapp.api-version}") String apiVersion,
            @Value("${app.notifications.whatsapp.base-url}") String baseUrl) {
        if (phoneNumberId == null || phoneNumberId.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException("WhatsApp is enabled: set WHATSAPP_PHONE_NUMBER_ID and WHATSAPP_TOKEN.");
        }
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/" + apiVersion + "/" + phoneNumberId.strip() + "/messages";
        this.token = token.strip();
    }

    /** WhatsApp wants the number as digits with the country code. A local Mozambican mobile (9 digits starting with 8) gets 258. */
    static String normalize(String phone) {
        var digits = phone == null ? "" : phone.replaceAll("\\D", "").replaceFirst("^0+", "");
        if (digits.length() == 9 && digits.startsWith("8")) digits = "258" + digits;
        if (digits.length() < 10 || digits.length() > 15) throw new IllegalArgumentException("Not a usable phone number");
        return digits;
    }

    @Override
    public void send(String phone, String text) {
        String body;
        try {
            body = json.writeValueAsString(Map.of(
                    "messaging_product", "whatsapp", "recipient_type", "individual", "to", normalize(phone),
                    "type", "text", "text", Map.of("preview_url", true, "body", text.length() > 4000 ? text.substring(0, 4000) : text)));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build the WhatsApp message", exception);
        }
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException(describe(response.statusCode(), response.body()));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("WhatsApp is unreachable (" + exception.getClass().getSimpleName() + ")");
        }
    }

    /** A short, safe explanation: the error code and message from WhatsApp, never the token or the message text. */
    private String describe(int status, String responseBody) {
        try {
            JsonNode error = json.readTree(responseBody).path("error");
            int code = error.path("code").asInt();
            var hint = switch (code) {
                case 131047 -> " (the customer has not written to this number in the last 24 hours: plain text is refused)";
                case 131030 -> " (the number is not on the list of recipients allowed for this test account)";
                case 190 -> " (the access token expired or is invalid)";
                case 131056 -> " (too many messages to this customer; try again later)";
                default -> "";
            };
            return "WhatsApp refused the message: HTTP " + status + ", code " + code + " — " + error.path("message").asText("no details") + hint;
        } catch (IOException exception) {
            return "WhatsApp refused the message: HTTP " + status;
        }
    }
}
