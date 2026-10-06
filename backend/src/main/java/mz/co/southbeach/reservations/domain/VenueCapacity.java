package mz.co.southbeach.reservations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "venue_capacity")
public class VenueCapacity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private ReservationVenue venue;

    @Column(nullable = false)
    private Integer capacity;

    protected VenueCapacity() { }

    public ReservationVenue getVenue() { return venue; }
    public Integer getCapacity() { return capacity; }
}
