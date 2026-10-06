package mz.co.southbeach.tickets.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import mz.co.southbeach.tickets.domain.TicketType;

import java.time.Instant;

/** {@code capacity}, {@code sold} and {@code held} are only filled in for staff. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketTypeResponse(
        Long id, String name, String description, long priceMinor, String currency,
        int available, boolean onSale, int maxPerOrder, Instant saleStartsAt, Instant saleEndsAt,
        Integer capacity, Integer sold, Integer held
) {
    public static TicketTypeResponse from(TicketType t, Instant now, boolean admin) {
        return new TicketTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getPriceMinor(), "MZN",
                t.available(), t.onSale(now) && t.available() > 0, t.getMaxPerOrder(), t.getSaleStartsAt(), t.getSaleEndsAt(),
                admin ? t.getCapacity() : null, admin ? t.getSold() : null, admin ? t.getHeld() : null);
    }
}
