package mz.co.southbeach.reservations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservations", uniqueConstraints = @UniqueConstraint(name = "uk_reservations_reference", columnNames = "reference"))
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String reference;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "requested_time", nullable = false)
    private LocalTime requestedTime;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReservationVenue venue;

    @Column(length = 100)
    private String occasion;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() { }

    public Reservation(String reference, String fullName, String phone, LocalDate requestedDate,
                       LocalTime requestedTime, Integer partySize, ReservationVenue venue,
                       String occasion, String notes, Instant now) {
        this.reference = reference;
        this.fullName = fullName;
        this.phone = phone;
        this.requestedDate = requestedDate;
        this.requestedTime = requestedTime;
        this.partySize = partySize;
        this.venue = venue;
        this.occasion = occasion;
        this.notes = notes;
        this.status = ReservationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeStatus(ReservationStatus next, Instant now) {
        if (status == next) return;
        if (status == ReservationStatus.CANCELLED || (status == ReservationStatus.CONFIRMED && next == ReservationStatus.PENDING)) {
            throw new IllegalStateException("This reservation status cannot be changed to the requested value.");
        }
        this.status = next;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public LocalDate getRequestedDate() { return requestedDate; }
    public LocalTime getRequestedTime() { return requestedTime; }
    public Integer getPartySize() { return partySize; }
    public ReservationVenue getVenue() { return venue; }
    public String getOccasion() { return occasion; }
    public String getNotes() { return notes; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
