package mz.co.southbeach.tickets.api;

import mz.co.southbeach.tickets.api.dto.EventResponse;
import mz.co.southbeach.tickets.service.EventService;
import mz.co.southbeach.tickets.domain.EventPoster;
import mz.co.southbeach.tickets.service.PosterService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService service;
    private final PosterService posters;

    public EventController(EventService service, PosterService posters) {
        this.service = service;
        this.posters = posters;
    }

    @GetMapping
    public List<EventResponse> list() { return service.listPublished(); }

    @GetMapping("/{slug}/poster")
    public ResponseEntity<byte[]> poster(@PathVariable String slug) {
        return image(posters.forPublic(slug));
    }

    static ResponseEntity<byte[]> image(EventPoster poster) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(poster.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(poster.getData());
    }

    @GetMapping("/{slug}")
    public EventResponse get(@PathVariable String slug) { return service.getPublished(slug); }
}
