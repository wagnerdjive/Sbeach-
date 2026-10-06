package mz.co.southbeach.tickets.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "ticket_order_items")
public class TicketOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "ticket_type_id", nullable = false) private Long ticketTypeId;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price_minor", nullable = false) private long unitPriceMinor;

    protected TicketOrderItem() { }

    public TicketOrderItem(Long orderId, Long ticketTypeId, int quantity, long unitPriceMinor) {
        this.orderId = orderId; this.ticketTypeId = ticketTypeId; this.quantity = quantity; this.unitPriceMinor = unitPriceMinor;
    }

    public Long getOrderId() { return orderId; }
    public Long getTicketTypeId() { return ticketTypeId; }
    public int getQuantity() { return quantity; }
    public long getUnitPriceMinor() { return unitPriceMinor; }
}
