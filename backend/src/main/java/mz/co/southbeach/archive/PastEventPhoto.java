package mz.co.southbeach.archive;

import jakarta.persistence.*;

@Entity
@Table(name = "past_event_photos")
public class PastEventPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "image_url", nullable = false, length = 500) private String imageUrl;
    @Column(nullable = false) private int position;

    protected PastEventPhoto() { }

    public PastEventPhoto(Long eventId, String imageUrl, int position) {
        this.eventId = eventId; this.imageUrl = imageUrl; this.position = position;
    }

    public void moveTo(int position) { this.position = position; }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getImageUrl() { return imageUrl; }
    public int getPosition() { return position; }
}
