package mz.co.southbeach.reservations.api.dto;

import jakarta.validation.constraints.NotNull;
import mz.co.southbeach.reservations.domain.ReservationStatus;
import mz.co.southbeach.reservations.domain.ReservationVenue;

/** {@code venue} is only needed to confirm a request made with NO_PREFERENCE. */
public record UpdateReservationStatusRequest(@NotNull ReservationStatus status, ReservationVenue venue) { }
