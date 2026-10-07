package mz.co.southbeach.archive;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "past_events")
public class PastEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 90) private String slug;
    @Column(name = "title_pt", nullable = false, length = 120) private String titlePt;
    @Column(name = "title_en", length = 120) private String titleEn;
    @Column(name = "date_text_pt", length = 80) private String dateTextPt;
    @Column(name = "date_text_en", length = 80) private String dateTextEn;
    @Column(name = "time_text", length = 60) private String timeText;
    @Column(length = 120) private String location;
    @Column(name = "description_pt", length = 2000) private String descriptionPt;
    @Column(name = "description_en", length = 2000) private String descriptionEn;
    @Column(name = "cover_url", length = 500) private String coverUrl;
    @Column(nullable = false) private int position;
    @Column(nullable = false) private boolean visible;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PastEvent() { }

    public PastEvent(String slug, int position, Instant now) {
        this.slug = slug; this.position = position; this.createdAt = now;
    }

    public void update(String titlePt, String titleEn, String dateTextPt, String dateTextEn, String timeText, String location,
                       String descriptionPt, String descriptionEn, String coverUrl, boolean visible) {
        this.titlePt = titlePt; this.titleEn = titleEn; this.dateTextPt = dateTextPt; this.dateTextEn = dateTextEn;
        this.timeText = timeText; this.location = location; this.descriptionPt = descriptionPt; this.descriptionEn = descriptionEn;
        this.coverUrl = coverUrl; this.visible = visible;
    }

    public void moveTo(int position) { this.position = position; }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitlePt() { return titlePt; }
    public String getTitleEn() { return titleEn; }
    public String getDateTextPt() { return dateTextPt; }
    public String getDateTextEn() { return dateTextEn; }
    public String getTimeText() { return timeText; }
    public String getLocation() { return location; }
    public String getDescriptionPt() { return descriptionPt; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getCoverUrl() { return coverUrl; }
    public int getPosition() { return position; }
    public boolean isVisible() { return visible; }
}
