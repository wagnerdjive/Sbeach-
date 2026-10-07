package mz.co.southbeach.tickets.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "issued_tickets")
public class IssuedTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "ticket_type_id", nullable = false) private Long ticketTypeId;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(nullable = false, length = 40) private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private TicketStatus status;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "used_at") private Instant usedAt;

    protected IssuedTicket() { }

    public IssuedTicket(Long orderId, Long ticketTypeId, Long eventId, String code, Instant now) {
        this.orderId = orderId; this.ticketTypeId = ticketTypeId; this.eventId = eventId;
        this.code = code; this.status = TicketStatus.VALID; this.issuedAt = now;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getTicketTypeId() { return ticketTypeId; }
    public Long getEventId() { return eventId; }
    public String getCode() { return code; }
    public TicketStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getUsedAt() { return usedAt; }
}
