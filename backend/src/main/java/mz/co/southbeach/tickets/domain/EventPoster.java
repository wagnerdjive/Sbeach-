package mz.co.southbeach.tickets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_posters")
public class EventPoster {
    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] data;

    protected EventPoster() { }

    public EventPoster(Long eventId, String contentType, byte[] data) {
        this.eventId = eventId;
        this.contentType = contentType;
        this.data = data;
    }

    public Long getEventId() { return eventId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
