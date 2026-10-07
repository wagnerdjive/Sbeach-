package mz.co.southbeach.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "site_content")
public class SiteContent {
    @Id @Column(name = "content_key", length = 120) private String key;
    @Column(length = 4000) private String pt;
    @Column(length = 4000) private String en;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SiteContent() { }

    public SiteContent(String key, String pt, String en, Instant now) {
        this.key = key; this.pt = pt; this.en = en; this.updatedAt = now;
    }

    public void change(String pt, String en, Instant now) { this.pt = pt; this.en = en; this.updatedAt = now; }

    public String getKey() { return key; }
    public String getPt() { return pt; }
    public String getEn() { return en; }
}
