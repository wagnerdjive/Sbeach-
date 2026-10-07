package mz.co.southbeach.menu;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "menu_items")
public class MenuItem {
    public enum Venue { RESTAURANT, BEACH, SPORTS }
    public enum Kind { FOOD, DRINKS }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Venue venue;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Kind kind;
    @Column(name = "group_id") private Long groupId;
    @Column(name = "section_pt", nullable = false, length = 80) private String sectionPt;
    @Column(name = "section_en", length = 80) private String sectionEn;
    @Column(name = "name_pt", nullable = false, length = 120) private String namePt;
    @Column(name = "name_en", length = 120) private String nameEn;
    @Column(name = "description_pt", length = 400) private String descriptionPt;
    @Column(name = "description_en", length = 400) private String descriptionEn;
    @Column(name = "price_cents") private Long priceCents;
    @Column(nullable = false) private int position;
    @Column(nullable = false) private boolean visible;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MenuItem() { }

    public MenuItem(int position, Instant now) {
        this.position = position;
        this.createdAt = now;
    }

    public void update(Venue venue, Kind kind, Long groupId, String sectionPt, String sectionEn, String namePt, String nameEn,
                       String descriptionPt, String descriptionEn, Long priceCents, boolean visible) {
        this.venue = venue; this.kind = kind; this.groupId = groupId; this.sectionPt = sectionPt; this.sectionEn = sectionEn;
        this.namePt = namePt; this.nameEn = nameEn; this.descriptionPt = descriptionPt; this.descriptionEn = descriptionEn;
        this.priceCents = priceCents; this.visible = visible;
    }

    public void moveTo(int position) { this.position = position; }

    public Long getId() { return id; }
    public Venue getVenue() { return venue; }
    public Kind getKind() { return kind; }
    public Long getGroupId() { return groupId; }
    public String getSectionPt() { return sectionPt; }
    public String getSectionEn() { return sectionEn; }
    public String getNamePt() { return namePt; }
    public String getNameEn() { return nameEn; }
    public String getDescriptionPt() { return descriptionPt; }
    public String getDescriptionEn() { return descriptionEn; }
    public Long getPriceCents() { return priceCents; }
    public int getPosition() { return position; }
    public boolean isVisible() { return visible; }
}
