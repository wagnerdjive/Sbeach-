package mz.co.southbeach.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.notification.ReservationMessages;
import mz.co.southbeach.reservations.notification.SmsGateway;
import mz.co.southbeach.reservations.repository.ReservationRepository;
import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketOrder;
import mz.co.southbeach.tickets.notification.TicketNotifier;
import mz.co.southbeach.tickets.repository.TicketOrderRepository;
import mz.co.southbeach.tickets.service.TicketPdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Answers customers who write to the business number, for example after tapping "receive on WhatsApp" on the site.
 * The reply is plain text inside the 24-hour window the customer just opened, so it carries no template.
 *
 * Privacy rule: details of an order or reservation only go to the WhatsApp number that made it. The sender's number comes from
 * WhatsApp itself (not from the text), so a reference typed by someone else gets the same answer as an unknown one.
 */
@Service
@ConditionalOnProperty(name = "app.notifications.whatsapp.enabled", havingValue = "true")
public class WhatsAppInboundService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);
    private static final Pattern REFERENCE = Pattern.compile("\\b(TK|SB)-[A-Z0-9]{12}\\b");
    private static final long MIN_GAP_MILLIS = 8_000; // at most one automatic reply per customer every 8 seconds

    private final TicketOrderRepository orders;
    private final ReservationRepository reservations;
    private final SmsGateway gateway;
    private final TicketPdfService pdfs;
    private final String siteUrl;
    private final Set<String> seenMessages = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) { return size() > 2000; }
    }));
    private final Map<String, Long> lastReply = new ConcurrentHashMap<>();

    public WhatsAppInboundService(TicketOrderRepository orders, ReservationRepository reservations, SmsGateway gateway, TicketPdfService pdfs,
                                  @Value("${app.site-url}") String siteUrl) {
        this.orders = orders;
        this.reservations = reservations;
        this.gateway = gateway;
        this.pdfs = pdfs;
        this.siteUrl = siteUrl;
    }

    @Async
    public void handle(JsonNode payload) {
        for (var entry : payload.path("entry")) {
            for (var change : entry.path("changes")) {
                var value = change.path("value");
                for (var status : value.path("statuses")) logStatus(status);
                for (var message : value.path("messages")) {
                    var id = message.path("id").asText("");
                    var from = message.path("from").asText("");
                    if (from.isEmpty() || (!id.isEmpty() && !seenMessages.add(id))) continue; // Meta may deliver the same message twice
                    try {
                        var answer = answer(from, message.path("type").asText(""), message.path("text").path("body").asText(""));
                        if (reply(from, answer.text()) && answer.ticketsToken() != null) sendPdf(from, answer.ticketsToken());
                    } catch (RuntimeException exception) {
                        log.warn("WhatsApp answer failed: {}", exception.getMessage());
                    }
                }
            }
        }
    }

    /** Delivery problems are otherwise invisible: surface them in the log (never the message text). */
    private void logStatus(JsonNode status) {
        if (!"failed".equals(status.path("status").asText())) return;
        var error = status.path("errors").path(0);
        log.warn("WhatsApp could not deliver a message: code {} — {}", error.path("code").asInt(), error.path("title").asText("no details"));
    }

    /** Returns false when the customer was answered a moment ago and nothing was sent. */
    private boolean reply(String to, String text) {
        var now = System.currentTimeMillis();
        var previous = lastReply.put(to, now);
        if (previous != null && now - previous < MIN_GAP_MILLIS) return false;
        gateway.send(to, text);
        return true;
    }

    private void sendPdf(String to, String accessToken) {
        if (!gateway.supportsDocuments()) return;
        try {
            pdfs.forAccessToken(accessToken).ifPresent(pdf -> gateway.sendDocument(to, pdf.filename(), "Os seus bilhetes em PDF.", pdf.bytes()));
        } catch (RuntimeException exception) {
            log.warn("WhatsApp ticket PDF could not be sent: {}", exception.getMessage());
        }
    }

    /** What to say, and the private ticket token when the PDF should follow. */
    record Answer(String text, String ticketsToken) {
        static Answer of(String text) { return new Answer(text, null); }
    }

    @Transactional(readOnly = true)
    Answer answer(String from, String type, String body) {
        var matcher = REFERENCE.matcher(body.toUpperCase(Locale.ROOT));
        if ("text".equals(type) && matcher.find()) {
            var reference = matcher.group();
            if (reference.startsWith("TK-")) {
                var order = orders.findByReference(reference).filter(o -> samePhone(o.getPhone(), from));
                return order.map(this::orderAnswer).orElse(Answer.of(notFound()));
            }
            var reservation = reservations.findByReference(reference).filter(r -> samePhone(r.getPhone(), from));
            return reservation.map(r -> Answer.of(reservationAnswer(r))).orElse(Answer.of(notFound()));
        }
        // No reference given: use the most recent order or reservation made with this very number.
        var order = orders.findTop200ByOrderByCreatedAtDesc().stream().filter(o -> samePhone(o.getPhone(), from)
                && (o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PENDING)).findFirst();
        Optional<Reservation> reservation = reservations.findTop200ByOrderByCreatedAtDesc().stream()
                .filter(r -> samePhone(r.getPhone(), from) && r.getStatus() != ReservationStatus.CANCELLED).findFirst();
        if (order.isPresent() && (reservation.isEmpty() || !order.get().getCreatedAt().isBefore(reservation.get().getCreatedAt()))) return orderAnswer(order.get());
        if (reservation.isPresent()) return Answer.of(reservationAnswer(reservation.get()));
        return Answer.of(help());
    }

    private Answer orderAnswer(TicketOrder order) {
        var first = order.getFullName().strip().split("\\s+")[0];
        if (order.getStatus() == OrderStatus.PAID) return new Answer(paidText(order, first), order.getAccessToken());
        return Answer.of(switch (order.getStatus()) {
            case PENDING -> "Olá, " + first + "! Recebemos o seu pedido da encomenda " + order.getReference()
                    + ". Assim que o pagamento for confirmado, enviamos aqui os seus bilhetes.";
            case REFUNDED -> "A encomenda " + order.getReference() + " foi reembolsada e os bilhetes ficaram anulados.";
            default -> "A encomenda " + order.getReference() + " expirou ou foi cancelada. Pode voltar a comprar em "
                    + siteUrl.replaceAll("/+$", "") + "/events.html";
        });
    }

    private String paidText(TicketOrder order, String first) {
        return "Olá, " + first + "! Aqui estão os seus bilhetes da encomenda " + order.getReference() + ": "
                + TicketNotifier.ticketsUrl(siteUrl, order.getAccessToken()) + "\nMostre o código QR à entrada. Cada código vale para uma pessoa. Segue também o PDF.";
    }

    private String reservationAnswer(Reservation r) {
        var first = r.getFullName().strip().split("\\s+")[0];
        return switch (r.getStatus()) {
            case CONFIRMED -> ReservationMessages.confirmed(r);
            case CANCELLED -> ReservationMessages.cancelled(r);
            default -> "Olá, " + first + "! Recebemos o seu pedido de reserva " + r.getReference() + " para " + ReservationMessages.when(r)
                    + ". Assim que a equipa confirmar, avisamos aqui.";
        };
    }

    private static String notFound() {
        return "Não encontrámos essa referência associada a este número. Escreva a partir do telemóvel que usou na compra ou na reserva.";
    }

    private static String help() {
        return "Olá! Sou o assistente automático do South Beach. Para receber os seus bilhetes ou a confirmação da sua reserva, escreva o número da "
                + "encomenda (TK-…) ou da reserva (SB-…), a partir do telemóvel que usou na compra.";
    }

    /** WhatsApp numbers are digits with the country code; the site stores what the customer typed. Compare the last nine digits. */
    static boolean samePhone(String stored, String whatsappNumber) {
        var a = tail(stored);
        return !a.isEmpty() && a.equals(tail(whatsappNumber));
    }

    private static String tail(String phone) {
        var digits = phone == null ? "" : phone.replaceAll("\\D", "");
        return digits.length() >= 9 ? digits.substring(digits.length() - 9) : "";
    }
}
