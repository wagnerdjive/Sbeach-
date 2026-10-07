package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.domain.TicketStatus;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.repository.IssuedTicketRepository;
import mz.co.southbeach.tickets.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Gate validation: a ticket admits one person once, at the event it was issued for. */
@Service
public class EntryService {
    public enum Outcome { ADMITTED, ALREADY_USED, VOID, WRONG_EVENT, NOT_FOUND, UNDONE, NOT_USED, VALID }

    public record Result(Outcome outcome, String ticketType, String eventTitle, Instant usedAt) { }
    public record TypeStats(Long ticketTypeId, String name, long issued, long admitted) { }
    public record Stats(long issued, long admitted, List<TypeStats> byType) { }
    public record Recent(String code, String ticketType, Instant usedAt) { }

    private final IssuedTicketRepository tickets;
    private final TicketTypeRepository types;
    private final EventRepository events;
    private final Clock clock;

    public EntryService(IssuedTicketRepository tickets, TicketTypeRepository types, EventRepository events, Clock clock) {
        this.tickets = tickets;
        this.types = types;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result checkIn(Long eventId, String rawCode) {
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        var found = tickets.findByCode(code);
        if (found.isEmpty()) return new Result(Outcome.NOT_FOUND, null, null, null);
        var ticket = found.get();
        var typeName = types.findById(ticket.getTicketTypeId()).map(t -> t.getName()).orElse(null);
        if (!ticket.getEventId().equals(eventId)) {
            var title = events.findById(ticket.getEventId()).map(e -> e.getTitle()).orElse(null);
            return new Result(Outcome.WRONG_EVENT, typeName, title, null);
        }
        if (ticket.getStatus() == TicketStatus.VOID) return new Result(Outcome.VOID, typeName, null, null);
        var now = clock.instant();
        if (tickets.markUsed(ticket.getId(), now) == 1) return new Result(Outcome.ADMITTED, typeName, null, now);
        var current = tickets.findById(ticket.getId()).orElseThrow();
        return new Result(current.getStatus() == TicketStatus.VOID ? Outcome.VOID : Outcome.ALREADY_USED, typeName, null, current.getUsedAt());
    }

    /** Looks a ticket up exactly as {@link #checkIn} would judge it, but admits nobody: a still-valid ticket answers VALID and stays valid. */
    @Transactional(readOnly = true)
    public Result peek(Long eventId, String rawCode) {
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        var found = tickets.findByCode(code);
        if (found.isEmpty()) return new Result(Outcome.NOT_FOUND, null, null, null);
        var ticket = found.get();
        var typeName = types.findById(ticket.getTicketTypeId()).map(t -> t.getName()).orElse(null);
        if (!ticket.getEventId().equals(eventId)) {
            return new Result(Outcome.WRONG_EVENT, typeName, events.findById(ticket.getEventId()).map(e -> e.getTitle()).orElse(null), null);
        }
        return switch (ticket.getStatus()) {
            case VOID -> new Result(Outcome.VOID, typeName, null, null);
            case USED -> new Result(Outcome.ALREADY_USED, typeName, null, ticket.getUsedAt());
            default -> new Result(Outcome.VALID, typeName, null, null);
        };
    }

    /** Takes back an entry that was recorded by mistake, so the ticket can be read again. */
    @Transactional
    public Result undo(Long eventId, String rawCode) {
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        var ticket = tickets.findByCode(code).filter(t -> t.getEventId().equals(eventId));
        if (ticket.isEmpty()) return new Result(Outcome.NOT_FOUND, null, null, null);
        var typeName = types.findById(ticket.get().getTicketTypeId()).map(t -> t.getName()).orElse(null);
        return new Result(tickets.markValidAgain(ticket.get().getId()) == 1 ? Outcome.UNDONE : Outcome.NOT_USED, typeName, null, null);
    }

    /** The latest admissions of the event, newest first, for the gate's own log. */
    @Transactional(readOnly = true)
    public List<Recent> recent(Long eventId) {
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var names = new java.util.HashMap<Long, String>();
        types.findByEventIdOrderByIdAsc(eventId).forEach(type -> names.put(type.getId(), type.getName()));
        return tickets.findTop15ByEventIdAndStatusOrderByUsedAtDesc(eventId, TicketStatus.USED).stream()
                .map(t -> new Recent(t.getCode(), names.getOrDefault(t.getTicketTypeId(), "?"), t.getUsedAt())).toList();
    }

    @Transactional(readOnly = true)
    public Stats stats(Long eventId) {
        events.findById(eventId).orElseThrow(() -> new TicketNotFoundException("Event"));
        var names = new java.util.HashMap<Long, String>();
        types.findByEventIdOrderByIdAsc(eventId).forEach(type -> names.put(type.getId(), type.getName()));
        var rows = tickets.countByType(eventId).stream()
                .map(r -> new TypeStats((Long) r[0], names.getOrDefault((Long) r[0], "?"), ((Number) r[1]).longValue(), ((Number) r[2]).longValue()))
                .toList();
        return new Stats(rows.stream().mapToLong(TypeStats::issued).sum(), rows.stream().mapToLong(TypeStats::admitted).sum(), rows);
    }
}
