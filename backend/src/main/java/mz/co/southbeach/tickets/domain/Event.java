package mz.co.southbeach.tickets.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80) private String slug;
    @Column(nullable = false, length = 150) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, length = 150) private String location;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EventStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Event() { }

    public Event(String slug, String title, String description, String location, Instant startsAt, Instant endsAt,
                 EventStatus status, Instant now) {
        this.createdAt = now;
        update(slug, title, description, location, startsAt, endsAt, status, now);
    }

    public void update(String slug, String title, String description, String location, Instant startsAt, Instant endsAt,
                       EventStatus status, Instant now) {
        this.slug = slug; this.title = title; this.description = description; this.location = location;
        this.startsAt = startsAt; this.endsAt = endsAt; this.status = status; this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public EventStatus getStatus() { return status; }
}
