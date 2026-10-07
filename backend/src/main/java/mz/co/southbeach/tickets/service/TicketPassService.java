package mz.co.southbeach.tickets.service;

import mz.co.southbeach.tickets.api.dto.TicketPassResponse;
import mz.co.southbeach.tickets.repository.EventRepository;
import mz.co.southbeach.tickets.repository.IssuedTicketRepository;
import mz.co.southbeach.tickets.repository.TicketOrderRepository;
import mz.co.southbeach.tickets.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
public class TicketPassService {
    private final TicketOrderRepository orders;
    private final IssuedTicketRepository tickets;
    private final TicketTypeRepository types;
    private final EventRepository events;

    public TicketPassService(TicketOrderRepository orders, IssuedTicketRepository tickets, TicketTypeRepository types, EventRepository events) {
        this.orders = orders;
        this.tickets = tickets;
        this.types = types;
        this.events = events;
    }

    /** Looks an order up by its private access token. An unknown token is indistinguishable from a missing page. */
    @Transactional(readOnly = true)
    public TicketPassResponse byAccessToken(String token) {
        var order = orders.findByAccessToken(token).orElseThrow(() -> new TicketNotFoundException("Tickets"));
        var issued = tickets.findByOrderIdOrderByIdAsc(order.getId());
        var typeNames = new HashMap<Long, String>();
        types.findAllById(issued.stream().map(t -> t.getTicketTypeId()).distinct().toList())
                .forEach(type -> typeNames.put(type.getId(), type.getName()));
        var eventsById = new HashMap<Long, mz.co.southbeach.tickets.domain.Event>();
        events.findAllById(issued.stream().map(t -> t.getEventId()).distinct().toList()).forEach(e -> eventsById.put(e.getId(), e));
        List<TicketPassResponse.Pass> passes = issued.stream().map(t -> {
            var event = eventsById.get(t.getEventId());
            return new TicketPassResponse.Pass(t.getCode(), t.getStatus(), typeNames.get(t.getTicketTypeId()),
                    event.getTitle(), event.getStartsAt(), event.getLocation());
        }).toList();
        return new TicketPassResponse(order.getReference(), order.getStatus(), passes);
    }

    @Transactional(readOnly = true)
    public void requireCode(String code) {
        tickets.findByCode(code).orElseThrow(() -> new TicketNotFoundException("Ticket"));
    }
}
