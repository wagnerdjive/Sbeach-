package mz.co.southbeach.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * The address WhatsApp calls: once to verify it, then for every message a customer writes to the business number and for
 * every delivery status. Calls are only trusted when they carry Meta's signature (HMAC-SHA256 with the app secret).
 */
@RestController
@RequestMapping("/api/whatsapp")
@ConditionalOnProperty(name = "app.notifications.whatsapp.enabled", havingValue = "true")
public class WhatsAppWebhookController {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppInboundService inbound;
    private final ObjectMapper json = new ObjectMapper();
    private final String verifyToken;
    private final String appSecret;
    private final String businessNumber;

    public WhatsAppWebhookController(WhatsAppInboundService inbound,
                                     @Value("${app.notifications.whatsapp.verify-token}") String verifyToken,
                                     @Value("${app.notifications.whatsapp.app-secret}") String appSecret,
                                     @Value("${app.notifications.whatsapp.business-number}") String businessNumber) {
        this.inbound = inbound;
        this.verifyToken = verifyToken == null ? "" : verifyToken.strip();
        this.appSecret = appSecret == null ? "" : appSecret.strip();
        this.businessNumber = businessNumber == null ? "" : businessNumber.replaceAll("\\D", "");
    }

    /** Meta's one-time check that this address is ours: echo the challenge when the shared verify token matches. */
    @GetMapping(value = "/webhook", produces = "text/plain")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && !verifyToken.isEmpty() && token != null && challenge != null && sameText(verifyToken, token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
    }

    @PostMapping(value = "/webhook")
    public ResponseEntity<Void> receive(@RequestBody byte[] raw, @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (appSecret.isEmpty()) {
            log.warn("WhatsApp webhook call ignored: WHATSAPP_APP_SECRET is not set, so the sender cannot be verified.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!validSignature(raw, signature)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        try {
            inbound.handle(json.readTree(raw));
        } catch (Exception exception) {
            log.warn("WhatsApp webhook payload could not be read: {}", exception.getClass().getSimpleName());
        }
        return ResponseEntity.ok().build(); // answered at once; the work happens in the background
    }

    /** The number customers write to, for the "receive on WhatsApp" button. Empty when not configured. */
    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        if (businessNumber.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(Map.of("number", businessNumber));
    }

    private boolean validSignature(byte[] raw, String header) {
        if (header == null || !header.startsWith("sha256=")) return false;
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(raw));
            return sameText(expected, header.substring("sha256=".length()).toLowerCase());
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean sameText(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
