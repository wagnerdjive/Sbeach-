package mz.co.southbeach.tickets.api;

import jakarta.validation.Valid;
import mz.co.southbeach.tickets.api.dto.OrderRequest;
import mz.co.southbeach.tickets.api.dto.OrderResponse;
import mz.co.southbeach.tickets.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    /** Holds the tickets for a limited time. It does not take payment. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody OrderRequest request) {
        var placed = service.place(request);
        return OrderResponse.from(placed.order(), placed.items(), false);
    }
}
