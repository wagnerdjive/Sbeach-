package mz.co.southbeach.tickets.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/** A sellable category or batch of an event. Stock counters are changed only by atomic repository updates. */
@Entity
@DynamicUpdate
@Table(name = "ticket_types")
public class TicketType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(nullable = false, length = 80) private String name;
    @Column(length = 500) private String description;
    @Column(name = "price_minor", nullable = false) private long priceMinor;
    @Column(nullable = false) private int capacity;
    @Column(nullable = false) private int sold;
    @Column(nullable = false) private int held;
    @Column(name = "sale_starts_at") private Instant saleStartsAt;
    @Column(name = "sale_ends_at") private Instant saleEndsAt;
    @Column(name = "max_per_order", nullable = false) private int maxPerOrder;

    protected TicketType() { }

    public TicketType(Long eventId, String name, String description, long priceMinor, int capacity,
                      Instant saleStartsAt, Instant saleEndsAt, int maxPerOrder) {
        this.eventId = eventId;
        this.capacity = capacity;
        update(name, description, priceMinor, saleStartsAt, saleEndsAt, maxPerOrder);
    }

    /** Capacity is deliberately absent: it changes only through {@code TicketTypeRepository.changeCapacity}. */
    public void update(String name, String description, long priceMinor,
                       Instant saleStartsAt, Instant saleEndsAt, int maxPerOrder) {
        this.name = name; this.description = description; this.priceMinor = priceMinor;
        this.saleStartsAt = saleStartsAt; this.saleEndsAt = saleEndsAt; this.maxPerOrder = maxPerOrder;
    }

    public int available() { return Math.max(capacity - sold - held, 0); }

    public boolean onSale(Instant now) {
        return (saleStartsAt == null || !saleStartsAt.isAfter(now)) && (saleEndsAt == null || saleEndsAt.isAfter(now));
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPriceMinor() { return priceMinor; }
    public int getCapacity() { return capacity; }
    public int getSold() { return sold; }
    public int getHeld() { return held; }
    public Instant getSaleStartsAt() { return saleStartsAt; }
    public Instant getSaleEndsAt() { return saleEndsAt; }
    public int getMaxPerOrder() { return maxPerOrder; }
}
