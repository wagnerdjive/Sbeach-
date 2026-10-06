package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.api.dto.OrderRequest;
import mz.co.southbeach.tickets.domain.EventStatus;
import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.domain.TicketOrder;
import mz.co.southbeach.tickets.domain.TicketOrderItem;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.repository.TicketOrderItemRepository;
import mz.co.southbeach.tickets.repository.TicketOrderRepository;
import mz.co.southbeach.tickets.repository.TicketTypeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Orders hold stock while awaiting payment. Holds are taken with atomic conditional updates and released
 * on expiry or cancellation; payment adapters call {@link #markPaid} to turn a hold into a sale.
 */
@Service
public class OrderService {
    private static final String CURRENCY = "MZN";

    private final EventRepository events;
    private final TicketTypeRepository types;
    private final TicketOrderRepository orders;
    private final TicketOrderItemRepository items;
    private final Clock clock;
    private final Duration holdDuration;

    public OrderService(EventRepository events, TicketTypeRepository types, TicketOrderRepository orders,
                        TicketOrderItemRepository items, Clock clock,
                        @Value("${app.tickets.hold-minutes}") long holdMinutes) {
        this.events = events;
        this.types = types;
        this.orders = orders;
        this.items = items;
        this.clock = clock;
        this.holdDuration = Duration.ofMinutes(holdMinutes);
    }

    public record Placed(TicketOrder order, List<TicketOrderItem> items) { }

    @Transactional
    public Placed place(OrderRequest request) {
        var event = events.findBySlugAndStatus(request.eventSlug(), EventStatus.PUBLISHED)
                .orElseThrow(() -> new TicketNotFoundException("Event"));
        var wanted = new LinkedHashMap<Long, Integer>();
        request.items().forEach(i -> wanted.merge(i.ticketTypeId(), i.quantity(), Integer::sum));

        var now = clock.instant();
        var found = types.findAllById(wanted.keySet());
        if (found.size() != wanted.size() || found.stream().anyMatch(t -> !t.getEventId().equals(event.getId()))) {
            throw new TicketRequestException("Every ticket type must belong to the chosen event.");
        }
        long total = 0;
        for (var type : found) {
            int quantity = wanted.get(type.getId());
            if (quantity > type.getMaxPerOrder()) {
                throw new TicketRequestException("At most " + type.getMaxPerOrder() + " tickets of '" + type.getName() + "' per order.");
            }
            total = Math.addExact(total, Math.multiplyExact(type.getPriceMinor(), (long) quantity));
        }
        // Any failed hold rolls the whole transaction back, which also undoes the holds already taken.
        for (var type : found) {
            if (types.hold(type.getId(), wanted.get(type.getId()), now) == 0) {
                throw new TicketConflictException("'" + type.getName() + "' is sold out, not on sale, or has fewer tickets left than requested.");
            }
        }
        var reference = "TK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        var order = orders.save(new TicketOrder(reference, request.fullName().trim(), request.phone().trim(),
                blankToNull(request.email()), total, CURRENCY, now.plus(holdDuration), now));
        var saved = items.saveAll(found.stream()
                .map(t -> new TicketOrderItem(order.getId(), t.getId(), wanted.get(t.getId()), t.getPriceMinor())).toList());
        return new Placed(order, saved);
    }

    /** Idempotent: a repeated payment notification for a paid order changes nothing. */
    @Transactional
    public Placed markPaid(String reference) {
        var order = find(reference);
        var lines = items.findByOrderId(order.getId());
        if (order.getStatus() == OrderStatus.PAID) return new Placed(order, lines);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new TicketConflictException("Order " + reference + " is " + order.getStatus() + " and can no longer be paid.");
        }
        for (var line : lines) {
            if (types.sell(line.getTicketTypeId(), line.getQuantity()) == 0) {
                throw new IllegalStateException("Held stock missing for order " + reference);
            }
        }
        order.setStatus(OrderStatus.PAID, clock.instant());
        return new Placed(order, lines);
    }

    @Transactional
    public Placed cancel(String reference) {
        var order = find(reference);
        var lines = items.findByOrderId(order.getId());
        if (order.getStatus() == OrderStatus.CANCELLED) return new Placed(order, lines);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new TicketConflictException("Only pending orders can be cancelled here; " + order.getStatus() + " orders need a refund flow.");
        }
        release(order, lines, OrderStatus.CANCELLED);
        return new Placed(order, lines);
    }

    /** Releases the stock held by pending orders whose payment window has passed. */
    @Transactional
    public int expireDue() {
        var now = clock.instant();
        var due = orders.findByStatusAndExpiresAtBefore(OrderStatus.PENDING, now);
        due.forEach(order -> release(order, items.findByOrderId(order.getId()), OrderStatus.EXPIRED));
        return due.size();
    }

    @Transactional(readOnly = true)
    public Page<TicketOrder> list(OrderStatus status, Pageable pageable) {
        return status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public List<TicketOrderItem> linesOf(TicketOrder order) {
        return items.findByOrderId(order.getId());
    }

    private void release(TicketOrder order, List<TicketOrderItem> lines, OrderStatus next) {
        lines.forEach(line -> types.release(line.getTicketTypeId(), line.getQuantity()));
        order.setStatus(next, clock.instant());
    }

    private TicketOrder find(String reference) {
        return orders.findByReference(reference).orElseThrow(() -> new TicketNotFoundException("Order"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
