package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.domain.Event;
import mz.co.southbeach.tickets.domain.IssuedTicket;
import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketStatus;
import mz.co.southbeach.tickets.repository.EventPosterRepository;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.repository.IssuedTicketRepository;
import mz.co.southbeach.tickets.repository.TicketOrderRepository;
import mz.co.southbeach.tickets.repository.TicketTypeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The customer's tickets as a PDF, one A4 page per person: the same card as the ticket page, built on the server so it can be sent anywhere. */
@Service
public class TicketPdfService {
    public record TicketPdf(String filename, byte[] bytes) { }

    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");
    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final Color BLACK = new Color(12, 15, 18), SUN = new Color(252, 179, 32), BLUE = new Color(40, 106, 151), GREY = new Color(85, 93, 100);
    private static final PDType1Font SANS = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font SANS_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font SERIF = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font MONO = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

    private final TicketOrderRepository orders;
    private final IssuedTicketRepository tickets;
    private final TicketTypeRepository types;
    private final EventRepository events;
    private final EventPosterRepository posters;
    private final QrService qr;

    public TicketPdfService(TicketOrderRepository orders, IssuedTicketRepository tickets, TicketTypeRepository types,
                            EventRepository events, EventPosterRepository posters, QrService qr) {
        this.orders = orders;
        this.tickets = tickets;
        this.types = types;
        this.events = events;
        this.posters = posters;
        this.qr = qr;
    }

