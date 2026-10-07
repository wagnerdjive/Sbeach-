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
        var type = mz.co.southbeach.content.ImageTypes.detect(bytes);
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
}
