package mz.co.southbeach.tickets.api;

import mz.co.southbeach.tickets.api.dto.EventResponse;
import mz.co.southbeach.tickets.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService service;

    public EventController(EventService service) { this.service = service; }

    @GetMapping
    public List<EventResponse> list() { return service.listPublished(); }

    @GetMapping("/{slug}")
    public EventResponse get(@PathVariable String slug) { return service.getPublished(slug); }
}
