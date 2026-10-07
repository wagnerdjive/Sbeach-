package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.domain.EventPoster;
import mz.co.southbeach.tickets.domain.EventStatus;
import mz.co.southbeach.tickets.repository.EventPosterRepository;
import mz.co.southbeach.tickets.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PosterService {
    private final EventRepository events;
    private final EventPosterRepository posters;
    private final Clock clock;
    private final long maxBytes;

    public PosterService(EventRepository events, EventPosterRepository posters, Clock clock,
                         @Value("${app.tickets.poster-max-bytes}") long maxBytes) {
        this.events = events;
        this.posters = posters;
        this.clock = clock;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public void store(Long eventId, byte[] bytes) {
        var event = events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        if (bytes == null || bytes.length == 0) throw new TicketRequestException("Choose an image file.");
        if (bytes.length > maxBytes) throw new TicketRequestException("The poster must be at most " + maxBytes / (1024 * 1024) + " MB.");
        // The type comes from the file's own signature; the client-supplied content type is never trusted.
        var type = detectType(bytes);
        if (type == null) throw new TicketRequestException("The poster must be a JPEG, PNG or WebP image.");
        posters.save(new EventPoster(eventId, type, bytes));
        event.setPosterVersion(clock.millis());
    }

    @Transactional
    public void remove(Long eventId) {
        var event = events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        posters.deleteById(eventId);
        event.setPosterVersion(null);
    }

    @Transactional(readOnly = true)
    public EventPoster forStaff(Long eventId) {
        return posters.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Poster"));
    }

    @Transactional(readOnly = true)
    public EventPoster forPublic(String slug) {
        var event = events.findBySlugAndStatus(slug, EventStatus.PUBLISHED).orElseThrow(() -> new TicketNotFoundException("Poster"));
        return posters.findById(event.getId()).orElseThrow(() -> new TicketNotFoundException("Poster"));
    }

    static String detectType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
