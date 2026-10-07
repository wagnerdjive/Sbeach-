package mz.co.southbeach.tickets.api.dto;

import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketStatus;

import java.time.Instant;
import java.util.List;

/** What a customer sees on their ticket page. {@code code} is the QR payload. */
public record TicketPassResponse(String orderReference, OrderStatus orderStatus, List<Pass> tickets) {
    public record Pass(String code, TicketStatus status, String ticketType, String eventTitle, Instant eventStartsAt, String eventLocation) { }
}