    /** Empty when the link is unknown or the order has no tickets (not paid, expired or cancelled). */
    @Transactional(readOnly = true)
    public Optional<TicketPdf> forAccessToken(String token) {
        var order = orders.findByAccessToken(token);
        if (order.isEmpty() || (order.get().getStatus() != OrderStatus.PAID && order.get().getStatus() != OrderStatus.REFUNDED)) return Optional.empty();
        var issued = tickets.findByOrderIdOrderByIdAsc(order.get().getId());
        if (issued.isEmpty()) return Optional.empty();

        var typeNames = new HashMap<Long, String>();
        types.findAllById(issued.stream().map(IssuedTicket::getTicketTypeId).distinct().toList()).forEach(type -> typeNames.put(type.getId(), type.getName()));
        var eventsById = new HashMap<Long, Event>();
        events.findAllById(issued.stream().map(IssuedTicket::getEventId).distinct().toList()).forEach(event -> eventsById.put(event.getId(), event));
        var posterBytes = new HashMap<Long, byte[]>();
        eventsById.keySet().forEach(id -> posters.findById(id).ifPresent(poster -> posterBytes.put(id, poster.getData())));

        try (var document = new PDDocument()) {
            var logo = PDImageXObject.createFromByteArray(document, logoBytes(), "logo");
            for (int i = 0; i < issued.size(); i++) {
                var ticket = issued.get(i);
                page(document, logo, order.get().getReference(), ticket, i + 1, issued.size(), eventsById.get(ticket.getEventId()),
                        typeNames.getOrDefault(ticket.getTicketTypeId(), ""), posterBytes.get(ticket.getEventId()));
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return Optional.of(new TicketPdf("Bilhetes-" + order.get().getReference() + ".pdf", out.toByteArray()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] logoBytes() throws IOException {
        try (var in = TicketPdfService.class.getResourceAsStream("/pdf/logo.png")) {
            return in.readAllBytes();
        }
    }

    private void page(PDDocument document, PDImageXObject logo, String reference, IssuedTicket ticket, int number, int total,
                      Event event, String typeName, byte[] posterData) throws IOException {
        var page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        float left = 40, right = 555, width = right - left;
        try (var cs = new PDPageContentStream(document, page)) {
            // Header: logo and order reference
            float logoHeight = 52, logoWidth = logo.getWidth() * logoHeight / logo.getHeight();
            cs.drawImage(logo, left, 842 - 40 - logoHeight, logoWidth, logoHeight);
            text(cs, SANS, 9, GREY, "ENCOMENDA " + reference, right - width(SANS, 9, "ENCOMENDA " + reference), 842 - 62);

            // Black card header with poster, title, date and time
            float headerBottom = 540, headerTop = 735;
            cs.setNonStrokingColor(BLACK);
            cs.addRect(left, headerBottom, width, headerTop - headerBottom);
            cs.fill();
            float textX = left + 24;
            if (posterData != null) {
                try {
                    var poster = PDImageXObject.createFromByteArray(document, posterData, "poster");
                    float ph = 150, pw = ph * 4 / 5f;
                    cs.drawImage(poster, left + 24, headerTop - 24 - ph, pw, ph);
                    textX = left + 24 + pw + 24;
                } catch (IOException | RuntimeException unsupportedImage) { /* e.g. WebP: the ticket is fine without the poster */ }
            }
            float textWidth = right - 24 - textX;
            var title = event == null ? "" : event.getTitle();
            text(cs, SANS_BOLD, 8.5f, SUN, spaced("SOUTH BEACH · MAPUTO"), textX, headerTop - 34);
            float y = headerTop - 66;
            for (var line : wrap(SERIF, 28, title, textWidth - 70)) { text(cs, SERIF, 28, Color.WHITE, line, textX, y); y -= 32; }
            y -= 6;
            if (event != null) {
                text(cs, SANS, 11.5f, Color.WHITE, dateLine(event.getStartsAt()), textX, y);
                text(cs, SANS_BOLD, 11.5f, Color.WHITE, timeLine(event.getStartsAt(), event.getEndsAt()), textX, y - 18);
            }
            // Category chip
            var chip = typeName.toUpperCase(PT);
            float chipWidth = width(SANS_BOLD, 10, chip) + 28;
            cs.setNonStrokingColor(SUN);
            cs.addRect(right - 24 - chipWidth, headerTop - 30, chipWidth, 30);
            cs.fill();
            text(cs, SANS_BOLD, 10, BLACK, chip, right - 24 - chipWidth + 14, headerTop - 20);

            // White body with a hairline frame and a tear line
            cs.setStrokingColor(new Color(210, 210, 205));
            cs.setLineWidth(0.8f);
            cs.addRect(left, 150, width, headerBottom - 150);
            cs.stroke();
            cs.setLineDashPattern(new float[]{5, 4}, 0);
            cs.moveTo(left + 18, headerBottom - 22);
            cs.lineTo(right - 18, headerBottom - 22);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);

            // QR
            float qrSize = 230, qrX = (595 - qrSize) / 2, qrY = 270;
            var qrImage = PDImageXObject.createFromByteArray(document, qr.png(ticket.getCode()), "qr");
            cs.drawImage(qrImage, qrX, qrY, qrSize, qrSize);
            var code = ticket.getCode();
            text(cs, MONO, 10, GREY, code, (595 - width(MONO, 10, code)) / 2, qrY - 18);

            // Status: a valid ticket says so; a used or void one covers the QR
            if (ticket.getStatus() == TicketStatus.VALID) {
                cs.setStrokingColor(BLUE);
                cs.setLineWidth(1.2f);
                cs.addRect(left + 24, qrY + qrSize - 22, 60, 22);
                cs.stroke();
                text(cs, SANS_BOLD, 9, BLUE, "VÁLIDO", left + 24 + (60 - width(SANS_BOLD, 9, "VÁLIDO")) / 2, qrY + qrSize - 15);
            } else {
                var gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.88f);
                cs.saveGraphicsState();
                cs.setGraphicsStateParameters(gs);
                cs.setNonStrokingColor(Color.WHITE);
                cs.addRect(qrX - 4, qrY - 4, qrSize + 8, qrSize + 8);
                cs.fill();
                cs.restoreGraphicsState();
                var label = ticket.getStatus() == TicketStatus.USED ? "JÁ UTILIZADO" : "ANULADO";
                text(cs, SANS_BOLD, 22, BLACK, label, (595 - width(SANS_BOLD, 22, label)) / 2, qrY + qrSize / 2 - 8);
            }

            // Details
            float rowY = 218;
            detail(cs, "LOCAL", event == null ? "" : event.getLocation(), left + 24, rowY, 250);
            detail(cs, "BILHETE", number + " de " + total, left + 300, rowY, 90);
            detail(cs, "ENCOMENDA", reference, left + 390, rowY, 120);
            text(cs, SANS, 9, GREY, "Mostre este código à entrada. Cada código vale para uma pessoa e uma só entrada.", left + 24, 168);
            text(cs, SANS, 8, GREY, "South Beach · Av. da Marginal 4272, Maputo", left, 120);
        }
    }

    private static void detail(PDPageContentStream cs, String label, String value, float x, float y, float maxWidth) throws IOException {
        text(cs, SANS_BOLD, 7.5f, GREY, spaced(label), x, y);
        float lineY = y - 14;
        for (var line : wrap(SANS, 10.5f, value, maxWidth)) { text(cs, SANS, 10.5f, BLACK, line, x, lineY); lineY -= 13; }
    }

    // ---- Text helpers: the built-in fonts only know Latin text, so anything else is replaced rather than failing the whole PDF ----
    private static String safe(PDFont font, String text) {
        var out = new StringBuilder();
        text.codePoints().forEach(cp -> {
            var s = new String(Character.toChars(cp));
            try { font.encode(s); out.append(s); } catch (IOException | IllegalArgumentException unsupported) { out.append('?'); }
        });
        return out.toString();
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, Color color, String text, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(font, text));
        cs.endText();
    }

    private static float width(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(safe(font, text)) / 1000f * size;
    }

    private static String spaced(String text) { return text; }

    private static List<String> wrap(PDFont font, float size, String text, float maxWidth) throws IOException {
        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.split("\\s+")) {
            var candidate = line.length() == 0 ? word : line + " " + word;
            if (width(font, size, candidate) > maxWidth && line.length() > 0) { lines.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(candidate);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static String dateLine(Instant instant) {
        var text = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PT).withZone(MAPUTO).format(instant);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String timeLine(Instant start, Instant end) {
        var time = DateTimeFormatter.ofPattern("HH:mm", PT).withZone(MAPUTO);
        if (end == null) return time.format(start);
        var sameDay = start.atZone(MAPUTO).toLocalDate().equals(end.atZone(MAPUTO).toLocalDate());
        return time.format(start) + " – " + time.format(end) + (sameDay ? "" : " (dia seguinte)");
    }
}
