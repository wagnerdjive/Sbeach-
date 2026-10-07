package mz.co.southbeach.content;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "media_files")
public class MediaFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "content_type", nullable = false, length = 30) private String contentType;
    @JdbcTypeCode(SqlTypes.VARBINARY) @Column(nullable = false) private byte[] data;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MediaFile() { }

    public MediaFile(String contentType, byte[] data, Instant now) {
        this.contentType = contentType; this.data = data; this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
