package mz.co.southbeach.tickets.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketOrder;
import mz.co.southbeach.tickets.domain.TicketOrderItem;

import java.time.Instant;
import java.util.List;

/** Customer contact data is only filled in for staff. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        String reference, OrderStatus status, long totalMinor, String currency, Instant expiresAt, Instant createdAt,
        List<Line> items, String fullName, String phone, String email
) {
    public record Line(Long ticketTypeId, int quantity, long unitPriceMinor) { }

    public static OrderResponse from(TicketOrder o, List<TicketOrderItem> items, boolean admin) {
        return new OrderResponse(o.getReference(), o.getStatus(), o.getTotalMinor(), o.getCurrency(), o.getExpiresAt(),
                o.getCreatedAt(),
                items.stream().map(i -> new Line(i.getTicketTypeId(), i.getQuantity(), i.getUnitPriceMinor())).toList(),
                admin ? o.getFullName() : null, admin ? o.getPhone() : null, admin ? o.getEmail() : null);
    }
}
