package mz.co.southbeach.reservations.api.dto;

import mz.co.southbeach.reservations.domain.Reservation;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.domain.ReservationVenue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AdminReservationResponse(
        String reference,
        String fullName,
        String phone,
        String email,
        LocalDate requestedDate,
        LocalTime requestedTime,
        Integer partySize,
        ReservationVenue venue,
        String occasion,
        String notes,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminReservationResponse from(Reservation reservation) {
        return new AdminReservationResponse(
                reservation.getReference(), reservation.getFullName(), reservation.getPhone(), reservation.getEmail(),
                reservation.getRequestedDate(), reservation.getRequestedTime(), reservation.getPartySize(),
                reservation.getVenue(), reservation.getOccasion(), reservation.getNotes(),
                reservation.getStatus(), reservation.getCreatedAt(), reservation.getUpdatedAt()
        );
    }
}
