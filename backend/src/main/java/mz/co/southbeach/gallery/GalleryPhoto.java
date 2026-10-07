package mz.co.southbeach.gallery;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "gallery_photos")
public class GalleryPhoto {
    public enum Category { SPACES, EVENTS }
    public enum Size { NORMAL, WIDE, TALL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "image_url", nullable = false, length = 500) private String imageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Size size;
    @Column(name = "caption_pt", nullable = false, length = 120) private String captionPt;
    @Column(name = "caption_en", length = 120) private String captionEn;
    @Column(nullable = false) private int position;
    @Column(nullable = false) private boolean visible;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected GalleryPhoto() { }

    public GalleryPhoto(String imageUrl, Category category, Size size, String captionPt, String captionEn,
                        int position, boolean visible, Instant now) {
        this.position = position;
        this.createdAt = now;
        update(imageUrl, category, size, captionPt, captionEn, visible);
    }

    public void update(String imageUrl, Category category, Size size, String captionPt, String captionEn, boolean visible) {
        this.imageUrl = imageUrl; this.category = category; this.size = size;
        this.captionPt = captionPt; this.captionEn = captionEn; this.visible = visible;
    }

    public void moveTo(int position) { this.position = position; }

    public Long getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public Category getCategory() { return category; }
    public Size getSize() { return size; }
    public String getCaptionPt() { return captionPt; }
    public String getCaptionEn() { return captionEn; }
    public int getPosition() { return position; }
    public boolean isVisible() { return visible; }
}
