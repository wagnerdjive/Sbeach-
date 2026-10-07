package mz.co.southbeach.gate;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "gate_users")
public class GateUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String username;
    @Column(nullable = false, length = 80) private String label;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "event_id") private Long eventId;
    @Column(nullable = false) private boolean active;
    @Column(name = "valid_until") private Instant validUntil;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected GateUser() { }

    public GateUser(String username, String label, String passwordHash, Long eventId, Instant validUntil, Instant now) {
        this.username = username; this.label = label; this.passwordHash = passwordHash; this.eventId = eventId;
        this.validUntil = validUntil; this.active = true; this.createdAt = now;
    }

    public void update(String label, Long eventId, Instant validUntil, boolean active) {
        this.label = label; this.eventId = eventId; this.validUntil = validUntil; this.active = active;
    }
    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    public void touch(Instant now) { this.lastUsedAt = now; }

    public boolean usableAt(Instant now) { return active && (validUntil == null || validUntil.isAfter(now)); }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getLabel() { return label; }
    public String getPasswordHash() { return passwordHash; }
    public Long getEventId() { return eventId; }
    public boolean isActive() { return active; }
    public Instant getValidUntil() { return validUntil; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
