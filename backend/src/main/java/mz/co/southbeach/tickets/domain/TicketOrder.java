package mz.co.southbeach.tickets.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ticket_orders")
public class TicketOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "full_name", nullable = false, length = 100) private String fullName;
    @Column(nullable = false, length = 30) private String phone;
    @Column(length = 120) private String email;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private OrderStatus status;
    @Column(name = "access_token", length = 40) private String accessToken;
    @Column(name = "refunded_at") private Instant refundedAt;
    @Column(name = "refund_note", length = 200) private String refundNote;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TicketOrder() { }

    public TicketOrder(String reference, String fullName, String phone, String email, long totalMinor,
                       String currency, Instant expiresAt, Instant now) {
        this.reference = reference; this.fullName = fullName; this.phone = phone; this.email = email;
        this.totalMinor = totalMinor; this.currency = currency; this.expiresAt = expiresAt;
        this.status = OrderStatus.PENDING; this.createdAt = now; this.updatedAt = now;
    }

    public void markRefunded(String note, Instant now) {
        this.status = OrderStatus.REFUNDED; this.refundNote = note; this.refundedAt = now; this.updatedAt = now;
    }

    public void assignAccessToken(String token) { this.accessToken = token; }

    public void setStatus(OrderStatus status, Instant now) { this.status = status; this.updatedAt = now; }

    public String getRefundNote() { return refundNote; }
    public Instant getRefundedAt() { return refundedAt; }
    public String getAccessToken() { return accessToken; }
    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public long getTotalMinor() { return totalMinor; }
    public String getCurrency() { return currency; }
    public OrderStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
