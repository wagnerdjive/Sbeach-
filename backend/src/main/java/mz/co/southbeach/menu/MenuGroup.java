package mz.co.southbeach.menu;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_groups")
public class MenuGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private MenuItem.Venue venue;
    @Column(name = "name_pt", nullable = false, length = 60) private String namePt;
    @Column(name = "name_en", length = 60) private String nameEn;
    @Column(nullable = false) private int position;

    protected MenuGroup() { }

    public MenuGroup(MenuItem.Venue venue, String namePt, String nameEn, int position) {
        this.venue = venue; this.position = position;
        rename(namePt, nameEn);
    }

    public void rename(String namePt, String nameEn) { this.namePt = namePt; this.nameEn = nameEn; }
    public void moveTo(int position) { this.position = position; }

    public Long getId() { return id; }
    public MenuItem.Venue getVenue() { return venue; }
    public String getNamePt() { return namePt; }
    public String getNameEn() { return nameEn; }
    public int getPosition() { return position; }
}
