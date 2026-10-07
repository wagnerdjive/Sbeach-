package mz.co.southbeach.tickets.api.dto;

import mz.co.southbeach.tickets.domain.Event;
import mz.co.southbeach.tickets.domain.EventStatus;
import mz.co.southbeach.tickets.domain.TicketType;

import java.time.Instant;
import java.util.List;

public record EventResponse(
        Long id, String slug, String title, String description, String location,
        Instant startsAt, Instant endsAt, EventStatus status, String posterUrl, List<TicketTypeResponse> ticketTypes
) {
    public static EventResponse from(Event e, List<TicketType> types, Instant now, boolean admin) {
        return new EventResponse(e.getId(), e.getSlug(), e.getTitle(), e.getDescription(), e.getLocation(),
                e.getStartsAt(), e.getEndsAt(), e.getStatus(),
                e.getPosterVersion() == null ? null : "/api/events/" + e.getSlug() + "/poster?v=" + e.getPosterVersion(),
                types.stream().map(t -> TicketTypeResponse.from(t, now, admin)).toList());
    }
}
