package mz.co.southbeach.reservations.api.dto;

import jakarta.validation.constraints.NotNull;
import mz.co.southbeach.reservations.domain.ReservationStatus;

public record UpdateReservationStatusRequest(@NotNull ReservationStatus status) { }
