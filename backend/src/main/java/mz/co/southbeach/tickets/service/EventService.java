package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.api.dto.EventRequest;
import mz.co.southbeach.tickets.api.dto.EventResponse;
import mz.co.southbeach.tickets.api.dto.TicketTypeRequest;
import mz.co.southbeach.tickets.api.dto.TicketTypeResponse;
import mz.co.southbeach.tickets.domain.Event;
import mz.co.southbeach.tickets.domain.EventStatus;
import mz.co.southbeach.tickets.domain.TicketType;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EventService {
    private final EventRepository events;
    private final TicketTypeRepository types;
    private final Clock clock;

    public EventService(EventRepository events, TicketTypeRepository types, Clock clock) {
        this.events = events;
        this.types = types;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listPublished() {
        return withTypes(events.findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED), false);
    }

    @Transactional(readOnly = true)
    public EventResponse getPublished(String slug) {
        var event = events.findBySlugAndStatus(slug, EventStatus.PUBLISHED).orElseThrow(() -> new TicketNotFoundException("Event"));
        return withTypes(List.of(event), false).get(0);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id) {
        var event = events.findById(id).orElseThrow(() -> new TicketNotFoundException("Event"));
        return withTypes(List.of(event), true).get(0);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listAll() {
        return withTypes(events.findAllByOrderByStartsAtDesc(), true);
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        validate(request);
        var event = events.saveAndFlush(new Event(request.slug(), request.title().trim(), clean(request.description()),
                request.location().trim(), request.startsAt(), request.endsAt(), request.status(), clock.instant()));
        return withTypes(List.of(event), true).get(0);
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        validate(request);
        var event = events.findById(id).orElseThrow(() -> new TicketNotFoundException("Event"));
        event.update(request.slug(), request.title().trim(), clean(request.description()), request.location().trim(),
                request.startsAt(), request.endsAt(), request.status(), clock.instant());
        events.flush();
        return withTypes(List.of(event), true).get(0);
    }

    @Transactional
    public TicketTypeResponse addTicketType(Long eventId, TicketTypeRequest request) {
        validate(request);
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var type = types.saveAndFlush(new TicketType(eventId, request.name().trim(), clean(request.description()),
                request.priceMinor(), request.capacity(), request.saleStartsAt(), request.saleEndsAt(), request.maxPerOrder()));
        return TicketTypeResponse.from(type, clock.instant(), true);
    }

    @Transactional
    public TicketTypeResponse updateTicketType(Long id, TicketTypeRequest request) {
        validate(request);
        var type = types.findById(id).orElseThrow(() -> new TicketNotFoundException("Ticket type"));
        type.update(request.name().trim(), clean(request.description()), request.priceMinor(),
                request.saleStartsAt(), request.saleEndsAt(), request.maxPerOrder());
        types.flush();
        if (types.changeCapacity(id, request.capacity()) == 0) {
            throw new TicketConflictException("Capacity cannot be lower than the tickets already sold or held.");
        }
        return TicketTypeResponse.from(types.findById(id).orElseThrow(), clock.instant(), true);
    }

    private List<EventResponse> withTypes(List<Event> list, boolean admin) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<TicketType>> byEvent = types.findByEventIdInOrderByIdAsc(list.stream().map(Event::getId).toList())
                .stream().collect(Collectors.groupingBy(TicketType::getEventId));
        var now = clock.instant();
        return list.stream().map(e -> EventResponse.from(e, byEvent.getOrDefault(e.getId(), List.of()), now, admin)).toList();
    }

    private void validate(EventRequest request) {
        if (request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new TicketRequestException("endsAt must be after startsAt.");
        }
    }

    private void validate(TicketTypeRequest request) {
        if (request.saleStartsAt() != null && request.saleEndsAt() != null && !request.saleEndsAt().isAfter(request.saleStartsAt())) {
            throw new TicketRequestException("saleEndsAt must be after saleStartsAt.");
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
