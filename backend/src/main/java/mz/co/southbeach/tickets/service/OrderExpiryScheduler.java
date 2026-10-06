package mz.co.southbeach.tickets.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpiryScheduler {
    private final OrderService orders;

    public OrderExpiryScheduler(OrderService orders) { this.orders = orders; }

    @Scheduled(fixedDelayString = "${app.tickets.expiry-check-ms}")
    public void expireUnpaidOrders() { orders.expireDue(); }
}
