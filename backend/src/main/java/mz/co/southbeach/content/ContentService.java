package mz.co.southbeach.content;

import mz.co.southbeach.tickets.service.TicketNotFoundException;
import mz.co.southbeach.tickets.service.TicketRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ContentService {
    public record Entry(String pt, String en) { }

    private static final Pattern KEY = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,118}$");
    private static final Pattern LINK = Pattern.compile(
            "^(https?://[^\\s\"'<>\\\\]{4,480}|tel:\\+?[0-9 ()-]{5,30}|mailto:[^\\s\"'<>\\\\]{3,200}|[a-z0-9-]+\\.html(#[a-z0-9-]*)?|#[a-z0-9-]+)$");
    private static final int MAX_ENTRIES = 100, MAX_TEXT = 2000;

    private final SiteContentRepository content;
    private final MediaFileRepository media;
    private final Clock clock;
    private final long maxMediaBytes;

    public ContentService(SiteContentRepository content, MediaFileRepository media, Clock clock,
                          @Value("${app.content.media-max-bytes}") long maxMediaBytes) {
        this.content = content;
        this.media = media;
        this.clock = clock;
        this.maxMediaBytes = maxMediaBytes;
    }

    @Transactional(readOnly = true)
    public Map<String, Entry> all() {
        var result = new LinkedHashMap<String, Entry>();
        content.findAll().forEach(item -> result.put(item.getKey(), new Entry(item.getPt(), item.getEn())));
        return result;
    }

    /** Saves every entry; an entry that is null or empty restores the page's own default for that key. All or nothing. */
    @Transactional
    public void save(Map<String, Entry> entries) {
        if (entries == null || entries.isEmpty()) throw new TicketRequestException("Nothing to save.");
        if (entries.size() > MAX_ENTRIES) throw new TicketRequestException("Too many entries in one request (maximum " + MAX_ENTRIES + ").");
        var now = clock.instant();
        entries.forEach((key, entry) -> {
            if (!KEY.matcher(key).matches()) throw new TicketRequestException("Invalid content key: " + key);
            var pt = blankToNull(entry == null ? null : entry.pt());
            var en = blankToNull(entry == null ? null : entry.en());
            if (pt == null && en == null) {
                content.deleteById(key);
                return;
            }
            if (key.endsWith(".image") || key.endsWith(".bg")) {
                if (pt == null || !ImageTypes.isAllowedUrl(pt)) throw new TicketRequestException("Image '" + key + "' must be an https address or an uploaded image.");
                en = null;
            } else if (key.endsWith(".href")) {
                if (pt == null || !LINK.matcher(pt).matches()) throw new TicketRequestException("Link '" + key + "' must be http(s), tel:, mailto: or a page of this site.");
                en = null;
            } else {
                check(key, pt); check(key, en);
            }
            var existing = content.findById(key);
            if (existing.isPresent()) existing.get().change(pt, en, now);
            else content.save(new SiteContent(key, pt, en, now));
        });
    }

    @Transactional
    public Long storeImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new TicketRequestException("Choose an image file.");
        if (bytes.length > maxMediaBytes) throw new TicketRequestException("The image must be at most " + maxMediaBytes / (1024 * 1024) + " MB.");
        var type = ImageTypes.detect(bytes);
        if (type == null) throw new TicketRequestException("The image must be a JPEG, PNG or WebP file.");
        return media.save(new MediaFile(type, bytes, clock.instant())).getId();
    }

    @Transactional(readOnly = true)
    public MediaFile image(Long id) {
        return media.findById(id).orElseThrow(() -> new TicketNotFoundException("Image"));
    }

    private void check(String key, String text) {
        if (text == null) return;
        if (text.length() > MAX_TEXT) throw new TicketRequestException("Text for '" + key + "' is too long (maximum " + MAX_TEXT + " characters).");
        for (char c : text.toCharArray()) {
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') throw new TicketRequestException("Text for '" + key + "' has invalid characters.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
