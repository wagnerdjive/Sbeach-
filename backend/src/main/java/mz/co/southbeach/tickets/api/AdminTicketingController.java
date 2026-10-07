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
import mz.co.southbeach.tickets.service.PosterService;
import org.springframework.web.multipart.MultipartFile;
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
    private final PosterService posters;
    private final String siteUrl;

    public AdminTicketingController(EventService events, OrderService orders, PosterService posters,
                                    @org.springframework.beans.factory.annotation.Value("${app.site-url}") String siteUrl) {
        this.siteUrl = siteUrl;
        this.events = events;
        this.orders = orders;
        this.posters = posters;
    }

    /** Uploads (or replaces) the event poster as multipart field {@code file}. */
    @PutMapping(path = "/events/{id}/poster", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse uploadPoster(@PathVariable Long id, @RequestPart("file") MultipartFile file) throws java.io.IOException {
        posters.store(id, file.getBytes());
        return events.get(id);
    }

    @DeleteMapping("/events/{id}/poster")
    public EventResponse removePoster(@PathVariable Long id) {
        posters.remove(id);
        return events.get(id);
    }

    @GetMapping("/events/{id}/poster")
    public org.springframework.http.ResponseEntity<byte[]> poster(@PathVariable Long id) {
        var poster = posters.forStaff(id);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(poster.getContentType()))
                .cacheControl(org.springframework.http.CacheControl.noCache().cachePrivate())
                .body(poster.getData());
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
        return orders.list(status, page).map(order -> adminView(order, orders.linesOf(order)));
    }

    /**
     * Staff confirm that the customer paid (cash, bank transfer, mobile money received by the team). This issues the
     * tickets. There is deliberately no public way to mark an order paid.
     */
    @PostMapping("/orders/{reference}/mark-paid")
    public OrderResponse markPaid(@PathVariable String reference) {
        var result = orders.markPaid(reference);
        return adminView(result.order(), result.items());
    }

    public record RefundRequest(@jakarta.validation.constraints.Size(max = 200) String note) { }

    /** Records a refund staff already paid out (cash, bank, mobile money). It voids the tickets and frees the seats. */
    @PostMapping("/orders/{reference}/refund")
    public OrderResponse refund(@PathVariable String reference, @Valid @RequestBody(required = false) RefundRequest request) {
        var result = orders.refund(reference, request == null ? null : request.note());
        return adminView(result.order(), result.items());
    }

    private OrderResponse adminView(mz.co.southbeach.tickets.domain.TicketOrder order, java.util.List<mz.co.southbeach.tickets.domain.TicketOrderItem> lines) {
        var url = order.getStatus() == OrderStatus.PAID && order.getAccessToken() != null
                ? mz.co.southbeach.tickets.notification.TicketNotifier.ticketsUrl(siteUrl, order.getAccessToken()) : null;
        return OrderResponse.from(order, lines, true, url);
    }

    @PostMapping("/orders/{reference}/cancel")
    public OrderResponse cancel(@PathVariable String reference) {
        var result = orders.cancel(reference);
        return adminView(result.order(), result.items());
    }
}
