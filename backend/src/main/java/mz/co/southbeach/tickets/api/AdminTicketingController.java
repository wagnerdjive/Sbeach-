package mz.co.southbeach.tickets.api;

import jakarta.validation.Valid;
import mz.co.southbeach.tickets.api.dto.EventRequest;
import mz.co.southbeach.tickets.api.dto.EventResponse;
import mz.co.southbeach.tickets.api.dto.OrderResponse;
import mz.co.southbeach.tickets.api.dto.TicketTypeRequest;
import mz.co.southbeach.tickets.api.dto.TicketTypeResponse;
import mz.co.southbeach.tickets.domain.OrderStatus;
import mz.co.southbeach.tickets.service.EventService;
import mz.co.southbeach.tickets.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminTicketingController {
    private final EventService events;
    private final OrderService orders;
    private final java.time.Clock clock;

    public AdminTicketingController(EventService events, OrderService orders, java.time.Clock clock) {
        this.events = events;
        this.orders = orders;
        this.clock = clock;
    }

    @GetMapping("/events")
    public List<EventResponse> events() { return events.listAll(); }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody EventRequest request) { return events.create(request); }

    @PutMapping("/events/{id}")
    public EventResponse updateEvent(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return events.update(id, request);
    }

    @PostMapping("/events/{id}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse addTicketType(@PathVariable Long id, @Valid @RequestBody TicketTypeRequest request) {
        return events.addTicketType(id, request);
    }

    @PutMapping("/ticket-types/{id}")
    public TicketTypeResponse updateTicketType(@PathVariable Long id, @Valid @RequestBody TicketTypeRequest request) {
        return events.updateTicketType(id, request);
    }

    @GetMapping("/orders")
    public Page<OrderResponse> orders(@RequestParam(required = false) OrderStatus status,
                                      @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return orders.list(status, page).map(order -> OrderResponse.from(order, orders.linesOf(order), true));
    }

    @PostMapping("/orders/{reference}/cancel")
    public OrderResponse cancel(@PathVariable String reference) {
        var result = orders.cancel(reference);
        return OrderResponse.from(result.order(), result.items(), true);
    }
}
